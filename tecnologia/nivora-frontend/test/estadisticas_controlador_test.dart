import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';
import 'package:nivora_frontend/src/controlador_consulta.dart';

import 'datos_estadisticas_prueba.dart';
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

  test('carga a demanda y conserva las otras consultas', () async {
    expect(api.consultasEstadisticas, 0);
    await control.cargarHistorial();
    await control.cargarCostos();
    await control.cargarProyeccion();
    await control.cargarComparacion();
    await control.cargarRecibos();
    final otros = (
      control.consumo,
      control.historial,
      control.costos,
      control.proyeccion,
      control.comparacion,
      control.recibos,
    );
    await control.cargarEstadisticas();
    expect(api.rangosEstadisticas.single, 12);
    expect((
      control.consumo,
      control.historial,
      control.costos,
      control.proyeccion,
      control.comparacion,
      control.recibos,
    ), otros);
  });

  test(
    'cambiar el rango elimina los datos anteriores durante la espera',
    () async {
      await control.cargarEstadisticas();
      final pendiente = Completer<EstadisticasConsumo>();
      api.alEstadisticas = (_) => pendiente.future;
      final carga = control.cargarEstadisticas(meses: 3);
      expect(control.estadisticas, isNull);
      expect(control.mesesEstadisticas, 3);
      await control.cargarEstadisticas(meses: 6);
      expect(api.rangosEstadisticas, [12, 3]);
      pendiente.complete(estadisticasPrueba(meses: 3));
      await carga;
      expect(control.consultandoEstadisticas, isFalse);
    },
  );

  test(
    '503 al refrescar conserva los datos y reintentar limpia el aviso',
    () async {
      await control.cargarEstadisticas();
      final anterior = control.estadisticas;
      api.alEstadisticas = (_) async =>
          throw const FalloApi('Sin conexión', estado: 503);
      await control.cargarEstadisticas();
      expect(control.estadisticas, same(anterior));
      expect(control.errorEstadisticas, 'Sin conexión');
      api.alEstadisticas = null;
      await control.cargarEstadisticas();
      expect(control.errorEstadisticas, isNull);
    },
  );

  test(
    'fallo al cambiar de rango no atribuye los datos al filtro nuevo',
    () async {
      await control.cargarEstadisticas();
      api.alEstadisticas = (_) async =>
          throw const FalloApi('Sin conexión', estado: 503);
      await control.cargarEstadisticas(meses: 3);
      expect(control.estadisticas, isNull);
      api.alEstadisticas = null;
      await control.cargarEstadisticas();
      expect(control.estadisticas!.serie, hasLength(3));
      expect(api.rangosEstadisticas.last, 3);
    },
  );

  test('400 explica el rango sin cerrar sesión', () async {
    api.alEstadisticas = (_) async =>
        throw const FalloApi('Inválido', estado: 400);
    await control.cargarEstadisticas();
    expect(control.errorEstadisticas, contains('3, 6 o 12'));
    expect(control.sesion, isNotNull);
  });

  test('401 borra todas las consultas y restablece el rango', () async {
    await control.cargarHistorial();
    await control.cargarCostos();
    await control.cargarProyeccion();
    await control.cargarComparacion();
    await control.cargarRecibos();
    await control.cargarEstadisticas(meses: 3);
    api.alEstadisticas = (_) async =>
        throw const FalloApi('Terminó', estado: 401);
    await control.cargarEstadisticas();
    expect([
      control.sesion,
      control.consumo,
      control.historial,
      control.costos,
      control.proyeccion,
      control.comparacion,
      control.recibos,
      control.estadisticas,
    ], everyElement(isNull));
    expect(control.mesesEstadisticas, 12);
  });

  for (final conError in [false, true]) {
    test(
      'descarta ${conError ? '401' : 'estadísticas'} de una sesión anterior',
      () async {
        final pendiente = Completer<EstadisticasConsumo>();
        api.alEstadisticas = (_) => pendiente.future;
        final carga = control.cargarEstadisticas();
        await control.salir();
        await control.ingresar('0013100390', '01234567');
        if (conError) {
          pendiente.completeError(const FalloApi('Terminó', estado: 401));
        } else {
          pendiente.complete(estadisticasPrueba());
        }
        await carga;
        expect(control.sesion, isNotNull);
        expect(control.estadisticas, isNull);
        expect(control.errorEstadisticas, isNull);
      },
    );
  }

  test('401 desde recibos invalida estadísticas pendientes', () async {
    final pendiente = Completer<EstadisticasConsumo>();
    api.alEstadisticas = (_) => pendiente.future;
    final carga = control.cargarEstadisticas();
    api.alRecibos = (_, _) async =>
        throw const FalloApi('Terminó', estado: 401);
    await control.cargarRecibos();
    pendiente.complete(estadisticasPrueba());
    await carga;
    expect(control.sesion, isNull);
    expect(control.estadisticas, isNull);
  });

  test('el vencimiento descarta la consulta pendiente', () async {
    var ahora = DateTime.now();
    final otro = ControladorConsulta(api, reloj: () => ahora);
    try {
      await otro.ingresar('0013100390', '01234567');
      final pendiente = Completer<EstadisticasConsumo>();
      api.alEstadisticas = (_) => pendiente.future;
      final carga = otro.cargarEstadisticas();
      ahora = ahora.add(const Duration(hours: 1));
      otro.verificarVencimiento();
      pendiente.complete(estadisticasPrueba());
      await carga;
      expect(otro.sesion, isNull);
      expect(otro.estadisticas, isNull);
    } finally {
      otro.dispose();
    }
  });
}
