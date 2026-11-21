import 'package:nivora_frontend/src/api_nivora.dart';

Map<String, dynamic> respuestaCostos({int meses = 3}) => {
  'fuente': 'empresa-agua',
  'codigoSuministro': '0013100390',
  'moneda': 'PEN',
  'zonaHoraria': 'America/Lima',
  'consultadoEn': '2026-11-20T18:00:00Z',
  'revisionTarifaria': '1',
  'revisionTarifariaAdministrativa': null,
  'actual': periodoCostoPrueba('2026-11', 'ESTIMADO', enCurso: true),
  'anteriores': List.generate(
    meses,
    (i) => periodoCostoPrueba(
      moverMes('2026-11', -i - 1),
      [
        'CONCEPTOS_INCOMPLETOS',
        'SIN_CONSUMO_CALCULABLE',
        'SIN_TARIFA_COMPATIBLE',
        'ESTIMADO',
      ][i % 4],
    ),
  ),
};

Map<String, dynamic> periodoCostoPrueba(
  String periodo,
  String estado, {
  bool enCurso = false,
  String? cobertura,
}) {
  final inicio = DateTime.utc(
    int.parse(periodo.substring(0, 4)),
    int.parse(periodo.substring(5)),
    1,
    5,
  );
  final fin = DateTime.utc(inicio.year, inicio.month + 1, 1, 5);
  final consumo = estado != 'SIN_CONSUMO_CALCULABLE';
  final conImporte = estado == 'ESTIMADO' || estado == 'CONCEPTOS_INCOMPLETOS';
  final completo = estado == 'ESTIMADO';
  return {
    'periodo': periodo,
    'inicioMes': inicio.toIso8601String(),
    'finMesExclusivo': fin.toIso8601String(),
    'estado': estado,
    'consumo': <String, dynamic>{
      'estado': cobertura ?? (consumo ? 'COMPLETO' : 'SIN_DATOS'),
      'motivo': consumo ? null : 'LECTURAS_INSUFICIENTES',
      'litros': consumo ? '5637' : null,
      'metrosCubicos': consumo ? '5.637' : null,
      'desde': consumo ? inicio.toIso8601String() : null,
      'hasta': consumo
          ? (enCurso ? inicio.add(const Duration(days: 18)) : fin)
                .toIso8601String()
          : null,
    },
    'tarifa': conImporte
        ? <String, dynamic>{
            'codigoTarifa': 'DOMESTICA',
            'versionTarifa': 1,
            'nombreTarifa': 'Tarifa de prueba',
            'aplicableDesde': '2020-01-01',
            'aplicableHasta': null,
            'igvPorcentaje': completo ? '18.0000' : null,
            'fuenteTarifaria': 'empresa-agua',
            'revisionTarifaria': '1',
          }
        : null,
    'desglose': conImporte
        ? <String, dynamic>{
            'agua': '8.46',
            'alcantarillado': completo ? '0.00' : null,
            'cargoFijo': '5.00',
            'subtotalConocido': '13.46',
            'igv': completo ? '2.42' : null,
            'totalEstimado': completo ? '15.88' : null,
            'tramos': <dynamic>[],
          }
        : null,
  };
}

CostosConsumo costosPrueba({int meses = 3}) => CostosConsumo.desdeJson(
  respuestaCostos(meses: meses),
  '0013100390',
  meses: meses,
);
