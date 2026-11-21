import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';

import 'datos_comparacion_prueba.dart';

ComparacionConsumo leer(Map<String, dynamic> json) =>
    ComparacionConsumo.desdeJson(
      json,
      '0013100390',
      periodoBase: json['base']['estimacion']['periodo'],
      periodoComparado: json['comparado']['estimacion']['periodo'],
    );

void main() {
  test('meses cerrados conservan diferencia positiva y distinta duración', () {
    final datos = comparacionPrueba(base: '2026-09', comparado: '2026-10');
    expect(datos.estado, 'COMPLETA');
    expect(datos.diferenciaLitros, '3200');
    expect(datos.diferenciaMetrosCubicos, '3.200');
    expect(datos.diferenciaCosto, '8.00');
    expect(datos.sentidoConsumo, 1);
    expect(datos.sentidoCosto, 1);
    expect(datos.mismaDuracion, isFalse);
  });

  test('invertir los meses invierte ambas diferencias', () {
    final datos = comparacionPrueba(base: '2026-10', comparado: '2026-09');
    expect(datos.diferenciaLitros, '-3200');
    expect(datos.diferenciaCosto, '-8.00');
    expect(datos.sentidoConsumo, -1);
    expect(datos.sentidoCosto, -1);
  });

  test('comparar el mismo mes conserva ceros y duración igual', () {
    final datos = comparacionPrueba(base: '2026-10', comparado: '2026-10');
    expect(datos.diferenciaLitros, '0');
    expect(datos.diferenciaCosto, '0.00');
    expect(datos.sentidoConsumo, 0);
    expect(datos.mismaDuracion, isTrue);
  });

  test('la selección predeterminada usa el mes en curso del servidor', () {
    final datos = comparacionPrueba();
    expect(datos.estado, 'PARCIAL');
    expect(datos.comparado.estimacion.enCurso, isTrue);
    expect(datos.diferenciaLitros, '-10000');
    expect(datos.diferenciaCosto, '-25.00');
  });

  test(
    'conceptos incompletos permiten comparar volumen sin inventar total',
    () {
      final datos = comparacionPrueba(conceptosIncompletos: true);
      expect(datos.estado, 'NO_CALCULABLE');
      expect(datos.diferenciaLitros, '-10000');
      expect(datos.diferenciaCosto, isNull);
      expect(datos.sentidoCosto, isNull);
      expect(datos.base.estimacion.desglose!.subtotalConocido, '38.00');
    },
  );

  test('mes sin lecturas conserva diferencias e indicadores ausentes', () {
    final datos = leer(respuestaComparacion(sinDatosBase: true));
    expect(datos.estado, 'NO_CALCULABLE');
    expect(datos.diferenciaLitros, isNull);
    expect(datos.diferenciaCosto, isNull);
    expect(datos.mismaDuracion, isNull);
    expect(datos.mismaTarifa, isNull);
  });

  test('admite un calendario mensual que comienza el día cinco', () {
    final json = respuestaComparacion(diaInicio: 5);
    json['base']['estimacion']['tarifa']['aplicableDesde'] = '2026-10-05';
    final datos = leer(json);
    expect(datos.base.inicio, DateTime.utc(2026, 10, 5, 5));
    expect(datos.base.fin, DateTime.utc(2026, 11, 5, 5));
  });

  test('cobertura parcial en un mes terminado sigue siendo parcial', () {
    final json = respuestaComparacion(
      periodoBase: '2026-09',
      periodoComparado: '2026-10',
    );
    json['base']['estimacion']['consumo']['estado'] = 'PARCIAL';
    json['base']['periodoCompleto'] = false;
    json['estado'] = 'PARCIAL';
    expect(leer(json).estado, 'PARCIAL');
  });

  test('una tarifa ausente no impide la diferencia de volumen', () {
    final json = respuestaComparacion(conceptosIncompletos: true);
    final costo = json['base']['estimacion'];
    costo['estado'] = 'SIN_TARIFA_COMPATIBLE';
    costo['tarifa'] = costo['desglose'] = null;
    json['mismaVersionTarifaria'] = null;
    expect(leer(json).diferenciaLitros, '-10000');
  });

  test('acepta un cambio de tarifa informado por el servidor', () {
    final json = respuestaComparacion();
    json['comparado']['estimacion']['tarifa']['versionTarifa'] = 2;
    json['mismaVersionTarifaria'] = false;
    expect(leer(json).mismaTarifa, isFalse);
  });

  final invalidos = <String, void Function(Map<String, dynamic>)>{
    'suministro ajeno': (j) => j['codigoSuministro'] = 'otro',
    'moneda distinta': (j) => j['moneda'] = 'USD',
    'zona distinta': (j) => j['zonaHoraria'] = 'UTC',
    'fuente distinta': (j) => j['fuente'] = 'otra',
    'volumen con signo incorrecto': (j) => j['diferenciaLitros'] = '10000',
    'decimales de volumen incompatibles': (j) =>
        j['diferenciaMetrosCubicos'] = '-10.001',
    'costo con signo incorrecto': (j) => j['diferenciaCostoEstimado'] = '25.00',
    'diferencia de costo numérica': (j) => j['diferenciaCostoEstimado'] = -25,
    'estado completo para mes abierto': (j) => j['estado'] = 'COMPLETA',
    'duración de periodo falsa': (j) =>
        j['base']['duracionPeriodoSegundos'] = '1',
    'duración de tramo falsa': (j) => j['base']['duracionTramoSegundos'] = '1',
    'indicador de duración falso': (j) => j['mismaDuracionTramo'] = true,
    'indicador de tarifa ausente': (j) => j['mismaVersionTarifaria'] = null,
    'indicador de tarifa de otro tipo': (j) =>
        j['mismaVersionTarifaria'] = 'true',
    'tarifas distintas declaradas iguales': (j) =>
        j['comparado']['estimacion']['tarifa']['versionTarifa'] = 2,
    'mes abierto declarado cerrado': (j) => j['comparado']['enCurso'] = false,
    'cobertura completa falsa': (j) => j['comparado']['periodoCompleto'] = true,
    'fecha sin UTC': (j) => j['consultadoEn'] = '2026-11-20T18:00:00',
    'inicio de periodo a otra hora': (j) =>
        j['base']['estimacion']['inicioMes'] = '2026-10-01T06:00:00Z',
  };
  for (final caso in invalidos.entries) {
    test('rechaza ${caso.key}', () {
      final json = respuestaComparacion();
      caso.value(json);
      expect(() => leer(json), throwsFormatException);
    });
  }

  test('rechaza comparar subtotales como si fueran totales', () {
    final json = respuestaComparacion(conceptosIncompletos: true);
    json['diferenciaCostoEstimado'] = '-25.00';
    expect(() => leer(json), throwsFormatException);
  });

  test('rechaza meses que no corresponden a los solicitados', () {
    expect(
      () => ComparacionConsumo.desdeJson(
        respuestaComparacion(),
        '0013100390',
        periodoBase: '2026-09',
        periodoComparado: '2026-10',
      ),
      throwsFormatException,
    );
  });

  test('la respuesta predeterminada debe contener el actual y el anterior', () {
    expect(
      () => ComparacionConsumo.desdeJson(
        respuestaComparacion(
          periodoBase: '2026-09',
          periodoComparado: '2026-10',
        ),
        '0013100390',
      ),
      throwsFormatException,
    );
  });

  test('valida ambos periodos y el límite de doce meses', () {
    expect(rangoComparacionValido(null, null), isTrue);
    expect(rangoComparacionValido('2025-11', '2026-11'), isTrue);
    expect(rangoComparacionValido('2026-11', '2025-11'), isTrue);
    for (final rango in [
      (null, '2026-11'),
      ('2025-10', '2026-11'),
      ('2026-13', '2026-11'),
      ('26-10', '2026-11'),
    ]) {
      expect(rangoComparacionValido(rango.$1, rango.$2), isFalse);
    }
  });
}
