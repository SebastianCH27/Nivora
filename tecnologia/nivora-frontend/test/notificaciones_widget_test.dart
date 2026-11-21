import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:nivora_frontend/src/api_nivora.dart';
import 'package:nivora_frontend/src/aplicacion.dart';

import 'datos_prueba.dart';
import 'datos_notificaciones_prueba.dart';

const reciboId = 'RECIBO_DISPONIBLE-2026-10';
const alertaId = 'ALERTA_CONSUMO-2026-10';

Future<void> abrirBandeja(WidgetTester tester, ServicioPrueba api) async {
  await tester.pumpWidget(NivoraApp(api: api));
  await tester.enterText(find.byType(TextFormField).at(0), '0013100390');
  await tester.enterText(find.byType(TextFormField).at(1), '01234567');
  await tester.ensureVisible(find.text('Ingresar'));
  await tester.tap(find.text('Ingresar'));
  await tester.pumpAndSettle();
  expect(api.consultasNotificaciones, 0);
  await tester.ensureVisible(find.text('Notificaciones'));
  await tester.tap(find.text('Notificaciones'));
  await tester.pumpAndSettle();
}

Future<void> abrirAviso(WidgetTester tester, String id) async {
  final tile = find.descendant(
    of: find.byKey(ValueKey('aviso-$id')),
    matching: find.byType(ExpansionTile),
  );
  await tester.ensureVisible(tile);
  await tester.tap(
    find.descendant(of: tile, matching: find.byType(Text)).first,
  );
  await tester.pumpAndSettle();
}

