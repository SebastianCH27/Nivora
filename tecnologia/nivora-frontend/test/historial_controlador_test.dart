import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';
import 'package:nivora_frontend/src/controlador_consulta.dart';

import 'datos_prueba.dart';

HistorialMensual datos() =>
    HistorialMensual.desdeJson(respuestaHistorial(), '0013100390', meses: 12);

void main() {
  late ServicioPrueba api;
  late ControladorConsulta control;
  setUp(() async {
    api = ServicioPrueba();
    control = ControladorConsulta(api);
    await control.ingresar('0013100390', '01234567');
  });
  tearDown(() => control.dispose());

  test(
    'carga a demanda y conserva el consumo al consultar otros meses',
    () async {
      expect(api.consultasHistorial, 0);
      final consumo = control.consumo;
      await control.cargarHistorial(meses: 3, hasta: '2026-07');
      expect(api.rangosHistorial.single, (meses: 3, hasta: '2026-07'));
      expect(control.historial!.periodos.first.periodo, '2026-07');
      expect(control.consumo, same(consumo));
      await control.cargarHistorial(ultimos: true);
      expect(control.hastaHistorial, isNull);
      expect(control.historial!.periodos.first.periodo, '2026-10');
    },
  );

  test('evita una consulta doble durante la carga', () async {
    final pendiente = Completer<HistorialMensual>();
    api.alHistorial = (_, _) => pendiente.future;
    final carga = control.cargarHistorial();
    await control.cargarHistorial(meses: 3);
    expect(api.consultasHistorial, 1);
    expect(control.consultandoHistorial, isTrue);
    pendiente.complete(datos());
    await carga;
    expect(control.consultandoHistorial, isFalse);
    expect(control.mesesHistorial, 12);
  });

  test(
    'un fallo al refrescar conserva el rango anterior y permite reintentar',
    () async {
      await control.cargarHistorial();
      final anteriores = control.historial;
      api.alHistorial = (_, _) async => throw const FalloApi('Sin conexión');
      await control.cargarHistorial();
      expect(control.historial, same(anteriores));
      expect(control.errorHistorial, 'Sin conexión');
      expect(control.sesion, isNotNull);
      api.alHistorial = null;
      await control.cargarHistorial();
      expect(control.errorHistorial, isNull);
    },
  );

  test(
    'un filtro nuevo fallido no muestra datos del filtro anterior',
    () async {
      await control.cargarHistorial();
      api.alHistorial = (_, _) async =>
          throw const FalloApi('No disponible', estado: 503);
      await control.cargarHistorial(meses: 3, hasta: '2025-10');
      expect(control.historial, isNull);
      expect(control.errorHistorial, 'No disponible');
      expect(control.mesesHistorial, 3);
      expect(control.hastaHistorial, '2025-10');
      expect(control.consumo, isNotNull);
    },
  );

  test(
    '401 en historial elimina todos los datos y filtros de sesión',
    () async {
      await control.cargarHistorial(meses: 3, hasta: '2025-10');
      api.alHistorial = (_, _) async =>
          throw const FalloApi('Terminó', estado: 401);
      await control.cargarHistorial();
      expect(control.historial, isNull);
      expect(control.consumo, isNull);
      expect(control.sesion, isNull);
      expect(control.hastaHistorial, isNull);
      expect(control.mesesHistorial, 12);
    },
  );

  test(
    'respuesta antigua no repone historial después de salir y volver a entrar',
    () async {
      final pendiente = Completer<HistorialMensual>();
      api.alHistorial = (_, _) => pendiente.future;
      final carga = control.cargarHistorial();
      await control.salir();
      await control.ingresar('0013100390', '01234567');
      pendiente.complete(datos());
      await carga;
      expect(control.sesion, isNotNull);
      expect(control.historial, isNull);
    },
  );

  test('401 de consumo invalida también un historial pendiente', () async {
    final pendiente = Completer<HistorialMensual>();
    api.alHistorial = (_, _) => pendiente.future;
    final carga = control.cargarHistorial();
    api.alConsultar = () async => throw const FalloApi('Terminó', estado: 401);
    await control.actualizar();
    pendiente.complete(datos());
    await carga;
    expect(control.historial, isNull);
    expect(control.sesion, isNull);
  });

  test(
    'vencimiento elimina historial y descarta la consulta pendiente',
    () async {
      var ahora = DateTime.now();
      final conReloj = ControladorConsulta(api, reloj: () => ahora);
      try {
        await conReloj.ingresar('0013100390', '01234567');
        await conReloj.cargarHistorial();
        final pendiente = Completer<HistorialMensual>();
        api.alHistorial = (_, _) => pendiente.future;
        final carga = conReloj.cargarHistorial();
        ahora = ahora.add(const Duration(hours: 1));
        conReloj.verificarVencimiento();
        pendiente.complete(datos());
        await carga;
        expect(conReloj.historial, isNull);
        expect(conReloj.sesion, isNull);
      } finally {
        conReloj.dispose();
      }
    },
  );
}
