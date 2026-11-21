import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';

import 'datos_estadisticas_prueba.dart';
import 'datos_prueba.dart';

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

  for (final meses in [3, 6, 12]) {
    test(
      'consulta $meses meses con Bearer sin datos personales en la URL',
      () async {
        atender = (p) async {
          expect(p.method, 'GET');
          expect(p.uri.path, '/api/v1/acceso/consulta/estadisticas');
          expect(p.uri.queryParameters, {'meses': '$meses'});
          expect(
            p.headers.value('authorization'),
            'Bearer ${sesionPrueba().token}',
          );
          p.response.write(jsonEncode(respuestaEstadisticas(meses: meses)));
          await p.response.close();
        };
        expect(
          (await api.estadisticas(sesionPrueba(), meses: meses)).serie,
          hasLength(meses),
        );
      },
    );
  }

  for (final estado in [400, 401, 404, 503]) {
    test('conserva HTTP $estado sin exponer el cuerpo de error', () async {
      atender = (p) async {
        p.response.statusCode = estado;
        p.response.write('{"detail":"dato privado"}');
        await p.response.close();
      };
      await expectLater(
        api.estadisticas(sesionPrueba()),
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

  test('rechaza meses fuera del contrato antes de enviar HTTP', () async {
    var consultas = 0;
    atender = (p) async {
      consultas++;
      await p.response.close();
    };
    for (final meses in [0, 13]) {
      await expectLater(
        api.estadisticas(sesionPrueba(), meses: meses),
        throwsA(isA<FalloApi>()),
      );
    }
    expect(consultas, 0);
  });

  test('una respuesta incoherente produce un mensaje comprensible', () async {
    atender = (p) async {
      final json = respuestaEstadisticas();
      json['resumen']['mesesSinDatos'] = 0;
      p.response.write(jsonEncode(json));
      await p.response.close();
    };
    await expectLater(
      api.estadisticas(sesionPrueba()),
      throwsA(
        isA<FalloApi>().having(
          (f) => f.mensaje,
          'mensaje',
          contains('interpretar las estadísticas'),
        ),
      ),
    );
  });
}
