import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';

import 'datos_recibos_prueba.dart';

PaginaRecibos leer(
  Map<String, dynamic> json, {
  int limite = 12,
  String? antesDe,
}) => PaginaRecibos.desdeJson(
  json,
  '0013100390',
  limite: limite,
  antesDe: antesDe,
);

void main() {
  test('pagina recibos de más reciente a más antiguo con corte exclusivo', () {
    final primera = paginaRecibosPrueba();
    expect(primera.recibos, hasLength(12));
    expect(primera.recibos.first.periodo, '2026-10');
    expect(primera.siguienteAntesDe, '2025-11');
    final segunda = paginaRecibosPrueba(antesDe: primera.siguienteAntesDe);
    expect(segunda.recibos.map((r) => r.periodo), [
      '2025-10',
      '2025-09',
      '2025-08',
    ]);
    expect(segunda.hayMas, isFalse);
  });

  test('la lista vacía es un resultado válido e inmutable', () {
    final datos = paginaRecibosPrueba(disponibles: 0);
    expect(datos.recibos, isEmpty);
    expect(datos.hayMas, isFalse);
    expect(
      () => datos.recibos.add(ReciboEmitido.desdeJson(reciboPrueba('2026-10'))),
      throwsUnsupportedError,
    );
  });

  test('acepta los límites de una y sesenta filas de la API', () {
    expect(paginaRecibosPrueba(limite: 1).recibos, hasLength(1));
    expect(
      paginaRecibosPrueba(limite: 60, disponibles: 65).recibos,
      hasLength(60),
    );
  });

  test('mantiene los cargos emitidos, referencias y fechas civiles', () {
    final datos = ReciboEmitido.desdeJson(
      reciboPrueba('2026-10')..['numeroRecibo'] = '000000123',
    );
    expect(datos.numero, '000000123');
    expect(datos.importeTotal, '13.46');
    expect(datos.litrosParaMostrar, '5637');
    expect(datos.metrosCubicos, '5.637');
    expect(datos.disponibleDesde, DateTime.utc(2026, 11, 1));
    expect(datos.vencimiento, DateTime.utc(2026, 11, 8));
    expect(datos.recibidoEn, DateTime.utc(2026, 11, 1, 6));
  });

  test('conserva fracciones de litro en m³ sin redondear', () {
    final datos = ReciboEmitido.desdeJson(
      reciboPrueba('2026-10')..['consumoLitros'] = '5637.125',
    );
    expect(datos.litrosParaMostrar, '5637.125');
    expect(datos.metrosCubicos, '5.637125');
  });

  test('el consumo cero es válido y diferente a una lista vacía', () {
    final datos = ReciboEmitido.desdeJson(
      reciboPrueba('2026-10')..['consumoLitros'] = '0.000',
    );
    expect(datos.litrosParaMostrar, '0');
    expect(datos.metrosCubicos, '0.000');
  });

  test('valida sumas de céntimos exactas y el importe máximo del contrato', () {
    final json = reciboPrueba('2026-10');
    json['cargoFijo'] = '0.01';
    json['importeConsumo'] = '0.02';
    json['importeTotal'] = '0.03';
    expect(ReciboEmitido.desdeJson(json).importeTotal, '0.03');
    json['cargoFijo'] = '0.00';
    json['importeConsumo'] = json['importeTotal'] = '9999999999.99';
    json['consumoLitros'] = '999999999999.999';
    final maximo = ReciboEmitido.desdeJson(json);
    expect(maximo.importeTotal, '9999999999.99');
    expect(maximo.metrosCubicos, '999999999.999999');
  });

  test('conserva ventanas distintas para facturación y análisis', () {
    final datos = ReciboEmitido.desdeJson(
      reciboPrueba('2026-10', calendarioDistinto: true),
    );
    expect(datos.coincideConAnalisis, isFalse);
    expect(datos.facturado.inicio, DateTime.utc(2026, 10, 1, 5));
    expect(datos.analisis.inicio, DateTime.utc(2026, 10, 5, 5));
  });

  test('admite un día de corte ajustado a febrero', () {
    final json = reciboPrueba('2024-02', calendarioDistinto: true);
    json['periodoAnalisis'] = {
      'inicio': '2024-02-29T05:00:00Z',
      'finExclusivo': '2024-03-31T05:00:00Z',
    };
    expect(ReciboEmitido.desdeJson(json).analisis.inicio.day, 29);
  });

  final invalidos = <String, void Function(Map<String, dynamic>)>{
    'número vacío': (j) => j['numeroRecibo'] = '',
    'número con espacios extremos': (j) => j['numeroRecibo'] = ' 001',
    'número con controles': (j) => j['numeroRecibo'] = '001\nprivado',
    'número demasiado largo': (j) => j['numeroRecibo'] = 'x' * 41,
    'periodo inválido': (j) => j['periodo'] = '2026-13',
    'consumo negativo': (j) => j['consumoLitros'] = '-1.000',
    'consumo numérico': (j) => j['consumoLitros'] = 5637,
    'consumo en notación científica': (j) => j['consumoLitros'] = '1e3',
    'consumo con demasiados decimales': (j) => j['consumoLitros'] = '1.0001',
    'consumo fuera de precisión': (j) =>
        j['consumoLitros'] = '1000000000000.000',
    'total incoherente': (j) => j['importeTotal'] = '13.45',
    'cargo negativo': (j) => j['cargoFijo'] = '-5.00',
    'cargo con demasiados decimales': (j) => j['cargoFijo'] = '5.001',
    'importe fuera de precisión': (j) => j['importeTotal'] = '10000000000.00',
    'fecha civil inexistente': (j) => j['fechaVencimiento'] = '2026-11-31',
    'disponibilidad dentro del mes facturado': (j) =>
        j['fechaDisponibilidad'] = '2026-10-31',
    'vencimiento anterior a disponibilidad': (j) =>
        j['fechaVencimiento'] = '2026-10-31',
    'versión cero': (j) => j['versionTarifa'] = 0,
    'versión de otro tipo': (j) => j['versionTarifa'] = '1',
    'tarifa vacía': (j) => j['codigoTarifa'] = '',
    'recepción sin UTC': (j) => j['recibidoEn'] = '2026-11-01T06:00:00',
    'facturación fuera del mes civil': (j) =>
        j['periodoFacturado']['inicio'] = '2026-10-05T05:00:00Z',
    'análisis a otra hora': (j) =>
        j['periodoAnalisis']['inicio'] = '2026-10-01T06:00:00Z',
    'coincidencia falsa': (j) => j['coincideConPeriodoAnalisis'] = false,
  };
  for (final caso in invalidos.entries) {
    test('rechaza ${caso.key}', () {
      final json = reciboPrueba('2026-10');
      caso.value(json);
      expect(() => ReciboEmitido.desdeJson(json), throwsFormatException);
    });
  }

  final paginasInvalidas = <String, void Function(Map<String, dynamic>)>{
    'suministro ajeno': (j) => j['codigoSuministro'] = '9999',
    'moneda distinta': (j) => j['moneda'] = 'USD',
    'zona distinta': (j) => j['zonaFechas'] = 'UTC',
    'lista ausente': (j) => j['recibos'] = null,
    'corte que no coincide con la última fila': (j) =>
        j['siguienteAntesDe'] = '2025-10',
    'corte con hayMas falso': (j) => j['hayMas'] = false,
    'indicador de otro tipo': (j) => j['hayMas'] = 'true',
    'números repetidos': (j) =>
        j['recibos'][1]['numeroRecibo'] = j['recibos'][0]['numeroRecibo'],
    'periodos fuera de orden': (j) =>
        j['recibos'] = (j['recibos'] as List).reversed.toList(),
  };
  for (final caso in paginasInvalidas.entries) {
    test('rechaza página con ${caso.key}', () {
      final json = respuestaRecibos();
      caso.value(json);
      expect(() => leer(json), throwsFormatException);
    });
  }

  test('rechaza filas fuera del límite o del corte exclusivo solicitado', () {
    expect(() => leer(respuestaRecibos(), limite: 1), throwsFormatException);
    expect(
      () => leer(respuestaRecibos(), antesDe: '2026-10'),
      throwsFormatException,
    );
    final parcial = respuestaRecibos(disponibles: 1)..['hayMas'] = true;
    parcial['siguienteAntesDe'] = '2026-10';
    expect(() => leer(parcial), throwsFormatException);
  });

  test('valida los parámetros sin usar el reloj del dispositivo', () {
    expect(parametrosRecibosValidos(12, '2030-01'), isTrue);
    for (final caso in [
      (0, null),
      (61, null),
      (12, '2026-00'),
      (12, '26-10'),
      (12, '0000-01'),
    ]) {
      expect(parametrosRecibosValidos(caso.$1, caso.$2), isFalse);
    }
  });
}
