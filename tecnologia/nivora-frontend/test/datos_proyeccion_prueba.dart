import 'package:nivora_frontend/src/api_nivora.dart';

import 'datos_costos_prueba.dart';

Map<String, dynamic> respuestaProyeccion({
  String estado = 'PROYECTADO',
  String estadoCosto = 'CONCEPTOS_INCOMPLETOS',
}) {
  final conConsumo = estado != 'SIN_CONSUMO_CALCULABLE';
  final origenCosto = periodoCostoPrueba('2026-11', estadoCosto, enCurso: true);
  final detalle = origenCosto['desglose'];
  if (detalle != null) {
    detalle['agua'] = '18.00';
    detalle['subtotalConocido'] = '23.00';
    detalle['igv'] = estadoCosto == 'ESTIMADO' ? '4.14' : null;
    detalle['totalEstimado'] = estadoCosto == 'ESTIMADO' ? '27.14' : null;
  }
  final hasta = switch (estado) {
    'TIEMPO_INSUFICIENTE' => '2026-11-01T17:00:00Z',
    'PERIODO_CERRADO' => '2026-12-01T05:00:00Z',
    _ => '2026-11-11T05:00:00Z',
  };
  return {
    'fuente': 'empresa-agua',
    'codigoSuministro': '0013100390',
    'moneda': 'PEN',
    'periodo': '2026-11',
    'zonaHoraria': 'America/Lima',
    'inicioMes': '2026-11-01T05:00:00Z',
    'finMesExclusivo': '2026-12-01T05:00:00Z',
    'calculadoEn': estado == 'PERIODO_CERRADO' ? hasta : '2026-11-20T18:00:00Z',
    'supuestos': <String, dynamic>{
      'metodo': 'RITMO_PROMEDIO_CONSTANTE',
      'minimoObservadoSegundos': 86400,
    },
    'estado': estado,
    'observado': <String, dynamic>{
      'estado': !conConsumo
          ? 'SIN_DATOS'
          : estado == 'COBERTURA_INCOMPLETA'
          ? 'PARCIAL'
          : 'COMPLETO',
      'motivo': conConsumo ? null : 'LECTURAS_INSUFICIENTES',
      'litros': conConsumo ? '4000' : null,
      'metrosCubicos': conConsumo ? '4.000' : null,
      'desde': !conConsumo
          ? null
          : estado == 'COBERTURA_INCOMPLETA'
          ? '2026-11-02T05:00:00Z'
          : '2026-11-01T05:00:00Z',
      'hasta': conConsumo ? hasta : null,
    },
    'actualizacion': <String, dynamic>{
      'estado': conConsumo ? 'CORTE_CONFIRMADO' : 'SIN_LECTURAS',
      'ultimaLectura': conConsumo ? hasta : null,
      'confirmadoHasta': conConsumo ? hasta : null,
    },
    'proyeccion': estado != 'PROYECTADO'
        ? null
        : <String, dynamic>{
            'litros': '12000',
            'metrosCubicos': '12.000',
            'promedioLitrosPorDia': '400.000',
            'segundosObservados': '864000',
            'segundosDelMes': '2592000',
          },
    'costoProyectado': estado != 'PROYECTADO'
        ? null
        : <String, dynamic>{
            'estado': estadoCosto,
            'fuenteTarifaria': origenCosto['tarifa']?['fuenteTarifaria'],
            'revisionTarifaria': origenCosto['tarifa']?['revisionTarifaria'],
            'tarifa': origenCosto['tarifa'],
            'desglose': detalle,
          },
  };
}

ProyeccionConsumo proyeccionPrueba() =>
    ProyeccionConsumo.desdeJson(respuestaProyeccion(), '0013100390');
