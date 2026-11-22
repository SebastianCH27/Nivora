import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/foundation.dart';
import 'package:material_ui/material_ui.dart';
import 'package:nivora_frontend/src/api_nivora.dart';
import 'package:nivora_frontend/src/aplicacion.dart';
import 'package:nivora_frontend/src/controlador_consulta.dart';
import 'package:nivora_frontend/src/configuracion_notificaciones.dart';

import 'datos_prueba.dart';
import 'datos_dispositivo_prueba.dart';

void main() {
  late ServicioPrueba api;
  late DispositivoPrueba d;
  late ControladorConsulta c;
  setUp(() async {
    api = ServicioPrueba();
    d = DispositivoPrueba();
    c = ControladorConsulta(api, dispositivo: d);
    await c.ingresar('0013100390', '01234567');
  });
  tearDown(() => c.dispose());
  Future<void> mostrar(WidgetTester tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: ListenableBuilder(
              listenable: c,
              builder: (context, _) =>
                  ConfiguracionNotificaciones(controlador: c),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Configurar notificaciones'));
    await tester.pumpAndSettle();
  }

  testWidgets('permiso explícito y activación separada', (tester) async {
    await mostrar(tester);
    expect(find.text('Permiso del sistema: Pendiente'), findsOneWidget);
    expect(d.solicitudes, 0);
    expect(
      tester.widget<SwitchListTile>(find.byType(SwitchListTile)).onChanged,
      isNull,
    );
    await tester.ensureVisible(find.text('Solicitar permiso'));
    await tester.tap(find.text('Solicitar permiso'));
    await tester.pumpAndSettle();
    expect(find.text('Permiso del sistema: Concedido'), findsOneWidget);
    expect(
      tester.widget<SwitchListTile>(find.byType(SwitchListTile)).value,
      false,
    );
    await tester.ensureVisible(find.byType(SwitchListTile));
    await tester.tap(find.byType(SwitchListTile));
    await tester.pumpAndSettle();
    expect(find.text('Estado guardado: activadas'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });
  testWidgets('denegado conserva desactivadas y abre ajustes', (tester) async {
    d.permiso = PermisoNotificaciones.denegado;
    await mostrar(tester);
    expect(find.text('Permiso del sistema: Denegado'), findsOneWidget);
    expect(
      tester.widget<SwitchListTile>(find.byType(SwitchListTile)).onChanged,
      isNull,
    );
    await tester.ensureVisible(find.text('Abrir ajustes del sistema'));
    await tester.tap(find.text('Abrir ajustes del sistema'));
    await tester.pumpAndSettle();
    expect(d.ajustes, 1);
    await tester.pumpWidget(const SizedBox.shrink());
  });
  testWidgets('no confirma un cambio en curso ni tras fallar', (tester) async {
    d.permiso = PermisoNotificaciones.concedido;
    await mostrar(tester);
    final pendiente = Completer<PreferenciaNotificaciones>();
    api.alGuardarConfiguracion = (s, id, p, h) => pendiente.future;
    await tester.ensureVisible(find.byType(SwitchListTile));
    await tester.tap(find.byType(SwitchListTile));
    await tester.pump();
    await tester.pump();
    expect(
      tester.widget<SwitchListTile>(find.byType(SwitchListTile)).onChanged,
      isNull,
    );
    expect(find.text('Estado guardado: activadas'), findsNothing);
    pendiente.completeError(const FalloApi('Sin conexión'));
    await tester.pumpAndSettle();
    expect(find.textContaining('No pudimos confirmar'), findsOneWidget);
    expect(
      tester.widget<SwitchListTile>(find.byType(SwitchListTile)).onChanged,
      isNull,
    );
    await tester.pumpWidget(const SizedBox.shrink());
  });
  for (final ancho in [320.0, 1200.0]) {
    testWidgets('ancho $ancho con texto ampliado', (tester) async {
      tester.view.physicalSize = Size(ancho, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(
        MaterialApp(
          home: MediaQuery(
            data: MediaQueryData(
              size: Size(ancho, 900),
              textScaler: const TextScaler.linear(1.8),
            ),
            child: Scaffold(
              body: SingleChildScrollView(
                child: ListenableBuilder(
                  listenable: c,
                  builder: (context, _) =>
                      ConfiguracionNotificaciones(controlador: c),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Configurar notificaciones'));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }
  testWidgets('Windows explica que solicitar no abre el diálogo de Android', (
    tester,
  ) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.windows;
    addTearDown(() => debugDefaultTargetPlatformOverride = null);
    await mostrar(tester);
    expect(
      find.textContaining('En Windows no aparece un diálogo'),
      findsOneWidget,
    );
    expect(find.textContaining('aviso de prueba'), findsOneWidget);
    await tester.ensureVisible(find.text('Solicitar permiso'));
    await tester.tap(find.text('Solicitar permiso'));
    await tester.pumpAndSettle();
    expect(
      find.textContaining('Permiso concedido. Puedes activar'),
      findsOneWidget,
    );
    expect(
      tester.widget<SwitchListTile>(find.byType(SwitchListTile)).value,
      false,
    );
    await tester.pumpWidget(const SizedBox.shrink());
  });
  testWidgets('volver sin cambios no mueve el panel ni bloquea los controles', (
    tester,
  ) async {
    d.permiso = PermisoNotificaciones.concedido;
    await c.configurarNotificaciones(habilitar: true);
    await mostrar(tester);
    final escrituras = api.cambiosConfiguracion.length;
    final consultas = api.consultasConfiguracion;
    final pendiente = Completer<PermisoNotificaciones>();
    d.alConsultar = () => pendiente.future;
    final posicion = tester.getTopLeft(find.byType(SwitchListTile));
    c.revisarPermisoAlVolver();
    await tester.pump();
    final interruptor = tester.widget<SwitchListTile>(
      find.byType(SwitchListTile),
    );
    expect(interruptor.value, true);
    expect(interruptor.onChanged, isNotNull);
    expect(find.text('Último estado confirmado: activadas'), findsNothing);
    expect(find.text('Estado guardado: activadas'), findsOneWidget);
    expect(find.byType(LinearProgressIndicator), findsNothing);
    expect(tester.getTopLeft(find.byType(SwitchListTile)), posicion);
    pendiente.complete(PermisoNotificaciones.concedido);
    await tester.pumpAndSettle();
    expect(find.text('Estado guardado: activadas'), findsOneWidget);
    expect(
      tester.widget<SwitchListTile>(find.byType(SwitchListTile)).value,
      true,
    );
    expect(
      tester.widget<SwitchListTile>(find.byType(SwitchListTile)).onChanged,
      isNotNull,
    );
    expect(api.cambiosConfiguracion.length, escrituras);
    expect(api.consultasConfiguracion, consultas);
    expect(tester.getTopLeft(find.byType(SwitchListTile)), posicion);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('cambiar de ventana repetidamente conserva el panel en la app', (
    tester,
  ) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.windows;
    addTearDown(() => debugDefaultTargetPlatformOverride = null);
    tester.view.physicalSize = const Size(1200, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(
      () => tester.binding.handleAppLifecycleStateChanged(
        AppLifecycleState.resumed,
      ),
    );
    d.permiso = PermisoNotificaciones.concedido;
    await tester.pumpWidget(NivoraApp(api: api, dispositivo: d));
    await tester.enterText(find.byType(TextFormField).at(0), '0013100390');
    await tester.enterText(find.byType(TextFormField).at(1), '01234567');
    await tester.ensureVisible(find.text('Ingresar'));
    await tester.tap(find.text('Ingresar'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Notificaciones'));
    await tester.tap(find.text('Notificaciones'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Configurar notificaciones'));
    await tester.tap(find.text('Configurar notificaciones'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.byType(SwitchListTile));
    await tester.tap(find.byType(SwitchListTile));
    await tester.pumpAndSettle();
    final consultas = api.consultasConfiguracion;
    final escrituras = api.cambiosConfiguracion.length;
    final posicion = tester.getTopLeft(find.byType(SwitchListTile));
    for (var i = 0; i < 3; i++) {
      final lectura = Completer<PermisoNotificaciones>();
      d.alConsultar = () => lectura.future;
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pump();
      expect(find.text('Estado guardado: activadas'), findsOneWidget);
      expect(find.text('Comprobando configuración…'), findsNothing);
      expect(tester.getTopLeft(find.byType(SwitchListTile)), posicion);
      expect(
        tester.widget<SwitchListTile>(find.byType(SwitchListTile)).onChanged,
        isNotNull,
      );
      d.alConsultar = null;
      lectura.complete(PermisoNotificaciones.concedido);
      await tester.pumpAndSettle();
    }
    expect(api.consultasConfiguracion, consultas);
    expect(api.cambiosConfiguracion.length, escrituras);
    // Volver después de revocar sí debe actualizar y bloquear la activación.
    d.permiso = PermisoNotificaciones.denegado;
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pumpAndSettle();
    expect(find.text('Estado guardado: desactivadas'), findsOneWidget);
    expect(
      tester.widget<SwitchListTile>(find.byType(SwitchListTile)).value,
      false,
    );
    expect(
      tester.widget<SwitchListTile>(find.byType(SwitchListTile)).onChanged,
      isNull,
    );
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
    'un fallo al refrescar conserva el último estado sin permitir cambios',
    (tester) async {
      d.permiso = PermisoNotificaciones.concedido;
      await c.configurarNotificaciones(habilitar: true);
      await mostrar(tester);
      api.alConfiguracion = (sesion, id) async =>
          throw const FalloApi('Sin conexión');
      await c.configurarNotificaciones();
      await tester.pumpAndSettle();
      expect(find.textContaining('No pudimos confirmar'), findsOneWidget);
      expect(find.text('Último estado confirmado: activadas'), findsOneWidget);
      expect(find.text('Estado guardado: activadas'), findsNothing);
      expect(
        tester.widget<SwitchListTile>(find.byType(SwitchListTile)).value,
        true,
      );
      expect(
        tester.widget<SwitchListTile>(find.byType(SwitchListTile)).onChanged,
        isNull,
      );
      api.alConfiguracion = null;
      await c.configurarNotificaciones();
      await tester.pumpAndSettle();
      expect(find.text('Estado guardado: activadas'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'revocar el permiso apaga el control aunque quede un estado anterior activo',
    (tester) async {
      d.permiso = PermisoNotificaciones.concedido;
      await c.configurarNotificaciones(habilitar: true);
      await mostrar(tester);
      d.permiso = PermisoNotificaciones.denegado;
      final pendiente = Completer<PreferenciaNotificaciones?>();
      api.alConfiguracion = (sesion, id) => pendiente.future;
      final carga = c.configurarNotificaciones();
      await tester.pump();
      expect(find.text('Permiso del sistema: Denegado'), findsOneWidget);
      expect(find.text('Último estado confirmado: activadas'), findsOneWidget);
      expect(
        tester.widget<SwitchListTile>(find.byType(SwitchListTile)).value,
        false,
      );
      expect(
        tester.widget<SwitchListTile>(find.byType(SwitchListTile)).onChanged,
        isNull,
      );
      pendiente.complete(preferenciaPrueba(habilitadas: true));
      await carga;
      await tester.pumpAndSettle();
      expect(find.text('Estado guardado: desactivadas'), findsOneWidget);
      expect(c.preferenciaNotificaciones!.habilitadas, false);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'cerrar sesión durante una comprobación retira el estado anterior',
    (tester) async {
      d.permiso = PermisoNotificaciones.concedido;
      await c.configurarNotificaciones(habilitar: true);
      await mostrar(tester);
      final pendiente = Completer<PreferenciaNotificaciones?>();
      api.alConfiguracion = (sesion, id) => pendiente.future;
      final carga = c.configurarNotificaciones();
      await tester.pump();
      await c.salir();
      pendiente.complete(preferenciaPrueba(habilitadas: true));
      await carga;
      await tester.pumpAndSettle();
      expect(find.textContaining('estado confirmado'), findsNothing);
      expect(find.text('Estado guardado: activadas'), findsNothing);
      expect(
        tester.widget<SwitchListTile>(find.byType(SwitchListTile)).value,
        false,
      );
      expect(
        tester.widget<SwitchListTile>(find.byType(SwitchListTile)).onChanged,
        isNull,
      );
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );
}
