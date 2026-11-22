import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:nivora_frontend/src/api_nivora.dart';
import 'package:nivora_frontend/src/aplicacion.dart';

import 'datos_comparacion_prueba.dart';
import 'datos_prueba.dart';
import 'datos_dispositivo_prueba.dart';

Future<void> abrirComparacion(WidgetTester tester, ServicioPrueba api) async {
  await tester.pumpWidget(
    NivoraApp(api: api, dispositivo: DispositivoPrueba()),
  );
  await tester.enterText(find.byType(TextFormField).at(0), '0013100390');
  await tester.enterText(find.byType(TextFormField).at(1), '01234567');
  await tester.ensureVisible(find.text('Ingresar'));
  await tester.tap(find.text('Ingresar'));
  await tester.pumpAndSettle();
  expect(api.consultasComparacion, 0);
  await tester.ensureVisible(find.text('Comparación'));
  await tester.tap(find.text('Comparación'));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('muestra volumen y costo con signo y advierte el mes en curso', (
    tester,
  ) async {
    await abrirComparacion(tester, ServicioPrueba());
    expect(find.text('Comparación parcial'), findsOneWidget);
    expect(find.text('Menor volumen medido'), findsOneWidget);
    expect(find.text('−10,000 m³'), findsOneWidget);
    expect(find.text('−10000 litros'), findsOneWidget);
    expect(find.text('−S/ 25.00'), findsOneWidget);
    expect(
      find.textContaining('Uno de los meses sigue en curso'),
      findsOneWidget,
    );
    expect(
      find.textContaining('Las lecturas abarcan tiempos distintos'),
      findsOneWidget,
    );
    final selector = tester.widget<DropdownButton<String>>(
      find.byType(DropdownButton<String>).first,
    );
    expect(selector.items, hasLength(13));
    expect(selector.items!.first.value, '2026-11');
    expect(selector.items!.last.value, '2025-11');
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
    'cambiar un mes requiere comparar y conserva la selección al navegar',
    (tester) async {
      final api = ServicioPrueba();
      await abrirComparacion(tester, api);
      await tester.ensureVisible(find.byType(DropdownButton<String>).first);
      await tester.tap(find.byType(DropdownButton<String>).first);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Septiembre 2026').last);
      await tester.pumpAndSettle();
      expect(api.consultasComparacion, 1);
      expect(find.text('−10,000 m³'), findsNothing);
      expect(
        find.text('Pulsa Comparar meses para consultar la selección.'),
        findsOneWidget,
      );
      await tester.ensureVisible(find.text('Consumo actual'));
      await tester.tap(find.text('Consumo actual'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Comparación'));
      await tester.pumpAndSettle();
      expect(api.consultasComparacion, 1);
      await tester.ensureVisible(find.text('Comparar meses'));
      await tester.tap(find.text('Comparar meses'));
      await tester.pumpAndSettle();
      expect(api.rangosComparacion.last, (
        base: '2026-09',
        comparado: '2026-11',
      ));
      expect(find.text('−6,800 m³'), findsOneWidget);
      await tester.ensureVisible(find.text('Actual y anterior'));
      await tester.tap(find.text('Actual y anterior'));
      await tester.pumpAndSettle();
      expect(api.rangosComparacion.last, (base: null, comparado: null));
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  for (final caso in [
    (
      base: '2026-09',
      comparado: '2026-10',
      volumen: '+3,200 m³',
      costo: '+S/ 8.00',
      titulo: 'Mayor volumen medido',
    ),
    (
      base: '2026-10',
      comparado: '2026-10',
      volumen: '0,000 m³',
      costo: 'S/ 0.00',
      titulo: 'Mismo volumen medido',
    ),
  ]) {
    testWidgets('presenta ${caso.titulo.toLowerCase()} sin alterar decimales', (
      tester,
    ) async {
      final api = ServicioPrueba();
      await abrirComparacion(tester, api);
      api.alComparar = (_, _) async =>
          comparacionPrueba(base: caso.base, comparado: caso.comparado);
      // La selección se realiza en ambos desplegables, como en el teléfono.
      for (final seleccion in [(0, caso.base), (1, caso.comparado)]) {
        final selector = find.byType(DropdownButton<String>).at(seleccion.$1);
        await tester.ensureVisible(selector);
        final widget = tester.widget<DropdownButton<String>>(selector);
        widget.onChanged!(seleccion.$2);
        await tester.pumpAndSettle();
      }
      await tester.ensureVisible(find.text('Comparar meses'));
      await tester.tap(find.text('Comparar meses'));
      await tester.pumpAndSettle();
      expect(find.text(caso.titulo), findsOneWidget);
      expect(find.text(caso.volumen), findsOneWidget);
      expect(find.text(caso.costo), findsOneWidget);
      expect(find.text('Comparación completa'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }

  testWidgets(
    'conceptos faltantes conservan el volumen y el detalle de subtotales',
    (tester) async {
      final api = ServicioPrueba()
        ..alComparar = (_, _) async =>
            comparacionPrueba(conceptosIncompletos: true);
      await abrirComparacion(tester, api);
      expect(find.text('Comparación de costo no disponible'), findsOneWidget);
      expect(find.text('−10,000 m³'), findsOneWidget);
      expect(find.text('−S/ 25.00'), findsNothing);
      await tester.ensureVisible(
        find.byKey(const ValueKey('comparacion-base')),
      );
      await tester.tap(find.text('Octubre 2026').last);
      await tester.pumpAndSettle();
      expect(find.text('Subtotal conocido'), findsWidgets);
      expect(find.text('Tarifa aplicada'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets('sin lecturas explica la falta de comparación sin mostrar cero', (
    tester,
  ) async {
    final api = ServicioPrueba()
      ..alComparar = (_, _) async => ComparacionConsumo.desdeJson(
        respuestaComparacion(sinDatosBase: true),
        '0013100390',
      );
    await abrirComparacion(tester, api);
    expect(find.text('Comparación no disponible'), findsOneWidget);
    expect(find.text('Diferencia de consumo no disponible'), findsOneWidget);
    expect(find.text('0,000 m³'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('avisa cuando se usaron tarifas diferentes', (tester) async {
    final api = ServicioPrueba()
      ..alComparar = (_, _) async {
        final json = respuestaComparacion()..['mismaVersionTarifaria'] = false;
        json['comparado']['estimacion']['tarifa']['versionTarifa'] = 2;
        return ComparacionConsumo.desdeJson(json, '0013100390');
      };
    await abrirComparacion(tester, api);
    expect(
      find.textContaining('Se aplicaron tarifas distintas'),
      findsOneWidget,
    );
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('permite reintentar y señala si conserva la última consulta', (
    tester,
  ) async {
    final api = ServicioPrueba()
      ..alComparar = (_, _) async => throw const FalloApi('Sin conexión');
    await abrirComparacion(tester, api);
    expect(find.text('Sin conexión'), findsOneWidget);
    api.alComparar = null;
    await tester.ensureVisible(find.text('Reintentar comparación'));
    await tester.tap(find.text('Reintentar comparación'));
    await tester.pumpAndSettle();
    expect(find.text('−10,000 m³'), findsOneWidget);
    api.alComparar = (_, _) async => throw const FalloApi('Sin conexión');
    await tester.ensureVisible(find.text('Comparar meses'));
    await tester.tap(find.text('Comparar meses'));
    await tester.pumpAndSettle();
    expect(
      find.text(
        'Se muestra la última comparación consultada para estos meses.',
      ),
      findsOneWidget,
    );
    expect(find.text('−10,000 m³'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
    'salir durante la consulta descarta la respuesta y desactiva filtros',
    (tester) async {
      final api = ServicioPrueba();
      await abrirComparacion(tester, api);
      final pendiente = Completer<ComparacionConsumo>();
      api.alComparar = (_, _) => pendiente.future;
      await tester.ensureVisible(find.text('Comparar meses'));
      await tester.tap(find.text('Comparar meses'));
      await tester.pump();
      expect(
        tester
            .widget<DropdownButton<String>>(
              find.byType(DropdownButton<String>).first,
            )
            .onChanged,
        isNull,
      );
      await tester.tap(find.byTooltip('Cerrar sesión'));
      await tester.pumpAndSettle();
      pendiente.complete(comparacionPrueba());
      await tester.pumpAndSettle();
      expect(find.text('Consulta tu suministro'), findsOneWidget);
      expect(find.text('−10,000 m³'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  for (final tamano in [const Size(320, 640), const Size(1200, 800)]) {
    testWidgets(
      'comparación a ${tamano.width.toInt()} px con detalle sin desbordamientos',
      (tester) async {
        tester.view.physicalSize = tamano;
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        await abrirComparacion(tester, ServicioPrueba());
        await tester.ensureVisible(
          find.byKey(const ValueKey('comparacion-base')),
        );
        await tester.tap(find.text('Octubre 2026').last);
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        await tester.ensureVisible(find.text('Detalle del mes comparado'));
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox.shrink());
      },
    );
  }
}
