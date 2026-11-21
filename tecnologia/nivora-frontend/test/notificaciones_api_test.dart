import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';

import 'datos_prueba.dart';
import 'datos_notificaciones_prueba.dart';

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

  test(
    'consulta con Bearer y filtro; conserva el cursor con microsegundos',
    () async {
      final cursor = paginaNotificacionesPrueba(limite: 1).siguienteAntesDe!;
      atender = (p) async {
        expect(p.method, 'GET');
        expect(p.uri.path, '/api/v1/acceso/consulta/notificaciones');
        expect(p.uri.queryParameters, {
          'limite': '1',
          'soloPendientes': 'true',
          'antesDe': cursor,
        });
        expect(
          p.headers.value('authorization'),
          'Bearer ${sesionPrueba().token}',
        );
        expect(p.uri.toString(), isNot(contains('dni')));
        expect(p.uri.toString(), isNot(contains('codigoSuministro')));
        p.response.write(
          jsonEncode(
            respuestaNotificaciones(
              limite: 1,
              soloPendientes: true,
              antesDe: cursor,
            ),
          ),
        );
        await p.response.close();
      };
      final pagina = await api.notificaciones(
        sesionPrueba(),
        limite: 1,
        soloPendientes: true,
        antesDe: cursor,
      );
      expect(pagina.avisos.single.tipo, 'ALERTA_CONSUMO');
    },
  );

  test('consulta inicial muestra ambos estados y admite lista vacía', () async {
    atender = (p) async {
      expect(p.uri.queryParameters, {
        'limite': '20',
        'soloPendientes': 'false',
      });
      p.response.write(jsonEncode(respuestaNotificaciones(disponibles: 0)));
      await p.response.close();
    };
    expect((await api.notificaciones(sesionPrueba())).avisos, isEmpty);
  });

  test(
    'marca mediante PUT sin cuerpo y acepta 204 repetido sin JSON',
    () async {
      var llamadas = 0;
      atender = (p) async {
        llamadas++;
        expect(p.method, 'PUT');
        expect(
          p.uri.path,
          '/api/v1/acceso/consulta/notificaciones/RECIBO_DISPONIBLE-2026-10/leida',
        );
        expect(p.uri.queryParameters, isEmpty);
        expect(
          p.headers.value('authorization'),
          'Bearer ${sesionPrueba().token}',
        );
        expect(await utf8.decoder.bind(p).join(), isEmpty);
        p.response.statusCode = 204;
        await p.response.close();
      };
      for (var i = 0; i < 2; i++) {
        await api.marcarNotificacionLeida(
          sesionPrueba(),
          'RECIBO_DISPONIBLE-2026-10',
        );
      }
      expect(llamadas, 2);
    },
  );

  for (final estado in [400, 401, 404, 503]) {
    for (final marcar in [false, true]) {
      test(
        '${marcar ? 'PUT' : 'GET'} conserva HTTP $estado sin exponer el cuerpo',
        () async {
          atender = (p) async {
            p.response.statusCode = estado;
            p.response.write('{"detail":"dato privado"}');
            await p.response.close();
          };
          final llamada = marcar
              ? api.marcarNotificacionLeida(
                  sesionPrueba(),
                  'ALERTA_CONSUMO-2026-11',
                )
              : api.notificaciones(sesionPrueba());
          await expectLater(
            llamada,
            throwsA(
              isA<FalloApi>()
                  .having((f) => f.estado, 'estado', estado)
                  .having(
                    (f) => f.mensaje,
                    'mensaje',
                    isNot(contains('dato privado')),
                  ),
            ),
          );
        },
      );
    }
  }

  test('rechaza parámetros e identificadores antes de enviar HTTP', () async {
    var llamadas = 0;
    atender = (p) async {
      llamadas++;
      await p.response.close();
    };
    for (final caso in [
      (0, null),
      (61, null),
      (20, 'cursor?dni=12345678'),
      (20, '2026-02-30T00:00:00Z~ALERTA_CONSUMO-2026-01'),
    ]) {
      await expectLater(
        api.notificaciones(sesionPrueba(), limite: caso.$1, antesDe: caso.$2),
        throwsA(isA<FalloApi>()),
      );
    }
    for (final id in [
      'OTRO-2026-10',
      'ALERTA_CONSUMO-2026-13',
      '../sesion',
      '',
    ]) {
      await expectLater(
        api.marcarNotificacionLeida(sesionPrueba(), id),
        throwsA(isA<FalloApi>()),
      );
    }
    expect(llamadas, 0);
  });

  test('una lista de otro suministro no se entrega a la interfaz', () async {
    atender = (p) async {
      final json = respuestaNotificaciones();
      json['codigoSuministro'] = '99999999';
      p.response.write(jsonEncode(json));
      await p.response.close();
    };
    await expectLater(
      api.notificaciones(sesionPrueba()),
      throwsA(
        isA<FalloApi>().having(
          (f) => f.mensaje,
          'mensaje',
          contains('interpretar las notificaciones'),
        ),
      ),
    );
  });

  test('no sigue redirecciones al marcar un aviso', () async {
    var llamadas = 0;
    atender = (p) async {
      llamadas++;
      p.response.statusCode = 307;
      p.response.headers.set('location', '/destino');
      await p.response.close();
    };
    await expectLater(
      api.marcarNotificacionLeida(sesionPrueba(), 'ALERTA_CONSUMO-2026-11'),
      throwsA(isA<FalloApi>().having((f) => f.estado, 'estado', 307)),
    );
    expect(llamadas, 1);
  });
}
