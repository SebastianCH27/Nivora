import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';

import 'datos_prueba.dart';
import 'datos_proyeccion_prueba.dart';

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
    'proyección usa Bearer en su ruta y el suministro de la sesión',
    () async {
      atender = (p) async {
        expect(p.method, 'GET');
        expect(p.uri.path, '/api/v1/acceso/consulta/proyeccion');
        expect(p.uri.queryParameters, isEmpty);
        expect(
          p.headers.value('authorization'),
          'Bearer ${sesionPrueba().token}',
        );
        p.response.write(jsonEncode(respuestaProyeccion()));
        await p.response.close();
      };
      expect((await api.proyeccion(sesionPrueba())).volumen!.litros, '12000');
    },
  );

  for (final estado in [400, 401, 503]) {
    test('proyección conserva HTTP $estado con un mensaje seguro', () async {
      atender = (p) async {
        p.response.statusCode = estado;
        p.response.write('{"detail":"dato privado"}');
        await p.response.close();
      };
      await expectLater(
        api.proyeccion(sesionPrueba()),
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

  test(
    'respuesta incompatible produce un error de proyección visible',
    () async {
      atender = (p) async {
        final json = respuestaProyeccion()..['codigoSuministro'] = '9999';
        p.response.write(jsonEncode(json));
        await p.response.close();
      };
      await expectLater(
        api.proyeccion(sesionPrueba()),
        throwsA(
          isA<FalloApi>().having(
            (f) => f.mensaje,
            'mensaje',
            contains('interpretar la proyección'),
          ),
        ),
      );
    },
  );
}
