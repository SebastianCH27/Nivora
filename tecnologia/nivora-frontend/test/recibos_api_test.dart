import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';

import 'datos_prueba.dart';
import 'datos_recibos_prueba.dart';

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
    'consulta recibos con Bearer, límite y suministro de la sesión',
    () async {
      atender = (p) async {
        expect(p.method, 'GET');
        expect(p.uri.path, '/api/v1/acceso/consulta/recibos');
        expect(p.uri.queryParameters, {'limite': '12'});
        expect(
          p.headers.value('authorization'),
          'Bearer ${sesionPrueba().token}',
        );
        p.response.write(jsonEncode(respuestaRecibos()));
        await p.response.close();
      };
      expect((await api.recibos(sesionPrueba())).recibos, hasLength(12));
    },
  );

  test(
    'envía el corte exclusivo y el límite sin DNI ni suministro en la URL',
    () async {
      atender = (p) async {
        expect(p.uri.queryParameters, {'limite': '1', 'antesDe': '2026-10'});
        p.response.write(
          jsonEncode(respuestaRecibos(limite: 1, antesDe: '2026-10')),
        );
        await p.response.close();
      };
      final pagina = await api.recibos(
        sesionPrueba(),
        limite: 1,
        antesDe: '2026-10',
      );
      expect(pagina.recibos.single.periodo, '2026-09');
    },
  );

  test('HTTP 200 con lista vacía es válido', () async {
    atender = (p) async {
      p.response.write(jsonEncode(respuestaRecibos(disponibles: 0)));
      await p.response.close();
    };
    expect((await api.recibos(sesionPrueba())).recibos, isEmpty);
  });

  for (final estado in [400, 401, 503]) {
    test('conserva HTTP $estado sin mostrar el cuerpo privado', () async {
      atender = (p) async {
        p.response.statusCode = estado;
        p.response.write('{"detail":"dato privado"}');
        await p.response.close();
      };
      await expectLater(
        api.recibos(sesionPrueba()),
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
    });
  }

  test('no envía HTTP para límites o cortes inválidos', () async {
    var consultas = 0;
    atender = (p) async {
      consultas++;
      await p.response.close();
    };
    for (final caso in [(0, null), (61, null), (12, '2026-13')]) {
      await expectLater(
        api.recibos(sesionPrueba(), limite: caso.$1, antesDe: caso.$2),
        throwsA(isA<FalloApi>()),
      );
    }
    expect(consultas, 0);
  });

  test('una respuesta incoherente produce un mensaje comprensible', () async {
    atender = (p) async {
      final json = respuestaRecibos();
      json['recibos'][0]['importeTotal'] = '999.00';
      p.response.write(jsonEncode(json));
      await p.response.close();
    };
    await expectLater(
      api.recibos(sesionPrueba()),
      throwsA(
        isA<FalloApi>().having(
          (f) => f.mensaje,
          'mensaje',
          contains('interpretar los recibos'),
        ),
      ),
    );
  });
}