Future<void> filtrarPendientes(WidgetTester tester) async {
  await tester.ensureVisible(find.byType(DropdownButton<bool>));
  await tester.tap(find.byType(DropdownButton<bool>));
  await tester.pumpAndSettle();
  await tester.tap(find.text('Solo pendientes').last);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets(
    'carga a demanda y muestra detalle de recibo sin inventar importes',
    (tester) async {
      final api = ServicioPrueba();
      await abrirBandeja(tester, api);
      expect(find.text('Tus notificaciones'), findsOneWidget);
      expect(find.text('Avisos mostrados: 20'), findsOneWidget);
      expect(find.text('Pendiente'), findsNWidgets(19));
      expect(find.text('Leída'), findsOneWidget);
      await abrirAviso(tester, reciboId);
      expect(find.text('Recibo EA-202610-001'), findsOneWidget);
      expect(find.text('Disponible desde: 01/11/2026'), findsOneWidget);
      expect(find.text('Vencimiento: 08/11/2026'), findsOneWidget);
      expect(
        find.textContaining('no cambia el estado de pago'),
        findsOneWidget,
      );
      expect(api.marcasNotificaciones, isEmpty);
      expect(find.textContaining('S/'), findsNothing);
      await tester.ensureVisible(find.text('Consumo actual'));
      await tester.tap(find.text('Consumo actual'));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Notificaciones'));
      await tester.tap(find.text('Notificaciones'));
      await tester.pumpAndSettle();
      expect(api.consultasNotificaciones, 1);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'alerta conserva los valores al detectarse y explica que no son lecturas actuales',
    (tester) async {
      await abrirBandeja(tester, ServicioPrueba());
      await abrirAviso(tester, alertaId);
      expect(find.text('Consumo al detectarse la alerta'), findsOneWidget);
      expect(find.text('Octubre 2026: 6,200 m³ · 6200 litros'), findsOneWidget);
      expect(
        find.text('Septiembre 2026: 5,637 m³ · 5637 litros'),
        findsOneWidget,
      );
      expect(find.textContaining('momento de la alerta'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'la lectura exige pulsar la acción y muestra su fecha al actualizar',
    (tester) async {
      final api = ServicioPrueba();
      await abrirBandeja(tester, api);
      await abrirAviso(tester, reciboId);
      final boton = find.byKey(const ValueKey('marcar-$reciboId'));
      await tester.ensureVisible(boton);
      await tester.tap(boton);
      await tester.pumpAndSettle();
      expect(api.marcasNotificaciones, [reciboId]);
      expect(find.text('Aviso marcado como leído.'), findsOneWidget);
      expect(boton, findsNothing);
      expect(find.text('Leída: 01/11/2026 03:10 (Lima)'), findsNothing);
      await tester.ensureVisible(find.text('Actualizar notificaciones'));
      await tester.tap(find.text('Actualizar notificaciones'));
      await tester.pumpAndSettle();
      expect(find.text('Leída: 01/11/2026 03:10 (Lima)'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'pendientes retira el aviso confirmado y mantiene el filtro al refrescar',
    (tester) async {
      final api = ServicioPrueba();
      await abrirBandeja(tester, api);
      await filtrarPendientes(tester);
      expect(find.text('Leída'), findsNothing);
      await abrirAviso(tester, reciboId);
      final boton = find.byKey(const ValueKey('marcar-$reciboId'));
      await tester.ensureVisible(boton);
      await tester.tap(boton);
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('aviso-$reciboId')), findsNothing);
      expect(find.text('Avisos mostrados: 19'), findsOneWidget);
      await tester.ensureVisible(find.text('Actualizar notificaciones'));
      await tester.tap(find.text('Actualizar notificaciones'));
      await tester.pumpAndSettle();
      expect(api.rangosNotificaciones.last.soloPendientes, isTrue);
      expect(find.byKey(const ValueKey('aviso-$reciboId')), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets('cargar más añade resultados y conserva los existentes', (
    tester,
  ) async {
    final api = ServicioPrueba();
    await abrirBandeja(tester, api);
    await tester.ensureVisible(find.text('Cargar más avisos'));
    await tester.tap(find.text('Cargar más avisos'));
    await tester.pumpAndSettle();
    expect(find.text('Avisos mostrados: 40'), findsOneWidget);
    expect(find.byKey(const ValueKey('aviso-$reciboId')), findsOneWidget);
    await tester.ensureVisible(find.text('Cargar más avisos'));
    await tester.tap(find.text('Cargar más avisos'));
    await tester.pumpAndSettle();
    expect(find.text('Avisos mostrados: 45'), findsOneWidget);
    expect(find.text('Cargar más avisos'), findsNothing);
    expect(find.text('No hay más avisos para este filtro.'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('vacío muestra el mensaje que corresponde al filtro', (
    tester,
  ) async {
    final api = ServicioPrueba()
      ..alNotificaciones = (_, solo, _) async =>
          paginaNotificacionesPrueba(soloPendientes: solo, disponibles: 0);
    await abrirBandeja(tester, api);
    expect(
      find.text('Todavía no hay notificaciones guardadas para tu suministro.'),
      findsOneWidget,
    );
    await filtrarPendientes(tester);
    expect(
      find.text('No hay avisos pendientes para tu suministro.'),
      findsOneWidget,
    );
    expect(find.text('Cargar más avisos'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
    'permite reintentar tras fallo inicial e identifica datos anteriores',
    (tester) async {
      final api = ServicioPrueba()
        ..alNotificaciones = (_, _, _) async =>
            throw const FalloApi('Sin conexión');
      await abrirBandeja(tester, api);
      expect(find.text('Sin conexión'), findsOneWidget);
      expect(find.text('Avisos mostrados: 20'), findsNothing);
      api.alNotificaciones = null;
      await tester.ensureVisible(find.text('Reintentar notificaciones'));
      await tester.tap(find.text('Reintentar notificaciones'));
      await tester.pumpAndSettle();
      expect(find.text('Avisos mostrados: 20'), findsOneWidget);
      api.alNotificaciones = (_, _, _) async =>
          throw const FalloApi('Sin conexión');
      await tester.ensureVisible(find.text('Actualizar notificaciones'));
      await tester.tap(find.text('Actualizar notificaciones'));
      await tester.pumpAndSettle();
      expect(
        find.text(
          'Se muestran los últimos avisos consultados con este filtro.',
        ),
        findsOneWidget,
      );
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets('marca fallida conserva el botón y el estado pendiente', (
    tester,
  ) async {
    final api = ServicioPrueba()
      ..alMarcarNotificacion = (_) async =>
          throw const FalloApi('Sin conexión');
    await abrirBandeja(tester, api);
    await abrirAviso(tester, reciboId);
    final boton = find.byKey(const ValueKey('marcar-$reciboId'));
    await tester.ensureVisible(boton);
    await tester.tap(boton);
    await tester.pumpAndSettle();
    expect(
      find.textContaining('No pudimos confirmar la lectura'),
      findsOneWidget,
    );
    expect(boton, findsOneWidget);
    expect(find.text('Pendiente'), findsNWidgets(19));
    api.alMarcarNotificacion = null;
    await tester.ensureVisible(boton);
    await tester.tap(boton);
    await tester.pumpAndSettle();
    expect(boton, findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
    'bloquea controles durante PUT y permite cerrar sesión sin esperar',
    (tester) async {
      final api = ServicioPrueba();
      await abrirBandeja(tester, api);
      await abrirAviso(tester, reciboId);
      final pendiente = Completer<void>();
      api.alMarcarNotificacion = (_) => pendiente.future;
      final boton = find.byKey(const ValueKey('marcar-$reciboId'));
      await tester.ensureVisible(boton);
      await tester.tap(boton);
      await tester.pump();
      expect(
        tester
            .widget<DropdownButton<bool>>(find.byType(DropdownButton<bool>))
            .onChanged,
        isNull,
      );
      expect(tester.widget<OutlinedButton>(boton).onPressed, isNull);
      await tester.tap(find.byTooltip('Cerrar sesión'));
      await tester.pumpAndSettle();
      pendiente.complete();
      await tester.pumpAndSettle();
      expect(find.text('Consulta tu suministro'), findsOneWidget);
      expect(find.text('Tus notificaciones'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets('401 al marcar vuelve al acceso y elimina avisos', (
    tester,
  ) async {
    final api = ServicioPrueba()
      ..alMarcarNotificacion = (_) async =>
          throw const FalloApi('Terminó', estado: 401);
    await abrirBandeja(tester, api);
    await abrirAviso(tester, reciboId);
    final boton = find.byKey(const ValueKey('marcar-$reciboId'));
    await tester.ensureVisible(boton);
    await tester.tap(boton);
    await tester.pumpAndSettle();
    expect(find.text('Consulta tu suministro'), findsOneWidget);
    expect(find.text('Tus notificaciones'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  for (final tamano in [const Size(320, 640), const Size(1200, 800)]) {
    testWidgets(
      'bandeja y detalle en ${tamano.width.toInt()} px con letra ampliada',
      (tester) async {
        tester.view.physicalSize = tamano;
        tester.view.devicePixelRatio = 1;
        tester.platformDispatcher.textScaleFactorTestValue = 1.4;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
        final api = ServicioPrueba()
          ..alNotificaciones = (_, solo, _) async =>
              paginaNotificacionesPrueba(soloPendientes: solo, disponibles: 2);
        await abrirBandeja(tester, api);
        await abrirAviso(tester, reciboId);
        await tester.ensureVisible(
          find.byKey(const ValueKey('marcar-$reciboId')),
        );
        expect(tester.takeException(), isNull);
        await abrirAviso(tester, alertaId);
        await tester.ensureVisible(
          find.byKey(const ValueKey('marcar-$alertaId')),
        );
        expect(tester.takeException(), isNull);
        await filtrarPendientes(tester);
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox.shrink());
      },
    );
  }
}
