import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';
import 'package:nivora_frontend/src/controlador_consulta.dart';

import 'datos_costos_prueba.dart';
import 'datos_prueba.dart';

void main() {
  late ServicioPrueba api;
  late ControladorConsulta control;
  setUp(() async {
    api = ServicioPrueba();
    control = ControladorConsulta(api);
    await control.ingresar('0013100390', '01234567');
  });
  tearDown(() => control.dispose());

  test('costos carga a demanda y no sustituye consumo ni historial', () async {
    expect(api.consultasCostos, 0);
    await control.cargarHistorial();
    final consumo = control.consumo;
    final historial = control.historial;
    await control.cargarCostos(meses: 6);
    expect(api.rangosCostos.single, 6);
    expect(control.costos!.anteriores.length, 6);
    expect(control.consumo, same(consumo));
    expect(control.historial, same(historial));
  });

  test('una doble carga genera una sola petición', () async {
    final pendiente = Completer<CostosConsumo>();
    api.alCostos = (_) => pendiente.future;
    final carga = control.cargarCostos();
    await control.cargarCostos(meses: 6);
    expect(api.consultasCostos, 1);
    expect(control.consultandoCostos, isTrue);
    pendiente.complete(costosPrueba());
    await carga;
    expect(control.consultandoCostos, isFalse);
    expect(control.mesesCostos, 3);
  });

  test('fallo al refrescar conserva los costos del mismo rango', () async {
    await control.cargarCostos();
    final previos = control.costos;
    api.alCostos = (_) async => throw const FalloApi('Sin conexión');
    await control.cargarCostos();
    expect(control.costos, same(previos));
    expect(control.errorCostos, 'Sin conexión');
    api.alCostos = null;
    await control.cargarCostos();
    expect(control.errorCostos, isNull);
  });

  test(
    'cambiar filtro retira los costos anteriores incluso si hay 503',
    () async {
      await control.cargarCostos();
      api.alCostos = (_) async =>
          throw const FalloApi('No disponible', estado: 503);
      await control.cargarCostos(meses: 12);
      expect(control.mesesCostos, 12);
      expect(control.costos, isNull);
      expect(control.errorCostos, 'No disponible');
      expect(control.consumo, isNotNull);
    },
  );

  test(
    '401 en costos limpia todas las consultas y reinicia sus filtros',
    () async {
      await control.cargarHistorial(meses: 6);
      await control.cargarCostos(meses: 12);
      api.alCostos = (_) async => throw const FalloApi('Terminó', estado: 401);
      await control.cargarCostos();
      expect(control.sesion, isNull);
      expect(control.consumo, isNull);
      expect(control.historial, isNull);
      expect(control.costos, isNull);
      expect(control.mesesCostos, 3);
      expect(control.consultandoCostos, isFalse);
    },
  );

  test(
    'salir invalida costos pendientes aunque ya exista una nueva sesión',
    () async {
      final pendiente = Completer<CostosConsumo>();
      api.alCostos = (_) => pendiente.future;
      final carga = control.cargarCostos();
      await control.salir();
      await control.ingresar('0013100390', '01234567');
      pendiente.complete(costosPrueba());
      await carga;
      expect(control.sesion, isNotNull);
      expect(control.costos, isNull);
    },
  );

  test('401 tardío de una sesión anterior no cierra la nueva', () async {
    final pendiente = Completer<CostosConsumo>();
    api.alCostos = (_) => pendiente.future;
    final carga = control.cargarCostos();
    await control.salir();
    await control.ingresar('0013100390', '01234567');
    pendiente.completeError(const FalloApi('Terminó', estado: 401));
    await carga;
    expect(control.sesion, isNotNull);
    expect(control.consumo, isNotNull);
    expect(control.errorCostos, isNull);
  });

  test('401 de historial invalida costos que todavía estén llegando', () async {
    final pendiente = Completer<CostosConsumo>();
    api.alCostos = (_) => pendiente.future;
    final carga = control.cargarCostos();
    api.alHistorial = (_, _) async =>
        throw const FalloApi('Terminó', estado: 401);
    await control.cargarHistorial();
    pendiente.complete(costosPrueba());
    await carga;
    expect(control.sesion, isNull);
    expect(control.costos, isNull);
  });

  test('vencimiento elimina costos y descarta la consulta pendiente', () async {
    var ahora = DateTime.now();
    final conReloj = ControladorConsulta(api, reloj: () => ahora);
    try {
      await conReloj.ingresar('0013100390', '01234567');
      await conReloj.cargarCostos();
      final pendiente = Completer<CostosConsumo>();
      api.alCostos = (_) => pendiente.future;
      final carga = conReloj.cargarCostos();
      ahora = ahora.add(const Duration(hours: 1));
      conReloj.verificarVencimiento();
      pendiente.complete(costosPrueba());
      await carga;
      expect(conReloj.sesion, isNull);
      expect(conReloj.costos, isNull);
    } finally {
      conReloj.dispose();
    }
  });

  test('dispose descarta una respuesta pendiente sin notificar', () async {
    final otro = ControladorConsulta(api);
    await otro.ingresar('0013100390', '01234567');
    final pendiente = Completer<CostosConsumo>();
    api.alCostos = (_) => pendiente.future;
    final carga = otro.cargarCostos();
    otro.dispose();
    pendiente.complete(costosPrueba());
    await carga;
    expect(otro.costos, isNull);
  });
}
