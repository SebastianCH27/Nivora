import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';
import 'package:nivora_frontend/src/controlador_consulta.dart';

import 'datos_prueba.dart';
import 'datos_dispositivo_prueba.dart';

void main() {
  late ServicioPrueba api;
  late DispositivoPrueba dispositivo;
  late ControladorConsulta c;
  setUp(() async {
    api = ServicioPrueba();
    dispositivo = DispositivoPrueba();
    c = ControladorConsulta(api, dispositivo: dispositivo);
    await c.ingresar('0013100390', '01234567');
    api.consultasConfiguracion = 0;
    api.cambiosConfiguracion.clear();
    api.preferenciasDispositivo.clear();
    c.preferenciaNotificaciones = null;
    c.permisoDispositivo = null;
    c.configuracionConfirmada = false;
  });
  tearDown(() => c.dispose());
  test(
    'registra pendiente desactivado sin solicitar permiso al consultar',
    () async {
      await c.configurarNotificaciones();
      expect(c.configuracionConfirmada, true);
      expect(c.preferenciaNotificaciones!.habilitadas, false);
      expect(
        api.cambiosConfiguracion.single.permiso,
        PermisoNotificaciones.pendiente,
      );
      expect(dispositivo.solicitudes, 0);
    },
  );
  test(
    'permiso no activa por sí solo; interruptor sí; refresco no escribe',
    () async {
      await c.configurarNotificaciones(solicitar: true);
      expect(c.preferenciaNotificaciones!.habilitadas, false);
      await c.configurarNotificaciones(habilitar: true);
      expect(c.preferenciaNotificaciones!.habilitadas, true);
      final cambios = api.cambiosConfiguracion.length;
      await c.configurarNotificaciones();
      expect(api.cambiosConfiguracion.length, cambios);
      await c.configurarNotificaciones(habilitar: false);
      expect(c.preferenciaNotificaciones!.habilitadas, false);
    },
  );
  test('revocar desactiva y conceder de nuevo no reactiva', () async {
    await c.configurarNotificaciones(solicitar: true);
    await c.configurarNotificaciones(habilitar: true);
    dispositivo.permiso = PermisoNotificaciones.denegado;
    await c.configurarNotificaciones();
    expect(c.preferenciaNotificaciones!.habilitadas, false);
    dispositivo.permiso = PermisoNotificaciones.concedido;
    await c.configurarNotificaciones();
    expect(c.preferenciaNotificaciones!.habilitadas, false);
  });
  test(
    'mismo UUID separa suministros y recupera preferencia entre sesiones',
    () async {
      await c.configurarNotificaciones(solicitar: true);
      await c.configurarNotificaciones(habilitar: true);
      await c.salir();
      expect(c.preferenciaNotificaciones, isNull);
      c.sesion = SesionConsulta(
        token: sesionPrueba().token,
        suministro: '777',
        nombre: 'Otra',
        expiraEn: DateTime.now().add(const Duration(minutes: 20)),
      );
      await c.configurarNotificaciones();
      expect(c.preferenciaNotificaciones!.habilitadas, false);
      expect(api.cambiosConfiguracion.last.suministro, '777');
      await c.salir();
      await c.ingresar('0013100390', '01234567');
      await c.configurarNotificaciones();
      expect(c.preferenciaNotificaciones!.habilitadas, true);
    },
  );
  test(
    'una respuesta perdida no confirma y actualizar recupera estado real',
    () async {
      await c.configurarNotificaciones(solicitar: true);
      api.alGuardarConfiguracion = (s, id, p, h) async {
        api.preferenciasDispositivo['${s.suministro}/$id'] = preferenciaPrueba(
          habilitadas: true,
        );
        throw const FalloApi('Sin conexión');
      };
      await c.configurarNotificaciones(habilitar: true);
      expect(c.configuracionConfirmada, false);
      expect(c.errorConfiguracion, isNotNull);
      api.alGuardarConfiguracion = null;
      await c.configurarNotificaciones();
      expect(c.preferenciaNotificaciones!.habilitadas, true);
      expect(c.configuracionConfirmada, true);
    },
  );
  for (final accion in ['salir', 'vencer', 'disponer']) {
    test('descarta respuestas tras $accion y no envía PUT', () async {
      final pendiente = Completer<PreferenciaNotificaciones?>();
      api.alConfiguracion = (s, id) => pendiente.future;
      final carga = c.configurarNotificaciones();
      await Future<void>.delayed(Duration.zero);
      if (accion == 'salir') {
        await c.salir();
      }
      if (accion == 'vencer') {
        c.sesion = SesionConsulta(
          token: sesionPrueba().token,
          suministro: '0013100390',
          nombre: 'X',
          expiraEn: DateTime.now().subtract(const Duration(seconds: 1)),
        );
        c.verificarVencimiento();
      }
      if (accion == 'disponer') {
        c.dispose();
      }
      pendiente.complete(null);
      await carga;
      expect(api.cambiosConfiguracion, isEmpty);
      expect(c.preferenciaNotificaciones, isNull);
      if (accion == 'disponer') {
        c = ControladorConsulta(ServicioPrueba());
      }
    });
  }
  test('401 limpia toda la sesión', () async {
    api.alConfiguracion = (s, id) async =>
        throw const FalloApi('Venció', estado: 401);
    await c.configurarNotificaciones();
    expect(c.sesion, isNull);
    expect(c.permisoDispositivo, isNull);
  });
  test(
    'error de dispositivo no fabrica un permiso ni escribe en servidor',
    () async {
      dispositivo.fallo = const FalloApi('Dispositivo no disponible');
      await c.configurarNotificaciones();
      expect(c.permisoDispositivo, isNull);
      expect(api.consultasConfiguracion, 0);
    },
  );
  test(
    'bloquea doble acción y vuelve sin repetir la consulta al servidor',
    () async {
      final pendiente = Completer<PreferenciaNotificaciones?>();
      api.alConfiguracion = (s, id) => pendiente.future;
      final carga = c.configurarNotificaciones();
      await Future<void>.delayed(Duration.zero);
      await c.configurarNotificaciones(habilitar: true);
      expect(api.consultasConfiguracion, 1);
      c.revisarPermisoAlVolver();
      api.alConfiguracion = null;
      pendiente.complete(null);
      await carga;
      await Future<void>.delayed(Duration.zero);
      expect(api.consultasConfiguracion, 1);
    },
  );
  test(
    'volver sin cambios solo consulta el permiso y no repinta la pantalla',
    () async {
      await c.configurarNotificaciones(solicitar: true);
      await c.configurarNotificaciones(habilitar: true);
      final consultas = api.consultasConfiguracion;
      final escrituras = api.cambiosConfiguracion.length;
      final permisoConsultas = dispositivo.consultas;
      var avisos = 0;
      c.addListener(() => avisos++);
      c.revisarPermisoAlVolver();
      await Future<void>.delayed(Duration.zero);
      expect(dispositivo.consultas, permisoConsultas + 1);
      expect(api.consultasConfiguracion, consultas);
      expect(api.cambiosConfiguracion.length, escrituras);
      expect(c.configuracionConfirmada, true);
      expect(c.configurandoNotificaciones, false);
      expect(c.preferenciaNotificaciones!.habilitadas, true);
      expect(avisos, 0);
    },
  );
  test('volver detecta revocación y conceder otra vez no reactiva', () async {
    await c.configurarNotificaciones(solicitar: true);
    await c.configurarNotificaciones(habilitar: true);
    dispositivo.permiso = PermisoNotificaciones.denegado;
    c.revisarPermisoAlVolver();
    await Future<void>.delayed(Duration.zero);
    expect(c.permisoDispositivo, PermisoNotificaciones.denegado);
    expect(c.preferenciaNotificaciones!.habilitadas, false);
    expect(c.configuracionConfirmada, true);
    expect(
      api.cambiosConfiguracion.last.permiso,
      PermisoNotificaciones.denegado,
    );
    dispositivo.permiso = PermisoNotificaciones.concedido;
    c.revisarPermisoAlVolver();
    await Future<void>.delayed(Duration.zero);
    expect(c.preferenciaNotificaciones!.habilitadas, false);
    expect(
      api.cambiosConfiguracion.last.permiso,
      PermisoNotificaciones.concedido,
    );
    expect(dispositivo.solicitudes, 1);
  });
  test('regresos rápidos agrupan lecturas y no recargan el panel', () async {
    await c.configurarNotificaciones(solicitar: true);
    final consultas = api.consultasConfiguracion;
    final permisoConsultas = dispositivo.consultas;
    final pendiente = Completer<PermisoNotificaciones>();
    dispositivo.alConsultar = () => pendiente.future;
    c.revisarPermisoAlVolver();
    c.revisarPermisoAlVolver();
    c.revisarPermisoAlVolver();
    expect(dispositivo.consultas, permisoConsultas + 1);
    dispositivo.alConsultar = null;
    pendiente.complete(PermisoNotificaciones.concedido);
    await Future<void>.delayed(Duration.zero);
    expect(dispositivo.consultas, permisoConsultas + 2);
    expect(api.consultasConfiguracion, consultas);
    expect(c.configurandoNotificaciones, false);
  });
  for (final falla in [false, true]) {
    test(
      'una lectura tardía no sustituye un cambio manual (fallo: $falla)',
      () async {
        await c.configurarNotificaciones(solicitar: true);
        await c.configurarNotificaciones(habilitar: true);
        final pendiente = Completer<PermisoNotificaciones>();
        dispositivo.alConsultar = () => pendiente.future;
        c.revisarPermisoAlVolver();
        dispositivo.alConsultar = null;
        await c.configurarNotificaciones(habilitar: false);
        final consultas = api.consultasConfiguracion;
        if (falla) {
          pendiente.completeError(const FalloApi('Lectura anterior falló'));
        } else {
          pendiente.complete(PermisoNotificaciones.denegado);
        }
        await Future<void>.delayed(Duration.zero);
        expect(c.configuracionConfirmada, true);
        expect(c.permisoDispositivo, PermisoNotificaciones.concedido);
        expect(c.preferenciaNotificaciones!.habilitadas, false);
        expect(c.errorConfiguracion, isNull);
        expect(api.consultasConfiguracion, consultas);
      },
    );
  }
  test(
    'un fallo al leer el permiso bloquea cambios y el regreso recupera',
    () async {
      await c.configurarNotificaciones(solicitar: true);
      await c.configurarNotificaciones(habilitar: true);
      final consultas = api.consultasConfiguracion;
      dispositivo.alConsultar = () async =>
          throw const FalloApi('Sin permiso observable');
      c.revisarPermisoAlVolver();
      await Future<void>.delayed(Duration.zero);
      expect(c.configuracionConfirmada, false);
      expect(c.errorConfiguracion, contains('Sin permiso observable'));
      expect(c.preferenciaNotificaciones!.habilitadas, true);
      expect(api.consultasConfiguracion, consultas);
      dispositivo.alConsultar = null;
      c.revisarPermisoAlVolver();
      await Future<void>.delayed(Duration.zero);
      expect(c.configuracionConfirmada, true);
      expect(c.errorConfiguracion, isNull);
      expect(api.consultasConfiguracion, consultas + 1);
    },
  );
  for (final accion in ['salir', 'vencer', 'disponer']) {
    test('la lectura al volver se descarta tras $accion', () async {
      await c.configurarNotificaciones(solicitar: true);
      final consultas = api.consultasConfiguracion;
      final escrituras = api.cambiosConfiguracion.length;
      final pendiente = Completer<PermisoNotificaciones>();
      dispositivo.alConsultar = () => pendiente.future;
      c.revisarPermisoAlVolver();
      if (accion == 'salir') await c.salir();
      if (accion == 'vencer') {
        c.sesion = SesionConsulta(
          token: sesionPrueba().token,
          suministro: '0013100390',
          nombre: 'X',
          expiraEn: DateTime.now().subtract(const Duration(seconds: 1)),
        );
        c.verificarVencimiento();
      }
      if (accion == 'disponer') c.dispose();
      pendiente.complete(PermisoNotificaciones.denegado);
      await Future<void>.delayed(Duration.zero);
      expect(api.consultasConfiguracion, consultas);
      expect(api.cambiosConfiguracion.length, escrituras);
      expect(c.preferenciaNotificaciones, isNull);
      if (accion == 'disponer') c = ControladorConsulta(ServicioPrueba());
    });
  }
  test(
    'recomprueba el permiso después de una consulta lenta antes de activar',
    () async {
      dispositivo.permiso = PermisoNotificaciones.concedido;
      api.alConfiguracion = (s, id) async {
        dispositivo.permiso = PermisoNotificaciones.denegado;
        return preferenciaPrueba(habilitadas: true);
      };
      await c.configurarNotificaciones(habilitar: true);
      expect(c.preferenciaNotificaciones!.habilitadas, false);
      expect(
        api.cambiosConfiguracion.last.permiso,
        PermisoNotificaciones.denegado,
      );
    },
  );
  test('el ingreso sincroniza sin mostrar el diálogo de permiso', () async {
    await c.salir();
    api.cambiosConfiguracion.clear();
    await c.ingresar('0013100390', '01234567');
    expect(c.configuracionConfirmada, true);
    expect(dispositivo.solicitudes, 0);
    expect(c.preferenciaNotificaciones!.habilitadas, false);
  });
  for (final permiso in PermisoNotificaciones.values) {
    test(
      'solicitar muestra una respuesta concreta para ${permiso.codigo}',
      () async {
        dispositivo.respuesta = permiso;
        await c.configurarNotificaciones(solicitar: true);
        expect(c.configuracionConfirmada, true);
        expect(c.preferenciaNotificaciones!.habilitadas, false);
        expect(
          c.mensajeConfiguracion,
          contains(switch (permiso) {
            PermisoNotificaciones.concedido => 'Permiso concedido',
            PermisoNotificaciones.denegado => 'bloqueadas',
            PermisoNotificaciones.pendiente => 'todavía no confirmó',
          }),
        );
      },
    );
  }
}
