import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';

import 'datos_estadisticas_prueba.dart';

EstadisticasConsumo leer(Map<String, dynamic> json, {int meses = 12}) =>
    EstadisticasConsumo.desdeJson(json, '0013100390', meses: meses);

void main() {
  test('conserva orden cronológico y resume solo los meses completos', () {
    final datos = estadisticasPrueba();
    expect(datos.serie.first.periodo, '2025-11');
    expect(datos.serie.last.periodo, '2026-10');
    expect(datos.periodoActual, '2026-11');
    expect(datos.conteos.values, everyElement(3));
    expect(datos.total!.litros, '14000.000');
    expect(datos.promedio!.litros, '4666.667');
    expect(datos.promedio!.metrosCubicos, '4.666667');
    expect(datos.minimo!.litros, '0.000');
    expect(datos.maximo!.litros, '9000.000');
  });

  test('un mes completo con cero participa en el promedio', () {
    final datos = estadisticasPrueba(
      meses: 3,
      estados: ['COMPLETO', 'COMPLETO', 'PARCIAL'],
      valores: [0, 1000, 9000],
    );
    expect(datos.total!.litros, '1000.000');
    expect(datos.promedio!.litros, '500.000');
    expect(datos.minimo!.litros, '0.000');
    expect(datos.maximo!.litros, '1000.000');
    expect(datos.serie.last.consumo.litros, '9000');
  });

  test(
    'conserva promedio de fracciones de litro y su equivalente exacto en m³',
    () {
      final datos = estadisticasPrueba(
        meses: 3,
        estados: List.filled(3, 'COMPLETO'),
        valores: [1, 0, 0],
      );
      expect(datos.promedio!.litros, '0.333');
      expect(datos.promedio!.metrosCubicos, '0.000333');
      final seis = estadisticasPrueba(
        meses: 6,
        estados: List.filled(6, 'COMPLETO'),
        valores: [1, 0, 0, 0, 0, 0],
      );
      expect(seis.promedio!.litros, '0.167');
    },
  );

  test(
    'sin meses completos el resumen es ausente y los parciales siguen visibles',
    () {
      final datos = estadisticasPrueba(
        meses: 3,
        estados: ['PARCIAL', 'SIN_DATOS', 'INCOMPATIBLE'],
      );
      expect([
        datos.total,
        datos.promedio,
        datos.minimo,
        datos.maximo,
      ], everyElement(isNull));
      expect(datos.serie.first.consumo.litros, '0');
      expect(datos.serie[1].consumo.litros, isNull);
    },
  );

  test(
    'un periodo con todos los consumos completos en cero tiene resumen cero',
    () {
      final datos = estadisticasPrueba(
        meses: 3,
        estados: List.filled(3, 'COMPLETO'),
        valores: [0, 0, 0],
      );
      expect(datos.total!.milesimasLitro, BigInt.zero);
      expect(datos.promedio!.milesimasLitro, BigInt.zero);
    },
  );

  test('admite el calendario del día cinco y el ajuste del día 31', () {
    expect(estadisticasPrueba(diaInicio: 5).serie.last.inicio.day, 5);
    final ajustado = estadisticasPrueba(diaInicio: 31);
    expect(ajustado.serie.first.inicio.day, 30);
    expect(ajustado.serie.last.fin.day, 30);
    expect(
      ajustado.serie.firstWhere((m) => m.periodo == '2026-02').inicio.day,
      28,
    );
  });

  test('mantiene cantidades mayores al entero exacto de un double', () {
    const litros = '9007199254740993';
    final cantidad = {
      'litros': '$litros.000',
      'metrosCubicos': '9007199254740.993000',
    };
    final json = respuestaEstadisticas(meses: 1);
    final consumo = json['serieMensual'][0]['consumo'];
    consumo['litros'] = litros;
    consumo['metrosCubicos'] = '9007199254740.993';
    for (final clave in ['total', 'promedio', 'minimo', 'maximo']) {
      json['resumen'][clave] = cantidad;
    }
    json['comparacion']['diferencia'] = {
      'litros': '-9007199254738493',
      'metrosCubicos': '-9007199254738.493',
    };
    expect(leer(json, meses: 1).total!.litros, '$litros.000');
  });

  test('conserva duraciones con fracciones de segundo', () {
    final json = respuestaEstadisticas();
    json['comparacion']['actual']['consumo']['hasta'] =
        '2026-11-06T05:00:00.123456Z';
    json['comparacion']['actual']['duracionSegundos'] = '432000.123456';
    expect(leer(json).serie, hasLength(12));
  });

  final invalidos = <String, void Function(Map<String, dynamic>)>{
    'suministro ajeno': (j) => j['codigoSuministro'] = '9999',
    'fuente distinta': (j) => j['fuente'] = 'otra',
    'zona distinta': (j) => j['zonaHoraria'] = 'UTC',
    'fecha sin UTC': (j) => j['consultadoEn'] = '2026-11-20T18:00:00',
    'serie fuera de orden': (j) =>
        j['serieMensual'] = (j['serieMensual'] as List).reversed.toList(),
    'serie incompleta': (j) => (j['serieMensual'] as List).removeLast(),
    'mes repetido': (j) => j['serieMensual'][1] = j['serieMensual'][0],
    'unidades de consumo incoherentes': (j) =>
        j['serieMensual'][0]['consumo']['metrosCubicos'] = '1.000',
    'consumo numérico': (j) => j['serieMensual'][0]['consumo']['litros'] = 0,
    'estado desconocido': (j) =>
        j['serieMensual'][0]['consumo']['estado'] = 'OTRO',
    'cobertura completa falsa': (j) =>
        j['serieMensual'][0]['consumo']['desde'] = '2025-11-02T05:00:00Z',
    'valor para un mes sin datos': (j) =>
        j['serieMensual'][2]['consumo']['litros'] = '0',
    'conteo incompatible': (j) => j['resumen']['mesesCompletos'] = 12,
    'total incorrecto': (j) =>
        j['resumen']['total'] = {'litros': '1', 'metrosCubicos': '0.001'},
    'promedio incorrecto': (j) =>
        j['resumen']['promedio'] = {'litros': '1', 'metrosCubicos': '0.001'},
    'mínimo incorrecto': (j) =>
        j['resumen']['minimo'] = {'litros': '1', 'metrosCubicos': '0.001'},
    'máximo incorrecto': (j) =>
        j['resumen']['maximo'] = {'litros': '1', 'metrosCubicos': '0.001'},
    'unidades del resumen incoherentes': (j) =>
        j['resumen']['total']['metrosCubicos'] = '1.001',
    'resumen ausente con meses completos': (j) => j['resumen']['total'] = null,
    'mes actual futuro': (j) =>
        j['comparacion']['actual']['periodo'] = '2026-12',
    'duración incorrecta': (j) =>
        j['comparacion']['actual']['duracionSegundos'] = '1',
    'duración con consumo ausente': (j) =>
        j['comparacion']['anterior']['duracionSegundos'] = '1',
    'comparación de otro mes': (j) =>
        j['comparacion']['anterior']['periodo'] = '2026-09',
    'fin de análisis a otra hora': (j) =>
        j['serieMensual'][0]['finMesExclusivo'] = '2025-12-01T06:00:00Z',
  };
  for (final caso in invalidos.entries) {
    test('rechaza ${caso.key}', () {
      final json = respuestaEstadisticas();
      caso.value(json);
      expect(() => leer(json), throwsFormatException);
    });
  }

  test('rechaza diferencia con signo contrario y resumen cero inventado', () {
    final json = respuestaEstadisticas(meses: 1);
    json['comparacion']['diferencia'] = {
      'litros': '-2500',
      'metrosCubicos': '-2.500',
    };
    expect(() => leer(json, meses: 1), throwsFormatException);
    final sinDatos = respuestaEstadisticas(meses: 1, estados: ['SIN_DATOS']);
    sinDatos['resumen']['total'] = {'litros': '0', 'metrosCubicos': '0.000'};
    expect(() => leer(sinDatos, meses: 1), throwsFormatException);
  });
}
