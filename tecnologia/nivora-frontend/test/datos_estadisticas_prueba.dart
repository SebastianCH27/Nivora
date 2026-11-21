import 'package:nivora_frontend/src/api_nivora.dart';

Map<String, dynamic> respuestaEstadisticas({
  int meses = 12,
  List<String>? estados,
  List<int>? valores,
  int diaInicio = 1,
}) {
  final consulta = diaInicio > 20
      ? DateTime.utc(2026, 12, 10, 18)
      : DateTime.utc(2026, 11, 20, 18);
  DateTime corte(int anio, int mes) => DateTime.utc(
    anio,
    mes,
    diaInicio < DateTime.utc(anio, mes + 1, 0).day
        ? diaInicio
        : DateTime.utc(anio, mes + 1, 0).day,
    5,
  );
  Map<String, String> cantidad(int milesimas) {
    String fijo(int escala) {
      final signo = milesimas < 0 ? '-' : '';
      final digitos = milesimas.abs().toString().padLeft(escala + 1, '0');
      return '$signo${digitos.substring(0, digitos.length - escala)}.${digitos.substring(digitos.length - escala)}';
    }

    return {'litros': fijo(3), 'metrosCubicos': fijo(6)};
  }

  final serie = <Map<String, dynamic>>[];
  final completos = <int>[];
  final conteos = {
    'COMPLETO': 0,
    'PARCIAL': 0,
    'SIN_DATOS': 0,
    'INCOMPATIBLE': 0,
  };
  for (var i = 0; i < meses; i++) {
    final periodo = moverMes('2026-11', i - meses);
    final anio = int.parse(periodo.substring(0, 4));
    final mes = int.parse(periodo.substring(5));
    final inicio = corte(anio, mes);
    final fin = corte(anio, mes + 1);
    final estado =
        estados?[i] ??
        ['COMPLETO', 'PARCIAL', 'SIN_DATOS', 'INCOMPATIBLE'][i % 4];
    final litros = valores?[i] ?? (i == 0 ? 0 : (i + 1) * 1000);
    final calculable = estado == 'COMPLETO' || estado == 'PARCIAL';
    conteos[estado] = conteos[estado]! + 1;
    if (estado == 'COMPLETO') completos.add(litros);
    serie.add({
      'periodo': periodo,
      'inicioMes': inicio.toIso8601String(),
      'finMesExclusivo': fin.toIso8601String(),
      'consumo': <String, dynamic>{
        'estado': estado,
        'motivo': switch (estado) {
          'COMPLETO' => null,
          'PARCIAL' => 'FALTA_LECTURA_FINAL',
          'INCOMPATIBLE' => 'CAMBIO_DE_MEDIDOR',
          _ => 'LECTURAS_INSUFICIENTES',
        },
        'tipoMedicion': 'ACUMULADA',
        'litros': calculable ? '$litros' : null,
        'metrosCubicos': calculable
            ? cantidad(litros * 1000)['metrosCubicos']!.substring(
                0,
                cantidad(litros * 1000)['metrosCubicos']!.length - 3,
              )
            : null,
        'desde': calculable ? inicio.toIso8601String() : null,
        'hasta': calculable
            ? (estado == 'COMPLETO'
                      ? fin
                      : fin.subtract(const Duration(days: 1)))
                  .toIso8601String()
            : null,
      },
    });
  }
  final inicioActual = corte(2026, 11);
  final hastaActual = inicioActual.add(const Duration(days: 5));
  final anterior = serie.last;
  final anteriorConsumo = anterior['consumo'] as Map<String, dynamic>;
  final actual = {
    'periodo': '2026-11',
    'consumo': {
      'estado': 'COMPLETO',
      'motivo': null,
      'tipoMedicion': 'ACUMULADA',
      'litros': '2500',
      'metrosCubicos': '2.500',
      'desde': inicioActual.toIso8601String(),
      'hasta': hastaActual.toIso8601String(),
    },
    'duracionSegundos': '${hastaActual.difference(inicioActual).inSeconds}',
  };
  final total = completos.fold<int>(0, (a, b) => a + b);
  return {
    'fuente': 'empresa-agua',
    'codigoSuministro': '0013100390',
    'zonaHoraria': 'America/Lima',
    'consultadoEn': consulta.toIso8601String(),
    'comparacion': {
      'actual': actual,
      'anterior': {
        'periodo': anterior['periodo'],
        'consumo': anteriorConsumo,
        'duracionSegundos': anteriorConsumo['desde'] == null
            ? null
            : '${DateTime.parse(anteriorConsumo['hasta']).difference(DateTime.parse(anteriorConsumo['desde'])).inSeconds}',
      },
      'diferencia': anteriorConsumo['litros'] == null
          ? null
          : cantidad((2500 - int.parse(anteriorConsumo['litros'])) * 1000),
    },
    'serieMensual': serie,
    'resumen': {
      'mesesCompletos': conteos['COMPLETO'],
      'mesesParciales': conteos['PARCIAL'],
      'mesesSinDatos': conteos['SIN_DATOS'],
      'mesesIncompatibles': conteos['INCOMPATIBLE'],
      'total': completos.isEmpty ? null : cantidad(total * 1000),
      'promedio': completos.isEmpty
          ? null
          : cantidad(
              (total * 2000 + completos.length) ~/ (completos.length * 2),
            ),
      'minimo': completos.isEmpty
          ? null
          : cantidad(completos.reduce((a, b) => a < b ? a : b) * 1000),
      'maximo': completos.isEmpty
          ? null
          : cantidad(completos.reduce((a, b) => a > b ? a : b) * 1000),
    },
  };
}

EstadisticasConsumo estadisticasPrueba({
  int meses = 12,
  List<String>? estados,
  List<int>? valores,
  int diaInicio = 1,
}) => EstadisticasConsumo.desdeJson(
  respuestaEstadisticas(
    meses: meses,
    estados: estados,
    valores: valores,
    diaInicio: diaInicio,
  ),
  '0013100390',
  meses: meses,
);
