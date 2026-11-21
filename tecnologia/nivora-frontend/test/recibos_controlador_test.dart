import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';
import 'package:nivora_frontend/src/controlador_consulta.dart';

import 'datos_prueba.dart';
import 'datos_recibos_prueba.dart';

void main() {
  late ServicioPrueba api;
  late ControladorConsulta control;
  setUp(() async {
    api = ServicioPrueba();
    control = ControladorConsulta(api);
    await control.ingresar('0013100390', '01234567');
  });
  tearDown(() => control.dispose());

  test('carga a demanda sin alterar las otras consultas', () async {
    expect(api.consultasRecibos, 0);
    await control.cargarHistorial();
    await control.cargarCostos();
    await control.cargarProyeccion();
    await control.cargarComparacion();
    final otros = (
      control.consumo,
      control.historial,
      control.costos,
      control.proyeccion,
      control.comparacion,
    );
    await control.cargarRecibos();
    expect(api.rangosRecibos.single, (limite: 12, antesDe: null));
    expect(control.recibos!.recibos.first.periodo, '2026-10');
    expect((
      control.consumo,
      control.historial,
      control.costos,
      control.proyeccion,
      control.comparacion,
    ), otros);
  });

  test(
    'navega con el corte del servidor, refresca el rango y vuelve a recientes',
    () async {
      await control.cargarRecibos();
      await control.cargarRecibos(antesDe: control.recibos!.siguienteAntesDe);
      expect(control.antesDeRecibos, '2025-11');
      expect(control.recibos!.recibos.first.periodo, '2025-10');
      await control.cargarRecibos();
      expect(api.rangosRecibos.last, (limite: 12, antesDe: '2025-11'));
      await control.cargarRecibos(recientes: true);
      expect(api.rangosRecibos.last, (limite: 12, antesDe: null));
      expect(control.antesDeRecibos, isNull);
    },
  );

  test(
    'cambiar de página elimina los recibos anteriores durante la espera',
    () async {
      await control.cargarRecibos();
      final pendiente = Completer<PaginaRecibos>();
      api.alRecibos = (_, _) => pendiente.future;
      final carga = control.cargarRecibos(antesDe: '2025-11');
      expect(control.recibos, isNull);
      expect(control.consultandoRecibos, isTrue);
      await control.cargarRecibos(recientes: true);
      expect(api.consultasRecibos, 2);
      pendiente.complete(paginaRecibosPrueba(antesDe: '2025-11'));
      await carga;
      expect(control.consultandoRecibos, isFalse);
    },
  );

  test(
    '503 al refrescar conserva la página y reintentar elimina el aviso',
    () async {
      await control.cargarRecibos();
      final anterior = control.recibos;
      api.alRecibos = (_, _) async =>
          throw const FalloApi('Sin conexión', estado: 503);
      await control.cargarRecibos();
      expect(control.recibos, same(anterior));
      expect(control.errorRecibos, 'Sin conexión');
      api.alRecibos = null;
      await control.cargarRecibos();
      expect(control.errorRecibos, isNull);
    },
  );

  test('fallo al navegar no atribuye los recibos a otro rango', () async {
    await control.cargarRecibos();
    api.alRecibos = (_, _) async =>
        throw const FalloApi('Sin conexión', estado: 503);
    await control.cargarRecibos(antesDe: '2025-11');
    expect(control.recibos, isNull);
    expect(control.antesDeRecibos, '2025-11');
    api.alRecibos = null;
    await control.cargarRecibos();
    expect(control.recibos!.recibos.first.periodo, '2025-10');
  });

  test('400 permite regresar a los más recientes sin cerrar sesión', () async {
    api.alRecibos = (_, _) async =>
        throw const FalloApi('Inválido', estado: 400);
    await control.cargarRecibos(antesDe: '2025-11');
    expect(control.errorRecibos, contains('más recientes'));
    expect(control.sesion, isNotNull);
  });

  test('401 desde recibos borra todas las consultas y el corte', () async {
    await control.cargarHistorial();
    await control.cargarCostos();
    await control.cargarProyeccion();
    await control.cargarComparacion();
    await control.cargarRecibos();
    api.alRecibos = (_, _) async =>
        throw const FalloApi('Terminó', estado: 401);
    await control.cargarRecibos();
    expect([
      control.sesion,
      control.consumo,
      control.historial,
      control.costos,
      control.proyeccion,
      control.comparacion,
      control.recibos,
      control.antesDeRecibos,
    ], everyElement(isNull));
    expect(control.consultandoRecibos, isFalse);
  });

  for (final conError in [false, true]) {
    test(
      'descarta ${conError ? '401' : 'recibos'} de una sesión anterior',
      () async {
        final pendiente = Completer<PaginaRecibos>();
        api.alRecibos = (_, _) => pendiente.future;
        final carga = control.cargarRecibos();
        await control.salir();
        await control.ingresar('0013100390', '01234567');
        if (conError) {
          pendiente.completeError(const FalloApi('Terminó', estado: 401));
        } else {
          pendiente.complete(paginaRecibosPrueba());
        }
        await carga;
        expect(control.sesion, isNotNull);
        expect(control.recibos, isNull);
        expect(control.errorRecibos, isNull);
      },
    );
  }

  test(
    '401 de otra consulta invalida una página pendiente de recibos',
    () async {
      final pendiente = Completer<PaginaRecibos>();
      api.alRecibos = (_, _) => pendiente.future;
      final carga = control.cargarRecibos();
      api.alCostos = (_) async => throw const FalloApi('Terminó', estado: 401);
      await control.cargarCostos();
      pendiente.complete(paginaRecibosPrueba());
      await carga;
      expect(control.sesion, isNull);
      expect(control.recibos, isNull);
    },
  );

  test('vencimiento borra recibos y descarta respuestas pendientes', () async {
    var ahora = DateTime.now();
    final otro = ControladorConsulta(api, reloj: () => ahora);
    try {
      await otro.ingresar('0013100390', '01234567');
      await otro.cargarRecibos();
      final pendiente = Completer<PaginaRecibos>();
      api.alRecibos = (_, _) => pendiente.future;
      final carga = otro.cargarRecibos();
      ahora = ahora.add(const Duration(hours: 1));
      otro.verificarVencimiento();
      pendiente.complete(paginaRecibosPrueba());
      await carga;
      expect(otro.sesion, isNull);
      expect(otro.recibos, isNull);
    } finally {
      otro.dispose();
    }
  });
}
