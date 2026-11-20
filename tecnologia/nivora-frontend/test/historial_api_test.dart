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
    servidor.listen((p) async => await atender(p));
  });
  tearDown(() async {
    api.cerrar();
    await servidor.close(force: true);
  });

  test('historial usa Bearer, rango y suministro de la sesión', () async {
    atender = (p) async {
      expect(p.method, 'GET');
      expect(p.uri.path, '/api/v1/acceso/consulta/historial');
      expect(p.uri.queryParameters, {'meses': '3', 'hasta': '2026-01'});
      expect(
        p.headers.value('authorization'),
        'Bearer ${sesionPrueba().token}',
      );
      p.response.write(
        jsonEncode(respuestaHistorial(meses: 3, hasta: '2026-01')),
      );
      await p.response.close();
    };
    final resultado = await api.historial(
      sesionPrueba(),
      meses: 3,
      hasta: '2026-01',
    );
    expect(resultado.periodos.map((m) => m.periodo), [
      '2026-01',
      '2025-12',
      '2025-11',
    ]);
    expect(resultado.periodos.first.metrosCubicos, '0.000');
    expect(resultado.periodos[1].metrosCubicos, '1.234');
    expect(resultado.periodos[2].litros, isNull);
  });

  test('consulta reciente deja que el backend elija el último mes', () async {
    atender = (p) async {
      expect(p.uri.queryParameters, {'meses': '12'});
      p.response.write(jsonEncode(respuestaHistorial()));
      await p.response.close();
    };
    final resultado = await api.historial(sesionPrueba());
    expect(resultado.periodos.length, 12);
    expect(resultado.periodos[3].estado, 'INCOMPATIBLE');
    expect(resultado.periodos[3].metrosCubicos, isNull);
  });

  for (final estado in [400, 401, 404, 503]) {
    test(
      'historial transmite estado HTTP $estado con mensaje seguro',
      () async {
        atender = (p) async {
          p.response.statusCode = estado;
          p.response.write('{"detail":"dato privado"}');
          await p.response.close();
        };
        await expectLater(
          api.historial(sesionPrueba()),
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

  final alteraciones = <String, void Function(Map<String, dynamic>)>{
    'otro suministro': (j) => j['codigoSuministro'] = '9999',
    'otra zona': (j) => j['zonaHoraria'] = 'UTC',
    'meses desordenados': (j) => (j['periodos'] as List).setRange(
      0,
      2,
      (j['periodos'] as List).take(2).toList().reversed,
    ),
    'mes duplicado': (j) => j['periodos'][1] = j['periodos'][0],
    'meses faltantes': (j) => (j['periodos'] as List).removeLast(),
    'lista vacía': (j) => j['periodos'] = [],
    'estado desconocido': (j) =>
        j['periodos'][0]['consumo']['estado'] = 'NUEVO',
    'decimal numérico': (j) => j['periodos'][0]['consumo']['metrosCubicos'] = 0,
    'unidades diferentes': (j) => j['periodos'][0]['consumo']['litros'] = '12',
    'corte fuera de Lima': (j) =>
        j['periodos'][0]['inicioMes'] = '2026-10-01T00:00:00Z',
    'mes completo sin cobertura': (j) =>
        j['periodos'][0]['consumo']['desde'] = '2026-10-02T05:00:00Z',
    'sin datos con importe': (j) => j['periodos'][2]['consumo']['litros'] = '0',
  };
  for (final caso in alteraciones.entries) {
    test('rechaza ${caso.key} antes de mostrar el historial', () async {
      atender = (p) async {
        final json = respuestaHistorial();
        caso.value(json);
        p.response.write(jsonEncode(json));
        await p.response.close();
      };
      await expectLater(
        api.historial(sesionPrueba()),
        throwsA(isA<FalloApi>()),
      );
    });
  }

  test('el cambio de mes se decide con la hora de Lima del servidor', () {
    final json = respuestaHistorial(meses: 3, hasta: '2025-12');
    json['consultadoEn'] = '2026-02-01T04:59:59Z'; // Enero en Lima.
    final datos = HistorialMensual.desdeJson(json, '0013100390', meses: 3);
    expect(datos.periodos.first.periodo, '2025-12');
  });

  test('mes actual y futuro no se aceptan como historial cerrado', () {
    final json = respuestaHistorial(meses: 3, hasta: '2026-11');
    expect(
      () => HistorialMensual.desdeJson(
        json,
        '0013100390',
        meses: 3,
        hasta: '2026-11',
      ),
      throwsFormatException,
    );
  });

  test(
    'conserva litros grandes sin redondeo y cero parcial con fechas iguales',
    () {
      final json = respuestaHistorial(meses: 3);
      json['periodos'][0]['consumo']['litros'] = '9223372036854775807';
      json['periodos'][0]['consumo']['metrosCubicos'] = '9223372036854775.807';
      final parcial = json['periodos'][1]['consumo'];
      parcial['hasta'] = parcial['desde'];
      parcial['litros'] = '0';
      parcial['metrosCubicos'] = '0.000';
      final resultado = HistorialMensual.desdeJson(
        json,
        '0013100390',
        meses: 3,
      );
      expect(resultado.periodos.first.litros, '9223372036854775807');
      expect(resultado.periodos[1].litros, '0');
      expect(resultado.periodos[1].estado, 'PARCIAL');
    },
  );

  test('valida el rango antes de enviar la petición', () async {
    await expectLater(
      api.historial(sesionPrueba(), meses: 0),
      throwsA(isA<FalloApi>()),
    );
    await expectLater(
      api.historial(sesionPrueba(), meses: 13),
      throwsA(isA<FalloApi>()),
    );
    await expectLater(
      api.historial(sesionPrueba(), hasta: '2026-13'),
      throwsA(isA<FalloApi>()),
    );
  });
}
