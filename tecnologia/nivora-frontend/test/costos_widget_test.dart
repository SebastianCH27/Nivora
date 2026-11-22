import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:nivora_frontend/src/api_nivora.dart';
import 'package:nivora_frontend/src/aplicacion.dart';

import 'datos_costos_prueba.dart';
import 'datos_prueba.dart';
import 'datos_dispositivo_prueba.dart';

Future<void> abrirCostos(WidgetTester tester, ServicioPrueba api) async {
  await tester.pumpWidget(
    NivoraApp(api: api, dispositivo: DispositivoPrueba()),
  );
  await tester.enterText(find.byType(TextFormField).at(0), '0013100390');
  await tester.enterText(find.byType(TextFormField).at(1), '01234567');
  await tester.ensureVisible(find.text('Ingresar'));
  await tester.tap(find.text('Ingresar'));
  await tester.pumpAndSettle();
  expect(api.consultasCostos, 0);
  await tester.ensureVisible(find.text('Costos estimados'));
  await tester.tap(find.text('Costos estimados'));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets(
    'costos distingue subtotal y total y muestra el desglose disponible',
    (tester) async {
      final api = ServicioPrueba();
      await abrirCostos(tester, api);
      expect(find.text('S/ 15.88'), findsOneWidget);
      expect(find.text('S/ 13.46'), findsOneWidget);
      expect(find.text('Sin tarifa compatible'), findsOneWidget);
      expect(find.text('Sin consumo calculable'), findsOneWidget);
      final octubre = find.byKey(const ValueKey('costo-2026-10'));
      await tester.ensureVisible(octubre);
      await tester.tap(
        find.descendant(of: octubre, matching: find.text('Octubre 2026')),
      );
      await tester.pumpAndSettle();
      expect(
        find.text('Alcantarillado: la tarifa no informa el importe.'),
        findsOneWidget,
      );
      expect(find.text('IGV: la tarifa no informa la tasa.'), findsOneWidget);
      expect(find.text('Tarifa aplicada'), findsOneWidget);
      expect(find.text('Importe no disponible'), findsNWidgets(2));
      expect(find.text('No disponible'), findsNWidgets(3));
      await tester.ensureVisible(find.text('Consumo actual'));
      await tester.tap(find.text('Consumo actual'));
      await tester.pumpAndSettle();
      expect(find.text('1,250 m³'), findsOneWidget);
      await tester.tap(find.text('Costos estimados'));
      await tester.pumpAndSettle();
      expect(api.consultasCostos, 1);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'selector cambia a 6 y 12 meses anteriores además del mes actual',
    (tester) async {
      final api = ServicioPrueba();
      await abrirCostos(tester, api);
      for (final meses in [6, 12]) {
        await tester.ensureVisible(find.byType(DropdownButton<int>));
        await tester.tap(find.byType(DropdownButton<int>));
        await tester.pumpAndSettle();
        await tester.tap(find.text('$meses meses anteriores').last);
        await tester.pumpAndSettle();
        expect(api.rangosCostos.last, meses);
        expect(find.byType(ExpansionTile), findsNWidgets(meses + 1));
      }
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'consumo parcial conserva una advertencia visible junto al importe',
    (tester) async {
      final api = ServicioPrueba()
        ..alCostos = (meses) async {
          final json = respuestaCostos(meses: meses);
          json['actual']['consumo']['estado'] = 'PARCIAL';
          json['actual']['consumo']['desde'] = '2026-11-02T05:00:00Z';
          return CostosConsumo.desdeJson(json, '0013100390', meses: meses);
        };
      await abrirCostos(tester, api);
      expect(find.text('Consumo parcial'), findsOneWidget);
      expect(
        find.text(
          'Importe sobre el consumo conocido. Puede faltar parte del mes.',
        ),
        findsOneWidget,
      );
      expect(find.text('S/ 15.88'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'fallo al actualizar conserva datos identificados y reintento recupera',
    (tester) async {
      final api = ServicioPrueba();
      await abrirCostos(tester, api);
      api.alCostos = (_) async => throw const FalloApi('Sin conexión');
      await tester.ensureVisible(find.text('Actualizar costos'));
      await tester.tap(find.text('Actualizar costos'));
      await tester.pumpAndSettle();
      expect(find.text('Sin conexión'), findsOneWidget);
      expect(
        find.text(
          'Se muestran los últimos costos consultados para este rango.',
        ),
        findsOneWidget,
      );
      expect(find.text('S/ 15.88'), findsOneWidget);
      api.alCostos = null;
      await tester.ensureVisible(find.text('Reintentar costos'));
      await tester.tap(find.text('Reintentar costos'));
      await tester.pumpAndSettle();
      expect(find.text('Sin conexión'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    '401 desde costos regresa al formulario sin datos del suministro',
    (tester) async {
      final api = ServicioPrueba()
        ..alCostos = (_) async => throw const FalloApi('Terminó', estado: 401);
      await abrirCostos(tester, api);
      expect(find.text('Consulta tu suministro'), findsOneWidget);
      expect(find.text('S/ 15.88'), findsNothing);
      expect(
        find.text('Tu sesión terminó. Vuelve a ingresar.'),
        findsOneWidget,
      );
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'una petición pendiente permite cerrar sesión y descarta su resultado',
    (tester) async {
      final api = ServicioPrueba();
      await abrirCostos(tester, api);
      final pendiente = Completer<CostosConsumo>();
      api.alCostos = (_) => pendiente.future;
      await tester.ensureVisible(find.text('Actualizar costos'));
      await tester.tap(find.text('Actualizar costos'));
      await tester.pump();
      expect(find.text('Consultando tus costos…'), findsOneWidget);
      await tester.tap(find.byTooltip('Cerrar sesión'));
      await tester.pumpAndSettle();
      pendiente.complete(costosPrueba());
      await tester.pumpAndSettle();
      expect(find.text('Consulta tu suministro'), findsOneWidget);
      expect(find.text('S/ 15.88'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  for (final tamano in [const Size(320, 640), const Size(1200, 800)]) {
    testWidgets(
      'costos a ${tamano.width.toInt()} px con detalle sin desbordamientos',
      (tester) async {
        tester.view.physicalSize = tamano;
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        await abrirCostos(tester, ServicioPrueba());
        final mes = find.byKey(const ValueKey('costo-2026-11'));
        await tester.ensureVisible(mes);
        await tester.tap(
          find.descendant(of: mes, matching: find.text('Noviembre 2026')),
        );
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        await tester.ensureVisible(find.text('Cargo fijo mensual'));
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox.shrink());
      },
    );
  }
}
