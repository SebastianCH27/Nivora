import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:nivora_frontend/src/api_nivora.dart';
import 'package:nivora_frontend/src/aplicacion.dart';

import 'datos_prueba.dart';
import 'datos_recibos_prueba.dart';

Future<void> abrirRecibos(WidgetTester tester, ServicioPrueba api) async {
  await tester.pumpWidget(NivoraApp(api: api));
  await tester.enterText(find.byType(TextFormField).at(0), '0013100390');
  await tester.enterText(find.byType(TextFormField).at(1), '01234567');
  await tester.ensureVisible(find.text('Ingresar'));
  await tester.tap(find.text('Ingresar'));
  await tester.pumpAndSettle();
  expect(api.consultasRecibos, 0);
  await tester.ensureVisible(find.text('Recibos'));
  await tester.tap(find.text('Recibos'));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets(
    'muestra recibos emitidos con total, consumo, vencimiento y cargos',
    (tester) async {
      final api = ServicioPrueba();
      await abrirRecibos(tester, api);
      expect(find.text('Recibo EA-202610-001'), findsOneWidget);
      expect(find.text('Importe del recibo'), findsNWidgets(12));
      expect(find.text('S/ 13.46'), findsNWidgets(12));
      expect(
        find.text('Consumo facturado: 5,637 m³ · 5637 litros'),
        findsNWidgets(12),
      );
      expect(find.text('Vencimiento: 08/11/2026'), findsOneWidget);
      expect(find.text('Total estimado'), findsNothing);
      expect(find.text('Pagado'), findsNothing);
      await tester.ensureVisible(find.text('Octubre 2026'));
      await tester.tap(find.text('Octubre 2026'));
      await tester.pumpAndSettle();
      expect(find.text('Cargos del recibo'), findsOneWidget);
      expect(find.text('Cargo fijo'), findsOneWidget);
      expect(find.text('Importe por consumo'), findsOneWidget);
      expect(find.text('Importe total emitido'), findsOneWidget);
      expect(find.text('Disponible desde: 01/11/2026'), findsOneWidget);
      expect(
        find.text('Recibido en Nivora: 01/11/2026 01:00 (Lima)'),
        findsOneWidget,
      );
      expect(find.text('Periodo de análisis de Nivora'), findsNothing);
      await tester.ensureVisible(find.text('Consumo actual'));
      await tester.tap(find.text('Consumo actual'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Recibos'));
      await tester.pumpAndSettle();
      expect(api.consultasRecibos, 1);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'pagina con el corte exclusivo y regresa a los recibos más recientes',
    (tester) async {
      final api = ServicioPrueba();
      await abrirRecibos(tester, api);
      await tester.ensureVisible(find.text('Ver recibos anteriores'));
      await tester.tap(find.text('Ver recibos anteriores'));
      await tester.pumpAndSettle();
      expect(api.rangosRecibos.last, (limite: 12, antesDe: '2025-11'));
      expect(find.text('Octubre 2026'), findsNothing);
      expect(find.text('Octubre 2025'), findsOneWidget);
      expect(find.text('3 recibos en esta página'), findsOneWidget);
      expect(find.text('Ver recibos anteriores'), findsNothing);
      await tester.ensureVisible(find.text('Volver a los más recientes'));
      await tester.tap(find.text('Volver a los más recientes'));
      await tester.pumpAndSettle();
      expect(api.rangosRecibos.last, (limite: 12, antesDe: null));
      expect(find.text('Octubre 2026'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets('la lista vacía muestra una explicación y permite actualizar', (
    tester,
  ) async {
    final api = ServicioPrueba()
      ..alRecibos = (_, _) async => paginaRecibosPrueba(disponibles: 0);
    await abrirRecibos(tester, api);
    expect(
      find.text('Todavía no hay recibos guardados para tu suministro.'),
      findsOneWidget,
    );
    expect(find.text('S/ 0.00'), findsNothing);
    expect(find.text('Ver recibos anteriores'), findsNothing);
    await tester.ensureVisible(find.text('Actualizar recibos'));
    await tester.tap(find.text('Actualizar recibos'));
    await tester.pumpAndSettle();
    expect(api.consultasRecibos, 2);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('un recibo de importe y consumo cero conserva su significado', (
    tester,
  ) async {
    final api = ServicioPrueba()
      ..alRecibos = (_, _) async {
        final json = respuestaRecibos(disponibles: 1);
        final recibo = json['recibos'][0];
        recibo['cargoFijo'] = recibo['importeConsumo'] =
            recibo['importeTotal'] = '0.00';
        recibo['consumoLitros'] = '0.000';
        return PaginaRecibos.desdeJson(json, '0013100390');
      };
    await abrirRecibos(tester, api);
    expect(find.text('S/ 0.00'), findsOneWidget);
    expect(find.text('Consumo facturado: 0,000 m³ · 0 litros'), findsOneWidget);
    expect(find.textContaining('Todavía no hay recibos'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('explica los periodos de facturación y análisis distintos', (
    tester,
  ) async {
    final api = ServicioPrueba()
      ..alRecibos = (_, _) async {
        final json = respuestaRecibos(disponibles: 1);
        json['recibos'] = [reciboPrueba('2026-10', calendarioDistinto: true)];
        return PaginaRecibos.desdeJson(json, '0013100390');
      };
    await abrirRecibos(tester, api);
    await tester.ensureVisible(find.text('Octubre 2026'));
    await tester.tap(find.text('Octubre 2026'));
    await tester.pumpAndSettle();
    expect(find.textContaining('El periodo facturado difiere'), findsOneWidget);
    expect(
      find.text('Desde 01/10/2026 hasta antes del 01/11/2026.'),
      findsOneWidget,
    );
    expect(
      find.text('Desde 05/10/2026 hasta antes del 05/11/2026.'),
      findsOneWidget,
    );
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('permite reintentar y señala si conserva la última página', (
    tester,
  ) async {
    final api = ServicioPrueba()
      ..alRecibos = (_, _) async => throw const FalloApi('Sin conexión');
    await abrirRecibos(tester, api);
    expect(find.text('Sin conexión'), findsOneWidget);
    api.alRecibos = null;
    await tester.ensureVisible(find.text('Reintentar recibos'));
    await tester.tap(find.text('Reintentar recibos'));
    await tester.pumpAndSettle();
    expect(find.text('Octubre 2026'), findsOneWidget);
    api.alRecibos = (_, _) async => throw const FalloApi('Sin conexión');
    await tester.ensureVisible(find.text('Actualizar recibos'));
    await tester.tap(find.text('Actualizar recibos'));
    await tester.pumpAndSettle();
    expect(
      find.text(
        'Se muestra la última página de recibos consultada para este rango.',
      ),
      findsOneWidget,
    );
    expect(find.text('Octubre 2026'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('error al consultar una página anterior no mezcla sus recibos', (
    tester,
  ) async {
    final api = ServicioPrueba();
    await abrirRecibos(tester, api);
    api.alRecibos = (_, _) async => throw const FalloApi('Sin conexión');
    await tester.ensureVisible(find.text('Ver recibos anteriores'));
    await tester.tap(find.text('Ver recibos anteriores'));
    await tester.pumpAndSettle();
    expect(find.text('Octubre 2026'), findsNothing);
    expect(find.text('Sin conexión'), findsOneWidget);
    api.alRecibos = null;
    await tester.ensureVisible(find.text('Reintentar recibos'));
    await tester.tap(find.text('Reintentar recibos'));
    await tester.pumpAndSettle();
    expect(find.text('Octubre 2025'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('HTTP 401 regresa al acceso y elimina los recibos visibles', (
    tester,
  ) async {
    final api = ServicioPrueba();
    await abrirRecibos(tester, api);
    api.alRecibos = (_, _) async =>
        throw const FalloApi('Terminó', estado: 401);
    await tester.ensureVisible(find.text('Actualizar recibos'));
    await tester.tap(find.text('Actualizar recibos'));
    await tester.pumpAndSettle();
    expect(find.text('Consulta tu suministro'), findsOneWidget);
    expect(find.text('Recibo EA-202610-001'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('una consulta pendiente permite salir y descarta la respuesta', (
    tester,
  ) async {
    final api = ServicioPrueba();
    await abrirRecibos(tester, api);
    final pendiente = Completer<PaginaRecibos>();
    api.alRecibos = (_, _) => pendiente.future;
    await tester.ensureVisible(find.text('Actualizar recibos'));
    await tester.tap(find.text('Actualizar recibos'));
    await tester.pump();
    expect(find.text('Consultando tus recibos…'), findsOneWidget);
    expect(
      tester
          .widget<OutlinedButton>(
            find.widgetWithText(OutlinedButton, 'Actualizar recibos'),
          )
          .onPressed,
      isNull,
    );
    await tester.tap(find.byTooltip('Cerrar sesión'));
    await tester.pumpAndSettle();
    pendiente.complete(paginaRecibosPrueba());
    await tester.pumpAndSettle();
    expect(find.text('Consulta tu suministro'), findsOneWidget);
    expect(find.text('Octubre 2026'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  for (final tamano in [const Size(320, 640), const Size(1200, 800)]) {
    testWidgets(
      'recibos a ${tamano.width.toInt()} px con detalle sin desbordamientos',
      (tester) async {
        tester.view.physicalSize = tamano;
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        await abrirRecibos(tester, ServicioPrueba());
        await tester.ensureVisible(find.text('Octubre 2026'));
        await tester.tap(find.text('Octubre 2026'));
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        await tester.ensureVisible(find.text('Cargos del recibo'));
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox.shrink());
      },
    );
  }

  testWidgets(
    'letra ampliada y referencias largas caben en un celular estrecho',
    (tester) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      tester.platformDispatcher.textScaleFactorTestValue = 1.4;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
      final api = ServicioPrueba()
        ..alRecibos = (_, _) async {
          final json = respuestaRecibos(disponibles: 1);
          final recibo = json['recibos'][0];
          recibo['numeroRecibo'] = 'R' * 40;
          recibo['codigoTarifa'] = 'T' * 30;
          recibo['cargoFijo'] = '0.00';
          recibo['importeConsumo'] = recibo['importeTotal'] = '9999999999.99';
          return PaginaRecibos.desdeJson(json, '0013100390');
        };
      await abrirRecibos(tester, api);
      await tester.ensureVisible(find.text('Octubre 2026'));
      await tester.tap(find.text('Octubre 2026'));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.ensureVisible(find.text('Cargos del recibo'));
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );
}
