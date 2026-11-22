import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:nivora_frontend/src/api_nivora.dart';
import 'package:nivora_frontend/src/aplicacion.dart';

import 'datos_prueba.dart';
import 'datos_dispositivo_prueba.dart';

Future<void> ingresar(WidgetTester tester, ServicioPrueba api) async {
  await tester.pumpWidget(
    NivoraApp(api: api, dispositivo: DispositivoPrueba()),
  );
  await tester.enterText(find.byType(TextFormField).at(0), '0013100390');
  await tester.enterText(find.byType(TextFormField).at(1), '01234567');
  await tester.ensureVisible(find.text('Ingresar'));
  await tester.tap(find.text('Ingresar'));
  await tester.pumpAndSettle();
  await tester.ensureVisible(find.text('Historial mensual'));
  await tester.tap(find.text('Historial mensual'));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets(
    'muestra meses y cobertura, detalle y conserva consumo al volver',
    (tester) async {
      final api = ServicioPrueba();
      await ingresar(tester, api);
      expect(api.consultasHistorial, 1);
      expect(find.text('Octubre 2026'), findsOneWidget);
      expect(find.text('0,000 m³'), findsOneWidget);
      expect(find.text('Consumo parcial'), findsNWidgets(3));
      expect(find.text('Sin datos suficientes'), findsNWidgets(3));
      expect(find.text('Lecturas en revisión'), findsNWidgets(3));
      expect(find.text('Sin valor disponible'), findsNWidgets(6));
      await tester.ensureVisible(find.text('Octubre 2026'));
      await tester.tap(find.text('Octubre 2026'));
      await tester.pumpAndSettle();
      expect(find.text('Las lecturas cubren todo el mes.'), findsOneWidget);
      expect(find.textContaining('01/10/2026 00:00 (Lima)'), findsOneWidget);
      await tester.ensureVisible(find.text('Consumo actual'));
      await tester.tap(find.text('Consumo actual'));
      await tester.pumpAndSettle();
      expect(find.text('1,250 m³'), findsOneWidget);
      await tester.tap(find.text('Historial mensual'));
      await tester.pumpAndSettle();
      expect(api.consultasHistorial, 1);
      await tester.tap(find.byTooltip('Cerrar sesión'));
      await tester.pumpAndSettle();
      expect(find.text('Consulta tu suministro'), findsOneWidget);
      expect(find.text('Octubre 2026'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets('cambia número de meses y navega entre ventanas', (tester) async {
    final api = ServicioPrueba();
    await ingresar(tester, api);
    await tester.ensureVisible(find.byType(DropdownButton<int>));
    await tester.tap(find.byType(DropdownButton<int>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('3 meses').last);
    await tester.pumpAndSettle();
    expect(api.rangosHistorial.last, (meses: 3, hasta: null));
    expect(find.text('Agosto 2026'), findsOneWidget);
    expect(find.text('Julio 2026'), findsNothing);
    await tester.ensureVisible(find.text('Meses anteriores'));
    await tester.tap(find.text('Meses anteriores'));
    await tester.pumpAndSettle();
    expect(api.rangosHistorial.last, (meses: 3, hasta: '2026-07'));
    expect(find.text('Julio 2026'), findsOneWidget);
    await tester.ensureVisible(find.text('Más recientes'));
    await tester.tap(find.text('Más recientes'));
    await tester.pumpAndSettle();
    expect(api.rangosHistorial.last, (meses: 3, hasta: null));
    expect(find.text('Octubre 2026'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('fallo inicial y reintento cargan historial sin perder sesión', (
    tester,
  ) async {
    final api = ServicioPrueba()
      ..alHistorial = (_, _) async => throw const FalloApi('Sin conexión');
    await ingresar(tester, api);
    expect(find.text('Sin conexión'), findsOneWidget);
    expect(find.byType(ExpansionTile), findsNothing);
    api.alHistorial = null;
    await tester.ensureVisible(find.text('Reintentar historial'));
    await tester.tap(find.text('Reintentar historial'));
    await tester.pumpAndSettle();
    expect(find.text('Sin conexión'), findsNothing);
    expect(find.text('Octubre 2026'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('una carga pendiente permite cerrar sesión', (tester) async {
    final pendiente = Completer<HistorialMensual>();
    final api = ServicioPrueba();
    // Entra primero para poder observar el indicador sin pumpAndSettle.
    await ingresar(tester, api);
    api.alHistorial = (_, _) => pendiente.future;
    await tester.ensureVisible(find.text('Actualizar historial'));
    await tester.tap(find.text('Actualizar historial'));
    await tester.pump();
    expect(find.text('Consultando tu historial…'), findsOneWidget);
    await tester.tap(find.byTooltip('Cerrar sesión'));
    await tester.pumpAndSettle();
    pendiente.complete(
      HistorialMensual.desdeJson(respuestaHistorial(), '0013100390', meses: 12),
    );
    await tester.pumpAndSettle();
    expect(find.text('Consulta tu suministro'), findsOneWidget);
    expect(find.text('Octubre 2026'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  for (final tamano in [const Size(320, 640), const Size(1200, 800)]) {
    testWidgets('historial a ${tamano.width.toInt()} px sin desbordamientos', (
      tester,
    ) async {
      tester.view.physicalSize = tamano;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await ingresar(tester, ServicioPrueba());
      await tester.ensureVisible(find.text('Octubre 2026'));
      await tester.tap(find.text('Octubre 2026'));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.ensureVisible(find.text('Meses anteriores'));
      await tester.tap(find.text('Meses anteriores'));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }
}
