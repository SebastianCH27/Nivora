import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';

import 'datos_comparacion_prueba.dart';
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

  test(
    'usa Bearer y deja al servidor elegir los meses predeterminados',
    () async {
      atender = (p) async {
        expect(p.method, 'GET');
        expect(p.uri.path, '/api/v1/acceso/consulta/comparacion');
        expect(p.uri.queryParameters, isEmpty);
        expect(
          p.headers.value('authorization'),
          'Bearer ${sesionPrueba().token}',
        );
        p.response.write(jsonEncode(respuestaComparacion()));
        await p.response.close();
      };
      expect((await api.comparar(sesionPrueba())).diferenciaLitros, '-10000');
    },
  );

  test('envía ambos meses sin añadir suministro ni DNI a la URL', () async {
    atender = (p) async {
      expect(p.uri.queryParameters, {
        'periodoBase': '2026-09',
        'periodoComparado': '2026-10',
      });
      p.response.write(
        jsonEncode(
          respuestaComparacion(
            periodoBase: '2026-09',
            periodoComparado: '2026-10',
          ),
        ),
      );
      await p.response.close();
    };
    expect(
      (await api.comparar(
        sesionPrueba(),
        periodoBase: '2026-09',
        periodoComparado: '2026-10',
      )).estado,
      'COMPLETA',
    );
  });

  for (final estado in [400, 401, 404, 503]) {
    test('conserva HTTP $estado sin exponer el cuerpo de error', () async {
      atender = (p) async {
        p.response.statusCode = estado;
        p.response.write('{"detail":"dato privado"}');
        await p.response.close();
      };
      await expectLater(
        api.comparar(sesionPrueba()),
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

  test('rechaza un rango inválido antes de enviar HTTP', () async {
    var peticiones = 0;
    atender = (p) async {
      peticiones++;
      await p.response.close();
    };
    for (final rango in [('2026-10', null), ('2025-10', '2026-11')]) {
      await expectLater(
        api.comparar(
          sesionPrueba(),
          periodoBase: rango.$1,
          periodoComparado: rango.$2,
        ),
        throwsA(isA<FalloApi>()),
      );
    }
    expect(peticiones, 0);
  });

  test(
    'convierte una diferencia incompatible en un error comprensible',
    () async {
      atender = (p) async {
        final json = respuestaComparacion()..['diferenciaLitros'] = '1';
        p.response.write(jsonEncode(json));
        await p.response.close();
      };
      await expectLater(
        api.comparar(sesionPrueba()),
        throwsA(
          isA<FalloApi>().having(
            (f) => f.mensaje,
            'mensaje',
            contains('interpretar la comparación'),
          ),
        ),
      );
    },
  );
}
