import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';
import 'package:nivora_frontend/src/controlador_consulta.dart';

import 'datos_prueba.dart';
import 'datos_notificaciones_prueba.dart';

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
    'carga a demanda y añade páginas sin perder los avisos anteriores',
    () async {
      expect(api.consultasNotificaciones, 0);
      final consumo = control.consumo;
      await control.cargarNotificaciones();
      final primera = control.notificaciones!;
      expect(primera.avisos, hasLength(20));
      await control.cargarNotificaciones(mas: true);
      expect(control.notificaciones!.avisos, hasLength(40));
      expect(api.rangosNotificaciones.last.antesDe, primera.siguienteAntesDe);
      await control.cargarNotificaciones(mas: true);
      expect(control.notificaciones!.avisos, hasLength(45));
      expect(control.notificaciones!.hayMas, isFalse);
      await control.cargarNotificaciones(mas: true);
      expect(api.consultasNotificaciones, 3);
      expect(control.consumo, same(consumo));
    },
  );

  test('actualizar vuelve a recientes con el filtro activo', () async {
    await control.cargarNotificaciones(soloPendientes: true);
    await control.cargarNotificaciones(mas: true);
    await control.cargarNotificaciones();
    expect(api.rangosNotificaciones.last, (
      limite: 20,
      soloPendientes: true,
      antesDe: null,
    ));
    expect(control.notificaciones!.avisos, hasLength(20));
  });

  test(
    'al cambiar el filtro no atribuye datos anteriores al filtro nuevo',
    () async {
      await control.cargarNotificaciones();
      api.alNotificaciones = (_, _, _) async =>
          throw const FalloApi('Sin conexión');
      await control.cargarNotificaciones(soloPendientes: true);
      expect(control.notificaciones, isNull);
      expect(control.soloPendientesNotificaciones, isTrue);
      expect(control.errorNotificaciones, 'Sin conexión');
    },
  );

  test(
    'fallo al cargar más conserva la lista y el mismo cursor para reintentar',
    () async {
      await control.cargarNotificaciones();
      final primera = control.notificaciones;
      api.alNotificaciones = (_, _, _) async =>
          throw const FalloApi('Sin conexión');
      await control.cargarNotificaciones(mas: true);
      expect(control.notificaciones, same(primera));
      expect(control.errorAlAmpliarNotificaciones, isTrue);
      api.alNotificaciones = null;
      await control.cargarNotificaciones(mas: true);
      expect(control.notificaciones!.avisos, hasLength(40));
      expect(api.rangosNotificaciones.last.antesDe, primera!.siguienteAntesDe);
    },
  );

  test(
    'fallo al actualizar identifica y conserva los avisos anteriores',
    () async {
      await control.cargarNotificaciones();
      final anterior = control.notificaciones;
      api.alNotificaciones = (_, _, _) async =>
          throw const FalloApi('Sin conexión');
      await control.cargarNotificaciones();
      expect(control.notificaciones, same(anterior));
      expect(control.errorAlAmpliarNotificaciones, isFalse);
    },
  );

  test('no mezcla una página repetida con la lista cargada', () async {
    await control.cargarNotificaciones();
    final primera = control.notificaciones!;
    api.alNotificaciones = (_, _, _) async => primera;
    await control.cargarNotificaciones(mas: true);
    expect(control.notificaciones, same(primera));
    expect(control.errorNotificaciones, contains('Actualiza la bandeja'));
  });

  test(
    'confirma la lectura en todos y recupera la fecha real al actualizar',
    () async {
      await control.cargarNotificaciones();
      final id = control.notificaciones!.avisos.first.id;
      await control.marcarNotificacionLeida(id);
      expect(control.notificaciones!.avisos.first.leida, isTrue);
      expect(control.notificaciones!.avisos.first.leidaEn, isNull);
      expect(control.mensajeNotificaciones, 'Aviso marcado como leído.');
      await control.marcarNotificacionLeida(id);
      expect(api.marcasNotificaciones, [id]);
      await control.cargarNotificaciones();
      expect(control.notificaciones!.avisos.first.leidaEn, isNotNull);
    },
  );

  test(
    'retira la leída de pendientes y conserva cursor para avisos anteriores',
    () async {
      await control.cargarNotificaciones(soloPendientes: true);
      final anterior = control.notificaciones!;
      final id = anterior.avisos.last.id;
      await control.marcarNotificacionLeida(id);
      expect(control.notificaciones!.avisos, hasLength(19));
      expect(control.notificaciones!.avisos.any((a) => a.id == id), isFalse);
      expect(
        control.notificaciones!.siguienteAntesDe,
        anterior.siguienteAntesDe,
      );
      await control.cargarNotificaciones(mas: true);
      expect(control.notificaciones!.avisos, hasLength(39));
      expect(control.notificaciones!.avisos.every((a) => !a.leida), isTrue);
    },
  );

  test('si todos los cargados se leen, permite seguir paginando', () async {
    await control.cargarNotificaciones(soloPendientes: true);
    final ids = control.notificaciones!.avisos.map((a) => a.id).toList();
    for (final id in ids) {
      await control.marcarNotificacionLeida(id);
    }
    expect(control.notificaciones!.avisos, isEmpty);
    expect(control.notificaciones!.hayMas, isTrue);
    await control.cargarNotificaciones(mas: true);
    expect(control.notificaciones!.avisos, hasLength(20));
  });

  for (final estado in [404, 503]) {
    test('PUT $estado no cambia la lectura y permite reintentar', () async {
      await control.cargarNotificaciones();
      final id = control.notificaciones!.avisos.first.id;
      api.alMarcarNotificacion = (_) async =>
          throw FalloApi('Fallo', estado: estado);
      await control.marcarNotificacionLeida(id);
      expect(control.notificaciones!.avisos.first.leida, isFalse);
      expect(
        control.errorMarcarNotificacion,
        contains(
          estado == 404 ? 'ya no está disponible' : 'No pudimos confirmar',
        ),
      );
      api.alMarcarNotificacion = null;
      await control.marcarNotificacionLeida(id);
      expect(control.notificaciones!.avisos.first.leida, isTrue);
    });
  }

  test('bloquea doble marca, filtros y paginación mientras confirma', () async {
    await control.cargarNotificaciones();
    final id = control.notificaciones!.avisos.first.id;
    final pendiente = Completer<void>();
    api.alMarcarNotificacion = (_) => pendiente.future;
    final marca = control.marcarNotificacionLeida(id);
    await control.marcarNotificacionLeida(id);
    await control.cargarNotificaciones(soloPendientes: true);
    await control.cargarNotificaciones(mas: true);
    expect(api.marcasNotificaciones, [id]);
    expect(api.consultasNotificaciones, 1);
    expect(control.soloPendientesNotificaciones, isFalse);
    pendiente.complete();
    await marca;
    expect(control.ocupadaBandeja, isFalse);
  });

  test(
    'ignora identificadores ajenos y no inicia operaciones sin sesión',
    () async {
      await control.cargarNotificaciones();
      await control.marcarNotificacionLeida('RECIBO_DISPONIBLE-1900-01');
      await control.salir();
      await control.cargarNotificaciones();
      await control.marcarNotificacionLeida('RECIBO_DISPONIBLE-2026-10');
      expect(api.marcasNotificaciones, isEmpty);
      expect(api.consultasNotificaciones, 1);
    },
  );

  for (final marcar in [false, true]) {
    test(
      '401 en ${marcar ? 'lectura' : 'lista'} borra sesión y todas las consultas',
      () async {
        await control.cargarNotificaciones();
        await control.cargarEstadisticas();
        await control.cargarRecibos();
        if (marcar) {
          api.alMarcarNotificacion = (_) async =>
              throw const FalloApi('Terminó', estado: 401);
          await control.marcarNotificacionLeida(
            control.notificaciones!.avisos.first.id,
          );
        } else {
          api.alNotificaciones = (_, _, _) async =>
              throw const FalloApi('Terminó', estado: 401);
          await control.cargarNotificaciones();
        }
        expect(control.sesion, isNull);
        expect(control.notificaciones, isNull);
        expect(control.consumo, isNull);
        expect(control.estadisticas, isNull);
        expect(control.recibos, isNull);
        expect(control.ocupadaBandeja, isFalse);
      },
    );
  }

  test(
    'salir descarta una lista tardía incluso si ya se inició otra sesión',
    () async {
      final pendiente = Completer<PaginaNotificaciones>();
      api.alNotificaciones = (_, _, _) => pendiente.future;
      final carga = control.cargarNotificaciones();
      await control.salir();
      await control.ingresar('0013100390', '01234567');
      pendiente.complete(paginaNotificacionesPrueba());
      await carga;
      expect(control.notificaciones, isNull);
      expect(control.sesion, isNotNull);
    },
  );

  test('salir durante una marca no repuebla la siguiente sesión', () async {
    await control.cargarNotificaciones();
    final pendiente = Completer<void>();
    api.alMarcarNotificacion = (_) => pendiente.future;
    final marca = control.marcarNotificacionLeida(
      control.notificaciones!.avisos.first.id,
    );
    await control.salir();
    await control.ingresar('0013100390', '01234567');
    pendiente.complete();
    await marca;
    expect(control.notificaciones, isNull);
    expect(control.mensajeNotificaciones, isNull);
  });

  test('vencimiento local descarta una marca pendiente', () async {
    await control.cargarNotificaciones();
    final pendiente = Completer<void>();
    api.alMarcarNotificacion = (_) => pendiente.future;
    final marca = control.marcarNotificacionLeida(
      control.notificaciones!.avisos.first.id,
    );
    control.sesion = SesionConsulta(
      token: sesionPrueba().token,
      suministro: '0013100390',
      nombre: 'Prueba',
      expiraEn: DateTime.now().subtract(const Duration(seconds: 1)),
    );
    control.verificarVencimiento();
    pendiente.complete();
    await marca;
    expect(control.sesion, isNull);
    expect(control.notificaciones, isNull);
    expect(control.marcandoNotificacion, isNull);
  });

  test('401 de otra consulta descarta una página de avisos en curso', () async {
    final pendiente = Completer<PaginaNotificaciones>();
    api.alNotificaciones = (_, _, _) => pendiente.future;
    final carga = control.cargarNotificaciones();
    api.alConsultar = () async => throw const FalloApi('Terminó', estado: 401);
    await control.actualizar();
    pendiente.complete(paginaNotificacionesPrueba());
    await carga;
    expect(control.notificaciones, isNull);
    expect(control.sesion, isNull);
  });
}
