import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';

import 'datos_costos_prueba.dart';
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

  test('costos solicita el rango con Bearer y sin otro suministro', () async {
    atender = (p) async {
      expect(p.method, 'GET');
      expect(p.uri.path, '/api/v1/acceso/consulta/costos');
      expect(p.uri.queryParameters, {'meses': '6'});
      expect(
        p.headers.value('authorization'),
        'Bearer ${sesionPrueba().token}',
      );
      p.response.write(jsonEncode(respuestaCostos(meses: 6)));
      await p.response.close();
    };
    final respuesta = await api.costos(sesionPrueba(), meses: 6);
    expect(respuesta.actual.desglose!.totalEstimado, '15.88');
    expect(respuesta.anteriores.length, 6);
  });

  for (final estado in [400, 401, 404, 503]) {
    test(
      'costos conserva HTTP $estado sin exponer el cuerpo del error',
      () async {
        atender = (p) async {
          p.response.statusCode = estado;
          p.response.write('{"detail":"dato privado"}');
          await p.response.close();
        };
        await expectLater(
          api.costos(sesionPrueba()),
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

  test(
    'JSON inválido y otro suministro se traducen a un fallo visible',
    () async {
      var llamadas = 0;
      atender = (p) async {
        llamadas++;
        p.response.write(
          llamadas == 1
              ? '<html>error</html>'
              : jsonEncode(respuestaCostos()..['codigoSuministro'] = '9999'),
        );
        await p.response.close();
      };
      await expectLater(api.costos(sesionPrueba()), throwsA(isA<FalloApi>()));
      await expectLater(api.costos(sesionPrueba()), throwsA(isA<FalloApi>()));
    },
  );

  test('valida el rango antes de consultar', () async {
    await expectLater(
      api.costos(sesionPrueba(), meses: 0),
      throwsA(isA<FalloApi>()),
    );
    await expectLater(
      api.costos(sesionPrueba(), meses: 13),
      throwsA(isA<FalloApi>()),
    );
  });
}
