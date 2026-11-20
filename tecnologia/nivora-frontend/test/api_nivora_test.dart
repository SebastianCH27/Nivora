import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';

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
    servidor.listen((peticion) async {
      await atender(peticion);
    });
  });
  tearDown(() async {
    api.cerrar();
    await servidor.close(force: true);
  });

  test(
    'envía suministro y DNI como cadenas, conservando ceros iniciales',
    () async {
      atender = (p) async {
        expect(p.method, 'POST');
        expect(p.uri.path, '/api/v1/acceso/consulta/sesiones');
        expect(jsonDecode(await utf8.decoder.bind(p).join()), {
          'codigoSuministro': '0013100390',
          'dni': '01234567',
        });
        expect(p.headers.value('authorization'), isNull);
        p.response.statusCode = 201;
        p.response.write(jsonEncode(respuestaSesion()));
        await p.response.close();
      };
      final sesion = await api.ingresar('0013100390', '01234567');
      expect(sesion.suministro, '0013100390');
    },
  );

  test('consulta con Bearer y admite DELETE 204 sin cuerpo', () async {
    atender = (p) async {
      expect(
        p.headers.value('authorization'),
        'Bearer ${sesionPrueba().token}',
      );
      if (p.method == 'GET') {
        expect(p.uri.path, endsWith('/consumo-actual'));
        p.response.write(jsonEncode(respuestaConsumo()));
      } else {
        expect(p.method, 'DELETE');
        expect(p.uri.path, endsWith('/sesion'));
        p.response.statusCode = 204;
      }
      await p.response.close();
    };
    expect((await api.consumo(sesionPrueba())).metrosCubicos, '1.250');
    await api.salir(sesionPrueba());
  });

  test(
    'respeta Retry-After y no expone el detalle crudo del servidor',
    () async {
      atender = (p) async {
        p.response.statusCode = 429;
        p.response.headers.set('Retry-After', '17');
        p.response.write('{"detail":"dato privado del servidor"}');
        await p.response.close();
      };
      await expectLater(
        api.ingresar('0013100390', '01234567'),
        throwsA(
          isA<FalloApi>()
              .having(
                (f) => f.reintentarEn,
                'espera',
                const Duration(seconds: 17),
              )
              .having(
                (f) => f.mensaje,
                'mensaje',
                isNot(contains('dato privado')),
              ),
        ),
      );
    },
  );

  for (final estado in [400, 401, 503]) {
    test('comunica un error HTTP $estado sin crear sesión', () async {
      atender = (p) async {
        p.response.statusCode = estado;
        await p.response.close();
      };
      await expectLater(
        api.ingresar('0013100390', '01234567'),
        throwsA(isA<FalloApi>().having((f) => f.estado, 'estado', estado)),
      );
    });
  }

  test('rechaza redirecciones sin enviar datos al nuevo destino', () async {
    var peticiones = 0;
    atender = (p) async {
      peticiones++;
      p.response.statusCode = 307;
      p.response.headers.set('Location', '/otro-destino');
      await p.response.close();
    };
    await expectLater(
      api.ingresar('0013100390', '01234567'),
      throwsA(isA<FalloApi>()),
    );
    expect(peticiones, 1);
  });

  test('rechaza sesión de un suministro diferente', () async {
    atender = (p) async {
      p.response.statusCode = 201;
      p.response.write(jsonEncode(respuestaSesion(suministro: '99999999')));
      await p.response.close();
    };
    await expectLater(
      api.ingresar('0013100390', '01234567'),
      throwsA(isA<FalloApi>()),
    );
  });

  test('una respuesta inválida no se convierte en datos de consumo', () async {
    atender = (p) async {
      p.response.write('<html>Error</html>');
      await p.response.close();
    };
    await expectLater(api.consumo(sesionPrueba()), throwsA(isA<FalloApi>()));
  });

  test(
    'el tiempo máximo incluye una respuesta que se queda esperando',
    () async {
      final recibida = Completer<void>();
      atender = (p) async {
        recibida.complete();
      };
      final rapida = ApiNivora(
        api.base,
        desarrollo: true,
        espera: const Duration(milliseconds: 100),
      );
      try {
        final resultado = rapida.ingresar('0013100390', '01234567');
        final comprobacion = expectLater(resultado, throwsA(isA<FalloApi>()));
        await recibida.future;
        await comprobacion;
      } finally {
        rapida.cerrar();
      }
    },
  );

  test(
    'HTTP externo se rechaza incluso en desarrollo; release requiere HTTPS',
    () {
      final externa = ApiNivora(
        Uri.parse('http://192.168.18.1:8082'),
        desarrollo: true,
      );
      final release = ApiNivora(Uri.parse('http://127.0.0.1:8082'));
      expect(externa.configuracionValida, isFalse);
      expect(release.configuracionValida, isFalse);
      expect(
        ApiNivora(Uri.parse('https://api.ejemplo.com')).configuracionValida,
        isTrue,
      );
      externa.cerrar();
      release.cerrar();
    },
  );

  test('sin lecturas no equivale a cero litros', () {
    final consumo = ConsumoActual.desdeJson(
      respuestaConsumo(estado: 'SIN_DATOS'),
      '0013100390',
    );
    expect(consumo.litros, isNull);
    expect(consumo.metrosCubicos, isNull);
  });
}
