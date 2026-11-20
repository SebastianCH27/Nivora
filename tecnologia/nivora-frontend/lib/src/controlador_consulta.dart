import 'dart:async';

import 'package:flutter/foundation.dart';

import 'api_nivora.dart';

class ControladorConsulta extends ChangeNotifier {
  ControladorConsulta(this.api, {DateTime Function()? reloj})
    : reloj = reloj ?? DateTime.now;
  final ServicioNivora api;
  final DateTime Function() reloj;
  SesionConsulta? sesion;
  ConsumoActual? consumo;
  String? aviso;
  String? errorConsumo;
  bool ingresando = false;
  bool consultando = false;
  bool saliendo = false;
  DateTime? _bloqueadoHasta;
  Timer? _temporizador;
  bool _cerrado = false;
  int _version = 0;

  int get segundosEspera {
    final restante = _bloqueadoHasta?.difference(reloj()).inMilliseconds ?? 0;
    return restante <= 0 ? 0 : (restante / 1000).ceil();
  }

  Future<void> ingresar(String suministro, String dni) async {
    if (_cerrado || ingresando || segundosEspera > 0 || sesion != null) return;
    ingresando = true;
    aviso = null;
    final version = ++_version;
    notifyListeners();
    try {
      final nueva = await api.ingresar(suministro, dni);
      if (_cerrado || version != _version) return;
      if (!nueva.expiraEn.isAfter(reloj())) {
        aviso = 'La sesión venció. Vuelve a ingresar.';
        return;
      }
      sesion = nueva;
      _iniciarReloj();
    } on FalloApi catch (fallo) {
      if (_cerrado || version != _version) return;
      aviso = fallo.mensaje;
      if (fallo.reintentarEn != null) {
        _bloqueadoHasta = reloj().add(fallo.reintentarEn!);
        _iniciarReloj();
      }
    } finally {
      if (!_cerrado && version == _version) {
        ingresando = false;
        notifyListeners();
      }
    }
    if (!_cerrado && version == _version && sesion != null) await actualizar();
  }

  Future<void> actualizar() async {
    verificarVencimiento();
    final actual = sesion;
    if (_cerrado || actual == null || consultando || saliendo) return;
    consultando = true;
    errorConsumo = null;
    final version = _version;
    notifyListeners();
    try {
      final nuevo = await api.consumo(actual);
      if (!_cerrado && version == _version) consumo = nuevo;
    } on FalloApi catch (fallo) {
      if (_cerrado || version != _version) return;
      if (fallo.estado == 401) {
        _limpiar('Tu sesión terminó. Vuelve a ingresar.');
      } else {
        errorConsumo = fallo.mensaje;
      }
    } finally {
      if (!_cerrado && version == _version) {
        consultando = false;
        notifyListeners();
      }
    }
  }

  Future<void> salir() async {
    final actual = sesion;
    if (_cerrado || actual == null || saliendo) return;
    saliendo = true;
    // Invalida las consultas que todavía estén en curso.
    final version = ++_version;
    consultando = false;
    notifyListeners();
    String mensaje = 'Cerraste tu sesión.';
    try {
      await api.salir(actual);
    } on FalloApi {
      mensaje =
          'Cerraste la sesión en este dispositivo. No pudimos confirmar '
          'el cierre en el servidor; la sesión vencerá automáticamente.';
    } finally {
      if (!_cerrado && version == _version) _limpiar(mensaje);
    }
  }

  void verificarVencimiento() {
    if (_cerrado) return;
    if (sesion != null && !sesion!.expiraEn.isAfter(reloj())) {
      _limpiar('Tu sesión venció. Vuelve a ingresar.');
    }
  }

  void _iniciarReloj() {
    _temporizador?.cancel();
    _temporizador = Timer.periodic(const Duration(seconds: 1), (_) {
      verificarVencimiento();
      if (_cerrado) return;
      if (sesion == null && segundosEspera == 0) {
        _temporizador?.cancel();
        _bloqueadoHasta = null;
      }
      if (sesion == null) notifyListeners();
    });
  }

  void _limpiar(String mensaje) {
    ++_version;
    _temporizador?.cancel();
    sesion = null;
    consumo = null;
    errorConsumo = null;
    ingresando = consultando = saliendo = false;
    aviso = mensaje;
    notifyListeners();
  }

  @override
  void dispose() {
    _cerrado = true;
    ++_version;
    _temporizador?.cancel();
    sesion = null;
    consumo = null;
    api.cerrar();
    super.dispose();
  }
}
