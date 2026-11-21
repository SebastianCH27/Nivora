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
  HistorialMensual? historial;
  String? errorHistorial;
  bool consultandoHistorial = false;
  int mesesHistorial = 12;
  String? hastaHistorial;
  CostosConsumo? costos;
  String? errorCostos;
  bool consultandoCostos = false;
  int mesesCostos = 3;
  ProyeccionConsumo? proyeccion;
  String? errorProyeccion;
  bool consultandoProyeccion = false;
  ComparacionConsumo? comparacion;
  String? errorComparacion;
  bool consultandoComparacion = false;
  String? baseComparacion;
  String? comparadoComparacion;
  String? actualComparacion;
  PaginaRecibos? recibos;
  String? errorRecibos;
  bool consultandoRecibos = false;
  String? antesDeRecibos;
  EstadisticasConsumo? estadisticas;
  String? errorEstadisticas;
  bool consultandoEstadisticas = false;
  int mesesEstadisticas = 12;
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
    consultandoHistorial = false;
    consultandoCostos = false;
    consultandoProyeccion = false;
    consultandoComparacion = false;
    consultandoRecibos = false;
    consultandoEstadisticas = false;
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

  Future<void> cargarHistorial({
    int? meses,
    String? hasta,
    bool ultimos = false,
  }) async {
    verificarVencimiento();
    final actual = sesion;
    if (_cerrado || actual == null || consultandoHistorial || saliendo) return;
    final nuevosMeses = meses ?? mesesHistorial;
    final nuevoHasta = ultimos ? null : hasta ?? hastaHistorial;
    if (nuevosMeses != mesesHistorial || nuevoHasta != hastaHistorial) {
      // Nunca atribuir a un filtro nuevo los datos de una consulta anterior.
      historial = null;
    }
    mesesHistorial = nuevosMeses;
    hastaHistorial = nuevoHasta;
    consultandoHistorial = true;
    errorHistorial = null;
    final version = _version;
    notifyListeners();
    try {
      final nuevo = await api.historial(
        actual,
        meses: nuevosMeses,
        hasta: nuevoHasta,
      );
      if (!_cerrado && version == _version) historial = nuevo;
    } on FalloApi catch (fallo) {
      if (_cerrado || version != _version) return;
      if (fallo.estado == 401) {
        _limpiar('Tu sesión terminó. Vuelve a ingresar.');
      } else {
        errorHistorial = fallo.estado == 400
            ? 'El rango no está disponible. Selecciona los últimos meses.'
            : fallo.mensaje;
      }
    } finally {
      if (!_cerrado && version == _version) {
        consultandoHistorial = false;
        notifyListeners();
      }
    }
  }

  void verificarVencimiento() {
    if (_cerrado) return;
    if (sesion != null && !sesion!.expiraEn.isAfter(reloj())) {
      _limpiar('Tu sesión venció. Vuelve a ingresar.');
    }
  }

  Future<void> cargarCostos({int? meses}) async {
    verificarVencimiento();
    final actual = sesion;
    if (_cerrado || actual == null || consultandoCostos || saliendo) return;
    final nuevosMeses = meses ?? mesesCostos;
    if (nuevosMeses != mesesCostos) costos = null;
    mesesCostos = nuevosMeses;
    errorCostos = null;
    consultandoCostos = true;
    final version = _version;
    notifyListeners();
    try {
      final nuevo = await api.costos(actual, meses: nuevosMeses);
      if (!_cerrado && version == _version) costos = nuevo;
    } on FalloApi catch (fallo) {
      if (_cerrado || version != _version) return;
      if (fallo.estado == 401) {
        _limpiar('Tu sesión terminó. Vuelve a ingresar.');
      } else {
        errorCostos = fallo.estado == 400
            ? 'Selecciona entre 1 y 12 meses anteriores para consultar los costos.'
            : fallo.mensaje;
      }
    } finally {
      if (!_cerrado && version == _version) {
        consultandoCostos = false;
        notifyListeners();
      }
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

  Future<void> cargarEstadisticas({int? meses}) async {
    verificarVencimiento();
    final actual = sesion;
    if (_cerrado || actual == null || consultandoEstadisticas || saliendo) {
      return;
    }
    final nuevosMeses = meses ?? mesesEstadisticas;
    if (nuevosMeses != mesesEstadisticas) estadisticas = null;
    mesesEstadisticas = nuevosMeses;
    errorEstadisticas = null;
    consultandoEstadisticas = true;
    final version = _version;
    notifyListeners();
    try {
      final datos = await api.estadisticas(actual, meses: nuevosMeses);
      if (!_cerrado && version == _version) estadisticas = datos;
    } on FalloApi catch (fallo) {
      if (_cerrado || version != _version) return;
      if (fallo.estado == 401) {
        _limpiar('Tu sesión terminó. Vuelve a ingresar.');
      } else {
        errorEstadisticas = fallo.estado == 400
            ? 'Ese rango no está disponible. Selecciona 3, 6 o 12 meses.'
            : fallo.mensaje;
      }
    } finally {
      if (!_cerrado && version == _version) {
        consultandoEstadisticas = false;
        notifyListeners();
      }
    }
  }

  Future<void> cargarRecibos({String? antesDe, bool recientes = false}) async {
    verificarVencimiento();
    final actual = sesion;
    if (_cerrado || actual == null || consultandoRecibos || saliendo) return;
    final corte = recientes ? null : antesDe ?? antesDeRecibos;
    if (corte != antesDeRecibos) recibos = null;
    antesDeRecibos = corte;
    errorRecibos = null;
    consultandoRecibos = true;
    final version = _version;
    notifyListeners();
    try {
      final pagina = await api.recibos(actual, antesDe: corte);
      if (!_cerrado && version == _version) recibos = pagina;
    } on FalloApi catch (fallo) {
      if (_cerrado || version != _version) return;
      if (fallo.estado == 401) {
        _limpiar('Tu sesión terminó. Vuelve a ingresar.');
      } else {
        errorRecibos = fallo.estado == 400
            ? 'Ese rango no está disponible. Vuelve a los recibos más recientes.'
            : fallo.mensaje;
      }
    } finally {
      if (!_cerrado && version == _version) {
        consultandoRecibos = false;
        notifyListeners();
      }
    }
  }

  void seleccionarComparacion({String? periodoBase, String? periodoComparado}) {
    verificarVencimiento();
    if (_cerrado || sesion == null || consultandoComparacion || saliendo) {
      return;
    }
    final base = periodoBase ?? baseComparacion;
    final comparado = periodoComparado ?? comparadoComparacion;
    if (base == baseComparacion && comparado == comparadoComparacion) return;
    baseComparacion = base;
    comparadoComparacion = comparado;
    comparacion = null;
    errorComparacion = null;
    notifyListeners();
  }

  Future<void> cargarComparacion({
    String? periodoBase,
    String? periodoComparado,
    bool predeterminada = false,
  }) async {
    verificarVencimiento();
    final actual = sesion;
    if (_cerrado || actual == null || consultandoComparacion || saliendo) {
      return;
    }
    final base = predeterminada ? null : periodoBase ?? baseComparacion;
    final comparado = predeterminada
        ? null
        : periodoComparado ?? comparadoComparacion;
    if (base != baseComparacion || comparado != comparadoComparacion) {
      comparacion = null;
    }
    baseComparacion = base;
    comparadoComparacion = comparado;
    errorComparacion = null;
    consultandoComparacion = true;
    final version = _version;
    notifyListeners();
    try {
      final nueva = await api.comparar(
        actual,
        periodoBase: base,
        periodoComparado: comparado,
      );
      if (_cerrado || version != _version) return;
      comparacion = nueva;
      baseComparacion = nueva.base.estimacion.periodo;
      comparadoComparacion = nueva.comparado.estimacion.periodo;
      if (base == null && comparado == null) {
        actualComparacion = comparadoComparacion;
      }
    } on FalloApi catch (fallo) {
      if (_cerrado || version != _version) return;
      if (fallo.estado == 401) {
        _limpiar('Tu sesión terminó. Vuelve a ingresar.');
      } else {
        errorComparacion = fallo.estado == 400
            ? 'Elige ambos meses, sin periodos futuros y con hasta 12 meses de separación.'
            : fallo.mensaje;
      }
    } finally {
      if (!_cerrado && version == _version) {
        consultandoComparacion = false;
        notifyListeners();
      }
    }
  }

  Future<void> cargarProyeccion() async {
    verificarVencimiento();
    final actual = sesion;
    if (_cerrado || actual == null || consultandoProyeccion || saliendo) return;
    consultandoProyeccion = true;
    errorProyeccion = null;
    final version = _version;
    notifyListeners();
    try {
      final nueva = await api.proyeccion(actual);
      if (!_cerrado && version == _version) proyeccion = nueva;
    } on FalloApi catch (fallo) {
      if (_cerrado || version != _version) return;
      if (fallo.estado == 401) {
        _limpiar('Tu sesión terminó. Vuelve a ingresar.');
      } else {
        errorProyeccion = fallo.mensaje;
      }
    } finally {
      if (!_cerrado && version == _version) {
        consultandoProyeccion = false;
        notifyListeners();
      }
    }
  }

  void _limpiar(String mensaje) {
    ++_version;
    _temporizador?.cancel();
    sesion = null;
    consumo = null;
    estadisticas = null;
    errorEstadisticas = null;
    consultandoEstadisticas = false;
    mesesEstadisticas = 12;
    recibos = null;
    errorRecibos = null;
    consultandoRecibos = false;
    antesDeRecibos = null;
    comparacion = null;
    errorComparacion = null;
    consultandoComparacion = false;
    baseComparacion = comparadoComparacion = actualComparacion = null;
    proyeccion = null;
    errorProyeccion = null;
    consultandoProyeccion = false;
    costos = null;
    errorCostos = null;
    consultandoCostos = false;
    mesesCostos = 3;
    historial = null;
    errorHistorial = null;
    consultandoHistorial = false;
    mesesHistorial = 12;
    hastaHistorial = null;
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
    comparacion = null;
    recibos = null;
    estadisticas = null;
    proyeccion = null;
    costos = null;
    historial = null;
    api.cerrar();
    super.dispose();
  }
}
