import 'package:nivora_frontend/src/api_nivora.dart';

import 'datos_costos_prueba.dart';

Map<String, dynamic> respuestaComparacion({
  String? periodoBase,
  String? periodoComparado,
  bool conceptosIncompletos = false,
  bool sinDatosBase = false,
  int diaInicio = 1,
}) {
  final consulta = DateTime.parse('2026-11-20T18:00:00Z');
  Map<String, dynamic> tramo(String periodo, bool sinDatos) {
    final mes = int.parse(periodo.substring(5));
    final anio = int.parse(periodo.substring(0, 4));
    final inicio = DateTime.utc(anio, mes, diaInicio, 5);
    final fin = DateTime.utc(anio, mes + 1, diaInicio, 5);
    final enCurso = consulta.isBefore(fin);
    final hasta = enCurso ? inicio.add(const Duration(days: 10)) : fin;
    final litros = periodo == '2026-11'
        ? 3200
        : periodo == '2026-10'
        ? 13200
        : 10000;
    String decimal(int unidades, int escala) {
      final digitos = unidades.toString().padLeft(escala + 1, '0');
      return '${digitos.substring(0, digitos.length - escala)}.${digitos.substring(digitos.length - escala)}';
    }

    final costo = periodoCostoPrueba(
      periodo,
      sinDatos
          ? 'SIN_CONSUMO_CALCULABLE'
          : conceptosIncompletos
          ? 'CONCEPTOS_INCOMPLETOS'
          : 'ESTIMADO',
      enCurso: enCurso,
    );
    costo['inicioMes'] = inicio.toIso8601String();
    costo['finMesExclusivo'] = fin.toIso8601String();
    if (!sinDatos) {
      costo['consumo']['litros'] = '$litros';
      costo['consumo']['metrosCubicos'] = decimal(litros, 3);
      costo['consumo']['desde'] = inicio.toIso8601String();
      costo['consumo']['hasta'] = hasta.toIso8601String();
      costo['desglose']['agua'] = decimal(litros * 250 ~/ 1000, 2);
      costo['desglose']['subtotalConocido'] = decimal(
        litros * 250 ~/ 1000 + 500,
        2,
      );
      costo['desglose']['igv'] = conceptosIncompletos ? null : '0.00';
      costo['desglose']['totalEstimado'] = conceptosIncompletos
          ? null
          : costo['desglose']['subtotalConocido'];
      costo['tarifa']['igvPorcentaje'] = conceptosIncompletos ? null : '0.0000';
    }
    return {
      'estimacion': costo,
      'enCurso': enCurso,
      'periodoCompleto': !enCurso && !sinDatos,
      'duracionPeriodoSegundos': '${fin.difference(inicio).inSeconds}',
      'duracionTramoSegundos': sinDatos
          ? null
          : '${hasta.difference(inicio).inSeconds}',
    };
  }

  final base = tramo(periodoBase ?? '2026-10', sinDatosBase);
  final comparado = tramo(periodoComparado ?? '2026-11', false);
  final a = base['estimacion'];
  final b = comparado['estimacion'];
  final volumen = sinDatosBase
      ? null
      : int.parse(b['consumo']['litros']) - int.parse(a['consumo']['litros']);
  final conCostos = !sinDatosBase && !conceptosIncompletos;
  String diferenciaM3(int litros) =>
      '${litros < 0 ? '-' : ''}${litros.abs() ~/ 1000}.${(litros.abs() % 1000).toString().padLeft(3, '0')}';
  String diferenciaDinero(String a, String b) {
    int centimos(String valor) => int.parse(valor.replaceAll('.', ''));
    final v = centimos(b) - centimos(a);
    return '${v < 0 ? '-' : ''}${v.abs() ~/ 100}.${(v.abs() % 100).toString().padLeft(2, '0')}';
  }

  return {
    'fuente': 'empresa-agua',
    'codigoSuministro': '0013100390',
    'moneda': 'PEN',
    'zonaHoraria': 'America/Lima',
    'consultadoEn': consulta.toIso8601String(),
    'revisionTarifaria': '1',
    'revisionTarifariaAdministrativa': null,
    'base': base,
    'comparado': comparado,
    'estado': !conCostos
        ? 'NO_CALCULABLE'
        : base['periodoCompleto'] == true &&
              comparado['periodoCompleto'] == true
        ? 'COMPLETA'
        : 'PARCIAL',
    'diferenciaLitros': volumen == null ? null : '$volumen',
    'diferenciaMetrosCubicos': volumen == null ? null : diferenciaM3(volumen),
    'diferenciaCostoEstimado': !conCostos
        ? null
        : diferenciaDinero(
            a['desglose']['totalEstimado'],
            b['desglose']['totalEstimado'],
          ),
    'mismaDuracionTramo': sinDatosBase
        ? null
        : base['duracionTramoSegundos'] == comparado['duracionTramoSegundos'],
    'mismaVersionTarifaria': sinDatosBase ? null : true,
  };
}

ComparacionConsumo comparacionPrueba({
  String? base,
  String? comparado,
  bool conceptosIncompletos = false,
}) => ComparacionConsumo.desdeJson(
  respuestaComparacion(
    periodoBase: base,
    periodoComparado: comparado,
    conceptosIncompletos: conceptosIncompletos,
  ),
  '0013100390',
  periodoBase: base,
  periodoComparado: comparado,
);
