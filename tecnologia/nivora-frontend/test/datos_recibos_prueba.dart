import 'package:nivora_frontend/src/api_nivora.dart';

Map<String, dynamic> reciboPrueba(
  String periodo, {
  bool calendarioDistinto = false,
}) {
  final anio = int.parse(periodo.substring(0, 4));
  final mes = int.parse(periodo.substring(5));
  final inicio = DateTime.utc(anio, mes, 1, 5);
  final fin = DateTime.utc(anio, mes + 1, 1, 5);
  String civil(DateTime f) => f.toIso8601String().substring(0, 10);
  Map<String, String> ventana(int dia) => {
    'inicio': DateTime.utc(anio, mes, dia, 5).toIso8601String(),
    'finExclusivo': DateTime.utc(anio, mes + 1, dia, 5).toIso8601String(),
  };
  return {
    'numeroRecibo': 'EA-${periodo.replaceAll('-', '')}-001',
    'periodo': periodo,
    'consumoLitros': '5637.000',
    'cargoFijo': '5.00',
    'importeConsumo': '8.46',
    'importeTotal': '13.46',
    'fechaDisponibilidad': civil(fin),
    'fechaVencimiento': civil(fin.add(const Duration(days: 7))),
    'codigoTarifa': 'DOMESTICA',
    'versionTarifa': 1,
    'recibidoEn': fin.add(const Duration(hours: 1)).toIso8601String(),
    'periodoFacturado': {
      'inicio': inicio.toIso8601String(),
      'finExclusivo': fin.toIso8601String(),
    },
    'periodoAnalisis': ventana(calendarioDistinto ? 5 : 1),
    'coincideConPeriodoAnalisis': !calendarioDistinto,
  };
}

Map<String, dynamic> respuestaRecibos({
  int limite = 12,
  String? antesDe,
  int disponibles = 15,
}) {
  final periodos = [
    for (var i = 0; i < disponibles; i++) moverMes('2026-10', -i),
  ].where((p) => antesDe == null || p.compareTo(antesDe) < 0).toList();
  final pagina = periodos.take(limite).toList();
  final hayMas = periodos.length > limite;
  return {
    'codigoSuministro': '0013100390',
    'moneda': 'PEN',
    'zonaFechas': 'America/Lima',
    'recibos': [for (final periodo in pagina) reciboPrueba(periodo)],
    'hayMas': hayMas,
    'siguienteAntesDe': hayMas ? pagina.last : null,
  };
}

PaginaRecibos paginaRecibosPrueba({
  int limite = 12,
  String? antesDe,
  int disponibles = 15,
}) => PaginaRecibos.desdeJson(
  respuestaRecibos(limite: limite, antesDe: antesDe, disponibles: disponibles),
  '0013100390',
  limite: limite,
  antesDe: antesDe,
);
