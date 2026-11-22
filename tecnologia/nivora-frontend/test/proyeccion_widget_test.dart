import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:nivora_frontend/src/api_nivora.dart';
import 'package:nivora_frontend/src/aplicacion.dart';

import 'datos_prueba.dart';
import 'datos_dispositivo_prueba.dart';
import 'datos_proyeccion_prueba.dart';

Future<void> abrirProyeccion(WidgetTester tester, ServicioPrueba api) async {
  await tester.pumpWidget(
    NivoraApp(api: api, dispositivo: DispositivoPrueba()),
  );
  await tester.enterText(find.byType(TextFormField).at(0), '0013100390');
  await tester.enterText(find.byType(TextFormField).at(1), '01234567');
  await tester.ensureVisible(find.text('Ingresar'));
  await tester.tap(find.text('Ingresar'));
  await tester.pumpAndSettle();
  expect(api.consultasProyeccion, 0);
  await tester.ensureVisible(find.text('Proyección mensual'));
  await tester.tap(find.text('Proyección mensual'));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets(
    'distingue lo observado de lo proyectado y muestra un subtotal identificado',
    (tester) async {
      final api = ServicioPrueba();
      await abrirProyeccion(tester, api);
      expect(find.text('12,000 m³'), findsOneWidget);
      expect(find.text('12000 litros'), findsOneWidget);
      expect(find.text('4,000 m³ · 4000 litros'), findsOneWidget);
      expect(
        find.text('Promedio observado: 400,000 litros/día.'),
        findsOneWidget,
      );
      expect(find.text('Subtotal proyectado conocido'), findsOneWidget);
      expect(find.text('S/ 23.00'), findsOneWidget);
      await tester.ensureVisible(find.text('Subtotal proyectado conocido'));
      await tester.tap(find.text('Subtotal proyectado conocido'));
      await tester.pumpAndSettle();
      expect(find.text('No disponible'), findsNWidgets(3));
      expect(find.text('Tarifa aplicada'), findsOneWidget);
      await tester.ensureVisible(find.text('Consumo actual'));
      await tester.tap(find.text('Consumo actual'));
      await tester.pumpAndSettle();
      expect(find.text('1,250 m³'), findsOneWidget);
      await tester.tap(find.text('Proyección mensual'));
      await tester.pumpAndSettle();
      expect(api.consultasProyeccion, 1);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  final estados = {
    'SIN_CONSUMO_CALCULABLE': 'Proyección no disponible',
    'COBERTURA_INCOMPLETA': 'Falta cobertura para proyectar',
    'TIEMPO_INSUFICIENTE': 'Aún falta tiempo de observación',
    'PERIODO_CERRADO': 'El periodo ya terminó',
  };
  for (final estado in estados.entries) {
    testWidgets('${estado.key} explica por qué no hay proyección', (
      tester,
    ) async {
      final api = ServicioPrueba()
        ..alProyeccion = () async => ProyeccionConsumo.desdeJson(
          respuestaProyeccion(estado: estado.key),
          '0013100390',
        );
      await abrirProyeccion(tester, api);
      expect(find.text(estado.value), findsOneWidget);
      expect(find.text('Consumo proyectado al cierre'), findsNothing);
      expect(find.text('S/ 0.00'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }

  testWidgets(
    'tarifa ausente conserva el volumen proyectado sin inventar importe',
    (tester) async {
      final api = ServicioPrueba()
        ..alProyeccion = () async => ProyeccionConsumo.desdeJson(
          respuestaProyeccion(estadoCosto: 'SIN_TARIFA_COMPATIBLE'),
          '0013100390',
        );
      await abrirProyeccion(tester, api);
      expect(find.text('12,000 m³'), findsOneWidget);
      expect(find.text('Costo proyectado no disponible'), findsOneWidget);
      expect(find.text('S/ 0.00'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets('costo completo se identifica como total proyectado estimado', (
    tester,
  ) async {
    final api = ServicioPrueba()
      ..alProyeccion = () async => ProyeccionConsumo.desdeJson(
        respuestaProyeccion(estadoCosto: 'ESTIMADO'),
        '0013100390',
      );
    await abrirProyeccion(tester, api);
    expect(find.text('Total proyectado estimado'), findsOneWidget);
    expect(find.text('S/ 27.14'), findsOneWidget);
    expect(find.text('Subtotal proyectado conocido'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
    'un fallo inicial permite reintentar y otro al refrescar conserva datos identificados',
    (tester) async {
      final api = ServicioPrueba()
        ..alProyeccion = () async => throw const FalloApi('Sin conexión');
      await abrirProyeccion(tester, api);
      expect(find.text('Sin conexión'), findsOneWidget);
      api.alProyeccion = null;
      await tester.ensureVisible(find.text('Reintentar proyección'));
      await tester.tap(find.text('Reintentar proyección'));
      await tester.pumpAndSettle();
      expect(find.text('12,000 m³'), findsOneWidget);
      api.alProyeccion = () async => throw const FalloApi('Sin conexión');
      await tester.ensureVisible(find.text('Actualizar proyección'));
      await tester.tap(find.text('Actualizar proyección'));
      await tester.pumpAndSettle();
      expect(find.text('12,000 m³'), findsOneWidget);
      expect(
        find.text('Se muestra la última proyección consultada.'),
        findsOneWidget,
      );
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets('cierra sesión durante la espera y descarta la respuesta', (
    tester,
  ) async {
    final api = ServicioPrueba();
    await abrirProyeccion(tester, api);
    final pendiente = Completer<ProyeccionConsumo>();
    api.alProyeccion = () => pendiente.future;
    await tester.ensureVisible(find.text('Actualizar proyección'));
    await tester.tap(find.text('Actualizar proyección'));
    await tester.pump();
    expect(find.text('Consultando tu proyección…'), findsOneWidget);
    await tester.tap(find.byTooltip('Cerrar sesión'));
    await tester.pumpAndSettle();
    pendiente.complete(proyeccionPrueba());
    await tester.pumpAndSettle();
    expect(find.text('Consulta tu suministro'), findsOneWidget);
    expect(find.text('12,000 m³'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  for (final tamano in [const Size(320, 640), const Size(1200, 800)]) {
    testWidgets(
      'proyección a ${tamano.width.toInt()} px con detalle sin desbordamientos',
      (tester) async {
        tester.view.physicalSize = tamano;
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        await abrirProyeccion(tester, ServicioPrueba());
        await tester.ensureVisible(find.text('Subtotal proyectado conocido'));
        await tester.tap(find.text('Subtotal proyectado conocido'));
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        await tester.ensureVisible(find.text('Consumo observado'));
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox.shrink());
      },
    );
  }
}
