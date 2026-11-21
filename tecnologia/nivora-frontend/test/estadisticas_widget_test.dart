import 'dart:async';
import 'dart:ui' as ui;

import 'package:flutter/rendering.dart' show RenderParagraph;

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:nivora_frontend/src/api_nivora.dart';
import 'package:nivora_frontend/src/aplicacion.dart';
import 'package:nivora_frontend/src/grafico_mensual.dart';

import 'datos_estadisticas_prueba.dart';
import 'datos_prueba.dart';

Future<void> abrirEstadisticas(WidgetTester tester, ServicioPrueba api) async {
  await tester.pumpWidget(NivoraApp(api: api));
  await tester.enterText(find.byType(TextFormField).at(0), '0013100390');
  await tester.enterText(find.byType(TextFormField).at(1), '01234567');
  await tester.ensureVisible(find.text('Ingresar'));
  await tester.tap(find.text('Ingresar'));
  await tester.pumpAndSettle();
  expect(api.consultasEstadisticas, 0);
  await tester.ensureVisible(find.text('Estadísticas'));
  await tester.tap(find.text('Estadísticas'));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets(
    'tres meses con una sola lectura completa muestran una columna y dos S/D',
    (tester) async {
      final api = ServicioPrueba()
        ..alEstadisticas = (meses) async => estadisticasPrueba(
          meses: meses,
          estados: List.generate(
            meses,
            (i) => i == meses - 1 ? 'COMPLETO' : 'SIN_DATOS',
          ),
          valores: List.generate(meses, (i) => i == meses - 1 ? 5637 : 0),
        );
      await abrirEstadisticas(tester, api);
      tester
          .widget<DropdownButton<int>>(find.byType(DropdownButton<int>))
          .onChanged!(3);
      await tester.pumpAndSettle();
      expect(find.text('S/D'), findsNWidgets(2));
      expect(find.byKey(const ValueKey('barra-2026-08')), findsNothing);
      expect(find.byKey(const ValueKey('cero-2026-08')), findsNothing);
      final barra = tester.widget<FractionallySizedBox>(
        find.byKey(const ValueKey('barra-2026-10')),
      );
      expect(barra.heightFactor, closeTo(.704625, .000001));
      expect(barra.widthFactor, isNull);
      expect(
        tester.widget<Text>(find.byKey(const ValueKey('eje-4'))).data,
        '8',
      );
      expect(find.text('5,637 m³'), findsNWidgets(4));
      expect(find.text('5,637000 m³'), findsNothing);
      expect(find.text('5637 litros'), findsNWidgets(4));
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'doce meses empiezan por los recientes y permiten desplazarse a los primeros',
    (tester) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await abrirEstadisticas(tester, ServicioPrueba());
      final viewport = find.byKey(const ValueKey('meses-grafico'));
      await tester.ensureVisible(viewport);
      await tester.pumpAndSettle();
      expect(
        tester.getRect(find.byKey(const ValueKey('columna-2026-10'))).right,
        closeTo(tester.getRect(viewport).right, 1),
      );
      await tester.drag(viewport, const Offset(800, 0));
      await tester.pumpAndSettle();
      final primero = find.byKey(const ValueKey('columna-2025-11'));
      expect(
        tester.getRect(primero).left,
        closeTo(tester.getRect(viewport).left, 1),
      );
      await tester.tap(primero);
      await tester.pumpAndSettle();
      expect(find.text('Noviembre 2025'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'cada columna expone mes, estado, cantidad y acción al lector de pantalla',
    (tester) async {
      final semantica = tester.ensureSemantics();
      try {
        await abrirEstadisticas(tester, ServicioPrueba());
        final columna = find.byKey(const ValueKey('columna-2026-07'));
        await tester.ensureVisible(columna);
        await tester.pumpAndSettle();
        final nodo = tester.getSemantics(
          find.bySemanticsLabel(RegExp(r'^Julio 2026\. Mes completo\.')),
        );
        expect(
          nodo.getSemanticsData().hasAction(ui.SemanticsAction.tap),
          isTrue,
        );
        expect(nodo.getSemanticsData().label, contains('9000 litros'));
        await tester.pumpWidget(const SizedBox.shrink());
      } finally {
        semantica.dispose();
      }
    },
  );

  testWidgets(
    'escala y columnas mantienen cantidades grandes con letra ampliada',
    (tester) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      tester.platformDispatcher.textScaleFactorTestValue = 1.4;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
      final original = estadisticasPrueba(meses: 1).serie.single;
      final mes = MesEstadistico(
        periodo: original.periodo,
        inicio: original.inicio,
        fin: original.fin,
        consumo: ConsumoEstadistico(
          estado: 'COMPLETO',
          motivo: null,
          tipoMedicion: 'ACUMULADA',
          litros: '9007199254740993',
          metrosCubicos: '9007199254740.993',
          desde: original.inicio,
          hasta: original.fin,
        ),
      );
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(child: GraficoMensual(periodos: [mes])),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('9007199254740,993'), findsOneWidget);
      final barra = tester.widget<FractionallySizedBox>(
        find.byKey(const ValueKey('barra-2026-10')),
      );
      expect(barra.heightFactor, greaterThan(0));
      expect(barra.heightFactor, lessThanOrEqualTo(1));
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'presenta resumen, cobertura y escala común que incluye los parciales',
    (tester) async {
      final api = ServicioPrueba();
      await abrirEstadisticas(tester, api);
      expect(find.text('Completos: 3'), findsOneWidget);
      expect(find.text('Parciales: 3'), findsOneWidget);
      expect(find.text('Sin datos: 3'), findsOneWidget);
      expect(find.text('En revisión: 3'), findsOneWidget);
      expect(find.text('Total de meses completos'), findsOneWidget);
      expect(find.text('4,666667 m³'), findsOneWidget);
      expect(
        tester.widget<Text>(find.byKey(const ValueKey('eje-4'))).data,
        '10',
      );
      expect(
        tester.getTopLeft(find.text('Consumo por mes')).dy,
        lessThan(tester.getTopLeft(find.text('Resumen de meses completos')).dy),
      );
      final completa = tester.widget<FractionallySizedBox>(
        find.byKey(const ValueKey('barra-2026-07')),
      );
      final parcial = tester.widget<FractionallySizedBox>(
        find.byKey(const ValueKey('barra-2026-08')),
      );
      expect(completa.heightFactor, .9);
      expect(completa.widthFactor, isNull);
      expect(parcial.heightFactor, 1);
      expect(find.byKey(const ValueKey('cero-2025-11')), findsOneWidget);
      expect(find.byKey(const ValueKey('barra-2026-01')), findsNothing);
      expect(find.byKey(const ValueKey('grafico-2026-11')), findsNothing);
      await tester.ensureVisible(find.text('Consumo actual'));
      await tester.tap(find.text('Consumo actual'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Estadísticas'));
      await tester.pumpAndSettle();
      expect(api.consultasEstadisticas, 1);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets('cambia entre 3 y 6 meses y actualiza ambos gráfico y resumen', (
    tester,
  ) async {
    final api = ServicioPrueba();
    await abrirEstadisticas(tester, api);
    for (final meses in [3, 6]) {
      await tester.ensureVisible(find.byType(DropdownButton<int>));
      await tester.tap(find.byType(DropdownButton<int>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('$meses meses').last);
      await tester.pumpAndSettle();
      expect(api.rangosEstadisticas.last, meses);
      expect(
        find.byWidgetPredicate(
          (w) =>
              w.key is ValueKey<String> &&
              (w.key as ValueKey<String>).value.startsWith('columna-'),
        ),
        findsNWidgets(meses),
      );
    }
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
    'sin meses completos conserva volumen parcial sin inventar resumen',
    (tester) async {
      final api = ServicioPrueba()
        ..alEstadisticas = (meses) async => estadisticasPrueba(
          meses: meses,
          estados: List.filled(meses, 'PARCIAL'),
          valores: List.filled(meses, 1000),
        );
      await abrirEstadisticas(tester, api);
      expect(
        find.text(
          'Aún no hay meses con cobertura completa para calcular el resumen.',
        ),
        findsOneWidget,
      );
      expect(find.text('Total de meses completos'), findsNothing);
      expect(find.textContaining('1,000 m³ · 1000 litros'), findsOneWidget);
      expect(find.textContaining('Consumo parcial'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets('todos sin datos se distinguen de cero y no tienen barras', (
    tester,
  ) async {
    final api = ServicioPrueba()
      ..alEstadisticas = (meses) async => estadisticasPrueba(
        meses: meses,
        estados: List.filled(meses, 'SIN_DATOS'),
      );
    await abrirEstadisticas(tester, api);
    expect(
      find.text('Sin consumos calculables para trazar columnas.'),
      findsOneWidget,
    );
    expect(find.textContaining('Consumo no disponible'), findsOneWidget);
    expect(find.text('S/D'), findsNWidgets(12));
    expect(find.textContaining('0 litros'), findsNothing);
    expect(find.byType(FractionallySizedBox), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('todos completos en cero conservan resumen y marcas de cero', (
    tester,
  ) async {
    final api = ServicioPrueba()
      ..alEstadisticas = (meses) async => estadisticasPrueba(
        meses: meses,
        estados: List.filled(meses, 'COMPLETO'),
        valores: List.filled(meses, 0),
      );
    await abrirEstadisticas(tester, api);
    expect(find.text('Los consumos calculables son cero.'), findsOneWidget);
    expect(find.text('Total de meses completos'), findsOneWidget);
    expect(find.byKey(const ValueKey('cero-2026-10')), findsOneWidget);
    expect(
      tester
          .renderObject<RenderParagraph>(find.byKey(const ValueKey('eje-3')))
          .didExceedMaxLines,
      isFalse,
    );
    expect(find.textContaining('0,000 m³ · 0 litros'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('despliega las fechas reales y los huecos entre intervalos', (
    tester,
  ) async {
    final api = ServicioPrueba()
      ..alEstadisticas = (meses) async {
        final json = respuestaEstadisticas(meses: meses, diaInicio: 5);
        json['serieMensual'][1]['consumo']['motivo'] =
            'HUECOS_ENTRE_INTERVALOS';
        json['serieMensual'][1]['consumo']['tipoMedicion'] = 'INTERVALO';
        return EstadisticasConsumo.desdeJson(json, '0013100390', meses: meses);
      };
    await abrirEstadisticas(tester, api);
    await tester.ensureVisible(find.byKey(const ValueKey('columna-2025-12')));
    await tester.tap(find.byKey(const ValueKey('columna-2025-12')));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Diciembre 2025'));
    await tester.tap(find.text('Diciembre 2025'));
    await tester.pumpAndSettle();
    expect(
      find.text(
        'Periodo de análisis: desde 05/12/2025 hasta antes del 05/01/2026.',
      ),
      findsOneWidget,
    );
    expect(find.textContaining('Hay huecos entre intervalos'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('permite reintentar y conserva el último rango identificado', (
    tester,
  ) async {
    final api = ServicioPrueba()
      ..alEstadisticas = (_) async => throw const FalloApi('Sin conexión');
    await abrirEstadisticas(tester, api);
    expect(find.text('Sin conexión'), findsOneWidget);
    api.alEstadisticas = null;
    await tester.ensureVisible(find.text('Reintentar estadísticas'));
    await tester.tap(find.text('Reintentar estadísticas'));
    await tester.pumpAndSettle();
    api.alEstadisticas = (_) async => throw const FalloApi('Sin conexión');
    await tester.ensureVisible(find.text('Actualizar estadísticas'));
    await tester.tap(find.text('Actualizar estadísticas'));
    await tester.pumpAndSettle();
    expect(
      find.text(
        'Se muestran las últimas estadísticas consultadas para este rango.',
      ),
      findsOneWidget,
    );
    expect(find.text('Total de meses completos'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('401 elimina el gráfico y devuelve al acceso', (tester) async {
    final api = ServicioPrueba();
    await abrirEstadisticas(tester, api);
    api.alEstadisticas = (_) async =>
        throw const FalloApi('Terminó', estado: 401);
    await tester.ensureVisible(find.text('Actualizar estadísticas'));
    await tester.tap(find.text('Actualizar estadísticas'));
    await tester.pumpAndSettle();
    expect(find.text('Consulta tu suministro'), findsOneWidget);
    expect(find.text('Consumo por mes'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
    'permite cerrar sesión durante una petición y descarta la respuesta',
    (tester) async {
      final api = ServicioPrueba();
      await abrirEstadisticas(tester, api);
      final pendiente = Completer<EstadisticasConsumo>();
      api.alEstadisticas = (_) => pendiente.future;
      await tester.ensureVisible(find.text('Actualizar estadísticas'));
      await tester.tap(find.text('Actualizar estadísticas'));
      await tester.pump();
      expect(
        tester
            .widget<DropdownButton<int>>(find.byType(DropdownButton<int>))
            .onChanged,
        isNull,
      );
      await tester.tap(find.byTooltip('Cerrar sesión'));
      await tester.pumpAndSettle();
      pendiente.complete(estadisticasPrueba());
      await tester.pumpAndSettle();
      expect(find.text('Consulta tu suministro'), findsOneWidget);
      expect(find.text('Consumo por mes'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  for (final tamano in [const Size(320, 640), const Size(1200, 800)]) {
    testWidgets(
      'estadísticas a ${tamano.width.toInt()} px con fechas sin desbordamientos',
      (tester) async {
        tester.view.physicalSize = tamano;
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        await abrirEstadisticas(tester, ServicioPrueba());
        await tester.ensureVisible(
          find.byKey(const ValueKey('columna-2025-12')),
        );
        await tester.tap(find.byKey(const ValueKey('columna-2025-12')));
        await tester.pumpAndSettle();
        await tester.ensureVisible(find.text('Diciembre 2025'));
        await tester.tap(find.text('Diciembre 2025'));
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox.shrink());
      },
    );
  }

  testWidgets(
    'letra ampliada en 320 px conserva las cantidades exactas del gráfico',
    (tester) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      tester.platformDispatcher.textScaleFactorTestValue = 1.4;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
      await abrirEstadisticas(tester, ServicioPrueba());
      await tester.ensureVisible(find.byKey(const ValueKey('columna-2025-12')));
      await tester.tap(find.byKey(const ValueKey('columna-2025-12')));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Diciembre 2025'));
      await tester.tap(find.text('Diciembre 2025'));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.textContaining('2,000 m³ · 2000 litros'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );
}
