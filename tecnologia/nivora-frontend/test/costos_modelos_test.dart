import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';
import 'package:nivora_frontend/src/formatos_consulta.dart';

import 'datos_costos_prueba.dart';

void main() {
  CostosConsumo leer(Map<String, dynamic> json) =>
      CostosConsumo.desdeJson(json, '0013100390', meses: 3);

  test('distingue total, subtotal, ausencia de tarifa y falta de lecturas', () {
    final costos = leer(respuestaCostos());
    expect(costos.actual.desglose!.totalEstimado, '15.88');
    expect(costos.actual.enCurso, isTrue);
    expect(costos.anteriores[0].desglose!.subtotalConocido, '13.46');
    expect(costos.anteriores[0].desglose!.totalEstimado, isNull);
    expect(costos.anteriores[1].estado, 'SIN_CONSUMO_CALCULABLE');
    expect(costos.anteriores[1].litros, isNull);
    expect(costos.anteriores[2].estado, 'SIN_TARIFA_COMPATIBLE');
    expect(costos.anteriores[2].litros, '5637');
  });

  test('admite cero informado sin confundirlo con un concepto ausente', () {
    final json = respuestaCostos();
    final detalle = json['actual']['desglose'];
    for (final campo in [
      'agua',
      'alcantarillado',
      'cargoFijo',
      'subtotalConocido',
      'igv',
      'totalEstimado',
    ]) {
      detalle[campo] = '0.00';
    }
    json['actual']['consumo']['litros'] = '0';
    json['actual']['consumo']['metrosCubicos'] = '0.000';
    final costo = leer(json).actual;
    expect(costo.estado, 'ESTIMADO');
    expect(soles(costo.desglose!.totalEstimado!), 'S/ 0.00');
    expect(costo.desglose!.alcantarillado, '0.00');
    expect(leer(json).anteriores[0].desglose!.alcantarillado, isNull);
  });

  test('importes superiores a la precisión de double conservan céntimos', () {
    final json = respuestaCostos();
    final detalle = json['actual']['desglose'];
    detalle['agua'] = '9007199254740993.01';
    detalle['subtotalConocido'] = '9007199254740998.01';
    detalle['totalEstimado'] = '9007199254741000.43';
    expect(
      soles(leer(json).actual.desglose!.totalEstimado!),
      'S/ 9007199254741000.43',
    );
  });

  test('conserva cobertura parcial con importe y cargo fijo del mes', () {
    final json = respuestaCostos();
    json['actual']['consumo']['estado'] = 'PARCIAL';
    json['actual']['consumo']['desde'] = '2026-11-02T05:00:00Z';
    final actual = leer(json).actual;
    expect(actual.cobertura, 'PARCIAL');
    expect(actual.desglose!.cargoFijo, '5.00');
  });

  test(
    'tasa conocida con alcantarillado ausente mantiene subtotal incompleto',
    () {
      final json = respuestaCostos();
      json['anteriores'][0]['tarifa']['igvPorcentaje'] = '18.0000';
      final anterior = leer(json).anteriores[0];
      expect(anterior.desglose!.igv, isNull);
      expect(anterior.estado, 'CONCEPTOS_INCOMPLETOS');
    },
  );

  test('valida el cambio de año con el reloj del servidor en Lima', () {
    final json = respuestaCostos();
    json['consultadoEn'] = '2027-01-01T04:59:00Z'; // Diciembre en Lima.
    json['actual'] = periodoCostoPrueba('2026-12', 'ESTIMADO', enCurso: true);
    json['anteriores'] = [
      for (var i = 0; i < 3; i++)
        periodoCostoPrueba(moverMes('2026-12', -i - 1), 'ESTIMADO'),
    ];
    expect(leer(json).actual.periodo, '2026-12');
  });

  final casos = <String, void Function(Map<String, dynamic>)>{
    'moneda distinta': (j) => j['moneda'] = 'USD',
    'zona distinta': (j) => j['zonaHoraria'] = 'UTC',
    'fuente distinta': (j) => j['fuente'] = 'otra',
    'rango incompleto': (j) => (j['anteriores'] as List).removeLast(),
    'mes duplicado': (j) => j['anteriores'][1] = j['anteriores'][0],
    'mes actual incorrecto': (j) => j['actual']['periodo'] = '2026-12',
    'corte mensual incorrecto': (j) =>
        j['actual']['inicioMes'] = '2026-11-01T00:00:00Z',
    'consumo numérico': (j) => j['actual']['consumo']['litros'] = 5637,
    'unidades distintas': (j) =>
        j['actual']['consumo']['metrosCubicos'] = '5.638',
    'lectura futura': (j) =>
        j['actual']['consumo']['hasta'] = '2026-11-21T05:00:00Z',
    'importe numérico': (j) => j['actual']['desglose']['agua'] = 8.46,
    'importe negativo': (j) => j['actual']['desglose']['agua'] = '-8.46',
    'precisión monetaria inesperada': (j) =>
        j['actual']['desglose']['agua'] = '8.461',
    'subtotal inconsistente': (j) =>
        j['actual']['desglose']['subtotalConocido'] = '13.45',
    'total inconsistente': (j) =>
        j['actual']['desglose']['totalEstimado'] = '15.87',
    'total en conceptos incompletos': (j) =>
        j['anteriores'][0]['desglose']['totalEstimado'] = '13.46',
    'estimado sin IGV': (j) => j['actual']['desglose']['igv'] = null,
    'estimado sin alcantarillado': (j) =>
        j['actual']['desglose']['alcantarillado'] = null,
    'tarifa iniciada a mitad de mes': (j) =>
        j['actual']['tarifa']['aplicableDesde'] = '2026-11-15',
    'tarifa terminada a mitad de mes': (j) =>
        j['actual']['tarifa']['aplicableHasta'] = '2026-11-15',
    'fecha normalizada inválida': (j) =>
        j['actual']['tarifa']['aplicableDesde'] = '2026-02-31',
    'estado desconocido': (j) => j['actual']['estado'] = 'OTRO',
    'consumo ausente con importe': (j) =>
        j['actual']['consumo']['estado'] = 'SIN_DATOS',
    'versión tarifaria inválida': (j) =>
        j['actual']['tarifa']['versionTarifa'] = 0,
  };
  for (final caso in casos.entries) {
    test('rechaza ${caso.key}', () {
      final json = respuestaCostos();
      caso.value(json);
      expect(() => leer(json), throwsFormatException);
    });
  }
}
