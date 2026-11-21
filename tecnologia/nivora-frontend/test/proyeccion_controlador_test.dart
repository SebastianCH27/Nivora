import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';
import 'package:nivora_frontend/src/controlador_consulta.dart';

import 'datos_prueba.dart';
import 'datos_proyeccion_prueba.dart';

void main() {
  late ServicioPrueba api;
  late ControladorConsulta control;
  setUp(() async {
    api = ServicioPrueba();
    control = ControladorConsulta(api);
    await control.ingresar('0013100390', '01234567');
  });
  tearDown(() => control.dispose());

  test('carga a demanda y conserva las otras consultas', () async {
    expect(api.consultasProyeccion, 0);
    await control.cargarHistorial();
    await control.cargarCostos();
    final consumo = control.consumo;
    final historial = control.historial;
    final costos = control.costos;
    await control.cargarProyeccion();
    expect(api.consultasProyeccion, 1);
    expect(control.proyeccion, isNotNull);
    expect(control.consumo, same(consumo));
    expect(control.historial, same(historial));
    expect(control.costos, same(costos));
  });

  test('evita peticiones dobles mientras espera la proyección', () async {
    final pendiente = Completer<ProyeccionConsumo>();
    api.alProyeccion = () => pendiente.future;
    final carga = control.cargarProyeccion();
    await control.cargarProyeccion();
    expect(api.consultasProyeccion, 1);
    pendiente.complete(proyeccionPrueba());
    await carga;
    expect(control.consultandoProyeccion, isFalse);
  });

  test(
    '503 conserva la proyección anterior y reintentar limpia el aviso',
    () async {
      await control.cargarProyeccion();
      final previa = control.proyeccion;
      api.alProyeccion = () async =>
          throw const FalloApi('No disponible', estado: 503);
      await control.cargarProyeccion();
      expect(control.proyeccion, same(previa));
      expect(control.errorProyeccion, 'No disponible');
      api.alProyeccion = null;
      await control.cargarProyeccion();
      expect(control.errorProyeccion, isNull);
    },
  );

  test(
    '401 desde proyección limpia sesión, consumo, costos e historial',
    () async {
      await control.cargarHistorial();
      await control.cargarCostos();
      await control.cargarProyeccion();
      api.alProyeccion = () async =>
          throw const FalloApi('Terminó', estado: 401);
      await control.cargarProyeccion();
      expect(control.sesion, isNull);
      expect(control.consumo, isNull);
      expect(control.historial, isNull);
      expect(control.costos, isNull);
      expect(control.proyeccion, isNull);
      expect(control.consultandoProyeccion, isFalse);
    },
  );

  test(
    'salir descarta una proyección que llega después de un nuevo ingreso',
    () async {
      final pendiente = Completer<ProyeccionConsumo>();
      api.alProyeccion = () => pendiente.future;
      final carga = control.cargarProyeccion();
      await control.salir();
      await control.ingresar('0013100390', '01234567');
      pendiente.complete(proyeccionPrueba());
      await carga;
      expect(control.sesion, isNotNull);
      expect(control.proyeccion, isNull);
    },
  );

  test(
    '401 tardío de una proyección antigua no cierra la sesión nueva',
    () async {
      final pendiente = Completer<ProyeccionConsumo>();
      api.alProyeccion = () => pendiente.future;
      final carga = control.cargarProyeccion();
      await control.salir();
      await control.ingresar('0013100390', '01234567');
      pendiente.completeError(const FalloApi('Terminó', estado: 401));
      await carga;
      expect(control.sesion, isNotNull);
      expect(control.errorProyeccion, isNull);
    },
  );

  test(
    'vencimiento elimina datos y descarta una proyección pendiente',
    () async {
      var ahora = DateTime.now();
      final otro = ControladorConsulta(api, reloj: () => ahora);
      try {
        await otro.ingresar('0013100390', '01234567');
        final pendiente = Completer<ProyeccionConsumo>();
        api.alProyeccion = () => pendiente.future;
        final carga = otro.cargarProyeccion();
        ahora = ahora.add(const Duration(hours: 1));
        otro.verificarVencimiento();
        pendiente.complete(proyeccionPrueba());
        await carga;
        expect(otro.sesion, isNull);
        expect(otro.proyeccion, isNull);
      } finally {
        otro.dispose();
      }
    },
  );

  test('401 de costos invalida también una proyección pendiente', () async {
    final pendiente = Completer<ProyeccionConsumo>();
    api.alProyeccion = () => pendiente.future;
    final carga = control.cargarProyeccion();
    api.alCostos = (_) async => throw const FalloApi('Terminó', estado: 401);
    await control.cargarCostos();
    pendiente.complete(proyeccionPrueba());
    await carga;
    expect(control.sesion, isNull);
    expect(control.proyeccion, isNull);
  });
}
