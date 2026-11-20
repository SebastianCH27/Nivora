import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';
import 'package:nivora_frontend/src/controlador_consulta.dart';

import 'datos_prueba.dart';

void main() {
  late ServicioPrueba api;
  late ControladorConsulta control;
  setUp(() {
    api = ServicioPrueba();
    control = ControladorConsulta(api);
  });
  tearDown(() => control.dispose());

  test('ingresar carga el consumo y salir elimina datos locales', () async {
    await control.ingresar('0013100390', '01234567');
    expect(control.sesion, isNotNull);
    expect(control.consumo, isNotNull);
    await control.salir();
    expect(api.salidas, 1);
    expect(control.sesion, isNull);
    expect(control.consumo, isNull);
  });

  test('un doble envío crea una sola sesión', () async {
    final pendiente = Completer<SesionConsulta>();
    api.alIngresar = () => pendiente.future;
    final primero = control.ingresar('0013100390', '01234567');
    await control.ingresar('0013100390', '01234567');
    expect(api.ingresos, 1);
    pendiente.complete(sesionPrueba());
    await primero;
  });

  test('401 al consultar elimina sesión y consumo', () async {
    await control.ingresar('0013100390', '01234567');
    api.alConsultar = () async =>
        throw const FalloApi('No autorizado', estado: 401);
    await control.actualizar();
    expect(control.sesion, isNull);
    expect(control.consumo, isNull);
    expect(control.aviso, contains('Vuelve a ingresar'));
  });

  test(
    '503 mantiene la sesión y señala datos anteriores al fallar actualización',
    () async {
      await control.ingresar('0013100390', '01234567');
      api.alConsultar = () async =>
          throw const FalloApi('Servicio no disponible', estado: 503);
      await control.actualizar();
      expect(control.sesion, isNotNull);
      expect(control.consumo, isNotNull);
      expect(control.errorConsumo, 'Servicio no disponible');
    },
  );

  test(
    'salir sin conexión limpia localmente y comunica la revocación pendiente',
    () async {
      await control.ingresar('0013100390', '01234567');
      api.alSalir = () async => throw const FalloApi('Sin conexión');
      await control.salir();
      expect(control.sesion, isNull);
      expect(control.consumo, isNull);
      expect(control.aviso, contains('No pudimos confirmar'));
    },
  );

  test('respuesta pendiente no restaura consumo después de salir', () async {
    await control.ingresar('0013100390', '01234567');
    final pendiente = Completer<ConsumoActual>();
    api.alConsultar = () => pendiente.future;
    final actualizacion = control.actualizar();
    await control.salir();
    pendiente.complete(consumoPrueba());
    await actualizacion;
    expect(control.consumo, isNull);
    expect(control.sesion, isNull);
  });

  test('vencimiento se comprueba también al volver a primer plano', () async {
    var ahora = DateTime.now();
    final conReloj = ControladorConsulta(api, reloj: () => ahora);
    try {
      await conReloj.ingresar('0013100390', '01234567');
      ahora = ahora.add(const Duration(hours: 1));
      conReloj.verificarVencimiento();
      expect(conReloj.sesion, isNull);
      expect(conReloj.consumo, isNull);
      expect(conReloj.aviso, contains('venció'));
    } finally {
      conReloj.dispose();
    }
  });

  test('429 impide reintentar hasta que termine Retry-After', () async {
    var ahora = DateTime.now();
    final conReloj = ControladorConsulta(api, reloj: () => ahora);
    api.alIngresar = () async => throw const FalloApi(
      'Espera',
      estado: 429,
      reintentarEn: Duration(seconds: 12),
    );
    try {
      await conReloj.ingresar('0013100390', '01234567');
      await conReloj.ingresar('0013100390', '01234567');
      expect(api.ingresos, 1);
      expect(conReloj.segundosEspera, 12);
      ahora = ahora.add(const Duration(seconds: 13));
      await conReloj.ingresar('0013100390', '01234567');
      expect(api.ingresos, 2);
    } finally {
      conReloj.dispose();
    }
  });
}
