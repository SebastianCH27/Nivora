import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';

import 'datos_prueba.dart';
import 'datos_dispositivo_prueba.dart';

void main() {
  late HttpServer servidor;
  late ApiNivora api;
  late Future<void> Function(HttpRequest) atender;
  setUp(() async {
    servidor = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    api = ApiNivora(
      Uri.parse('http://127.0.0.1:${servidor.port}'),
      desarrollo: true,
    );
    servidor.listen((p) async => await atender(p));
  });
  tearDown(() async {
    api.cerrar();
    await servidor.close(force: true);
  });
  test('GET con Bearer y UUID sin suministro ni DNI', () async {
    atender = (p) async {
      expect(p.method, 'GET');
      expect(
        p.uri.path,
        '/api/v1/acceso/consulta/notificaciones/dispositivos/$dispositivoId',
      );
      expect(p.uri.hasQuery, false);
      expect(
        p.headers.value('authorization'),
        'Bearer ${sesionPrueba().token}',
      );
      p.response.write(jsonEncode(respuestaPreferencia()));
      await p.response.close();
    };
    expect(
      (await api.configuracionNotificaciones(
        sesionPrueba(),
        dispositivoId,
      ))!.habilitadas,
      false,
    );
  });
  test('404 significa instalación sin registrar', () async {
    atender = (p) async {
      p.response.statusCode = 404;
      await p.response.close();
    };
    expect(
      await api.configuracionNotificaciones(sesionPrueba(), dispositivoId),
      isNull,
    );
  });
  test('PUT envía booleano JSON y confirma respuesta del servidor', () async {
    atender = (p) async {
      expect(p.method, 'PUT');
      expect(p.headers.contentType!.mimeType, 'application/json');
      expect(
        p.headers.value('authorization'),
        'Bearer ${sesionPrueba().token}',
      );
      expect(jsonDecode(await utf8.decoder.bind(p).join()), {
        'permiso': 'CONCEDIDO',
        'habilitadas': true,
      });
      p.response.write(jsonEncode(respuestaPreferencia(habilitadas: true)));
      await p.response.close();
    };
    expect(
      (await api.guardarConfiguracionNotificaciones(
        sesionPrueba(),
        dispositivoId,
        PermisoNotificaciones.concedido,
        true,
      )).habilitadas,
      true,
    );
  });
  for (final estado in [400, 401, 403, 429, 503]) {
    test('propaga HTTP $estado', () async {
      atender = (p) async {
        p.response.statusCode = estado;
        await p.response.close();
      };
      await expectLater(
        api.configuracionNotificaciones(sesionPrueba(), dispositivoId),
        throwsA(isA<FalloApi>().having((e) => e.estado, 'estado', estado)),
      );
    });
  }
  test('404 al guardar sigue siendo un fallo', () async {
    atender = (p) async {
      p.response.statusCode = 404;
      await p.response.close();
    };
    await expectLater(
      api.guardarConfiguracionNotificaciones(
        sesionPrueba(),
        dispositivoId,
        PermisoNotificaciones.concedido,
        false,
      ),
      throwsA(isA<FalloApi>().having((e) => e.estado, 'estado', 404)),
    );
  });
  test('no confirma una respuesta distinta a lo solicitado', () async {
    atender = (p) async {
      p.response.write(jsonEncode(respuestaPreferencia()));
      await p.response.close();
    };
    await expectLater(
      api.guardarConfiguracionNotificaciones(
        sesionPrueba(),
        dispositivoId,
        PermisoNotificaciones.concedido,
        true,
      ),
      throwsA(isA<FalloApi>()),
    );
  });
  test('bloquea activación inválida y UUID inválido antes del HTTP', () async {
    atender = (p) async {
      fail('No debe realizar una solicitud');
    };
    await expectLater(
      api.configuracionNotificaciones(sesionPrueba(), 'ABC'),
      throwsA(isA<FalloApi>()),
    );
    await expectLater(
      api.guardarConfiguracionNotificaciones(
        sesionPrueba(),
        dispositivoId,
        PermisoNotificaciones.denegado,
        true,
      ),
      throwsA(isA<FalloApi>()),
    );
  });
  test('rechaza identidad cruzada y JSON inválido', () async {
    atender = (p) async {
      p.response.write(
        jsonEncode(
          respuestaPreferencia(id: 'aaaaaaaa-1234-4123-8123-123456789abc'),
        ),
      );
      await p.response.close();
    };
    await expectLater(
      api.configuracionNotificaciones(sesionPrueba(), dispositivoId),
      throwsA(isA<FalloApi>()),
    );
  });
}
