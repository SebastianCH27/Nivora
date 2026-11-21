import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';
import 'package:nivora_frontend/src/controlador_consulta.dart';

import 'datos_comparacion_prueba.dart';
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
    expect(api.consultasComparacion, 0);
    await control.cargarHistorial();
    await control.cargarCostos();
    await control.cargarProyeccion();
    final previos = (
      control.consumo,
      control.historial,
      control.costos,
      control.proyeccion,
    );
    await control.cargarComparacion();
    expect(api.rangosComparacion.single, (base: null, comparado: null));
    expect(control.actualComparacion, '2026-11');
    expect((
      control.consumo,
      control.historial,
      control.costos,
      control.proyeccion,
    ), previos);
  });

  test(
    'seleccionar no consulta y elimina datos de los meses anteriores',
    () async {
      await control.cargarComparacion();
      control.seleccionarComparacion(periodoBase: '2026-09');
      expect(control.comparacion, isNull);
      expect(api.consultasComparacion, 1);
      expect(control.comparadoComparacion, '2026-11');
      await control.cargarComparacion();
      expect(api.rangosComparacion.last, (
        base: '2026-09',
        comparado: '2026-11',
      ));
      expect(control.comparacion!.base.estimacion.periodo, '2026-09');
    },
  );

  test('restablecer actual y anterior omite ambos parámetros', () async {
    await control.cargarComparacion();
    await control.cargarComparacion(
      periodoBase: '2026-09',
      periodoComparado: '2026-10',
    );
    await control.cargarComparacion(predeterminada: true);
    expect(api.rangosComparacion.last, (base: null, comparado: null));
    expect(control.baseComparacion, '2026-10');
    expect(control.comparadoComparacion, '2026-11');
  });

  test(
    'evita peticiones dobles y cambios de filtro durante la espera',
    () async {
      await control.cargarComparacion();
      final pendiente = Completer<ComparacionConsumo>();
      api.alComparar = (_, _) => pendiente.future;
      final carga = control.cargarComparacion();
      await control.cargarComparacion();
      control.seleccionarComparacion(periodoBase: '2026-09');
      expect(api.consultasComparacion, 2);
      expect(control.baseComparacion, '2026-10');
      pendiente.complete(comparacionPrueba());
      await carga;
      expect(control.consultandoComparacion, isFalse);
    },
  );

  test(
    '503 al refrescar conserva datos y reintentar elimina el error',
    () async {
      await control.cargarComparacion();
      final previa = control.comparacion;
      api.alComparar = (_, _) async =>
          throw const FalloApi('Sin conexión', estado: 503);
      await control.cargarComparacion();
      expect(control.comparacion, same(previa));
      expect(control.errorComparacion, 'Sin conexión');
      api.alComparar = null;
      await control.cargarComparacion();
      expect(control.errorComparacion, isNull);
    },
  );

  test(
    'fallo con otros meses no muestra resultados de la selección anterior',
    () async {
      await control.cargarComparacion();
      api.alComparar = (_, _) async =>
          throw const FalloApi('Sin conexión', estado: 503);
      await control.cargarComparacion(periodoBase: '2026-09');
      expect(control.comparacion, isNull);
      expect(control.errorComparacion, 'Sin conexión');
    },
  );

  test('400 explica el rango sin cerrar sesión', () async {
    api.alComparar = (_, _) async => throw const FalloApi('Error', estado: 400);
    await control.cargarComparacion();
    expect(control.errorComparacion, contains('12 meses'));
    expect(control.sesion, isNotNull);
  });

  test('401 limpia todas las consultas y los meses seleccionados', () async {
    await control.cargarHistorial();
    await control.cargarCostos();
    await control.cargarProyeccion();
    await control.cargarComparacion();
    api.alComparar = (_, _) async =>
        throw const FalloApi('Terminó', estado: 401);
    await control.cargarComparacion();
    expect([
      control.sesion,
      control.consumo,
      control.historial,
      control.costos,
      control.proyeccion,
      control.comparacion,
      control.actualComparacion,
      control.baseComparacion,
    ], everyElement(isNull));
    expect(control.consultandoComparacion, isFalse);
  });

  for (final conError in [false, true]) {
    test(
      'descarta ${conError ? '401' : 'datos'} de una sesión anterior',
      () async {
        final pendiente = Completer<ComparacionConsumo>();
        api.alComparar = (_, _) => pendiente.future;
        final carga = control.cargarComparacion();
        await control.salir();
        await control.ingresar('0013100390', '01234567');
        if (conError) {
          pendiente.completeError(const FalloApi('Terminó', estado: 401));
        } else {
          pendiente.complete(comparacionPrueba());
        }
        await carga;
        expect(control.sesion, isNotNull);
        expect(control.comparacion, isNull);
        expect(control.errorComparacion, isNull);
      },
    );
  }

  test('401 de costos invalida una comparación pendiente', () async {
    final pendiente = Completer<ComparacionConsumo>();
    api.alComparar = (_, _) => pendiente.future;
    final carga = control.cargarComparacion();
    api.alCostos = (_) async => throw const FalloApi('Terminó', estado: 401);
    await control.cargarCostos();
    pendiente.complete(comparacionPrueba());
    await carga;
    expect(control.sesion, isNull);
    expect(control.comparacion, isNull);
  });

  test('el vencimiento descarta una comparación pendiente', () async {
    var ahora = DateTime.now();
    final otro = ControladorConsulta(api, reloj: () => ahora);
    try {
      await otro.ingresar('0013100390', '01234567');
      final pendiente = Completer<ComparacionConsumo>();
      api.alComparar = (_, _) => pendiente.future;
      final carga = otro.cargarComparacion();
      ahora = ahora.add(const Duration(hours: 1));
      otro.verificarVencimiento();
      pendiente.complete(comparacionPrueba());
      await carga;
      expect(otro.sesion, isNull);
      expect(otro.comparacion, isNull);
    } finally {
      otro.dispose();
    }
  });
}
