import 'package:nivora_frontend/src/api_nivora.dart';

Map<String, dynamic> avisoPrueba(int i, {Set<String> leidas = const {}}) {
  final periodo = moverMes('2026-10', -(i ~/ 2));
  final recibo = i.isEven;
  final tipo = recibo ? 'RECIBO_DISPONIBLE' : 'ALERTA_CONSUMO';
  final id = '$tipo-$periodo';
  final inicio = DateTime.utc(
    int.parse(periodo.substring(0, 4)),
    int.parse(periodo.substring(5)) + 1,
    1,
    8,
    0,
    0,
    123,
    456,
  );
  final leida = leidas.contains(id) || i == 3;
  return {
    'id': id,
    'tipo': tipo,
    'periodo': periodo,
    'titulo': recibo
        ? 'Tu recibo está disponible'
        : 'Superaste el consumo del mes anterior',
    'registradaEn': inicio.toIso8601String(),
    'leida': leida,
    'leidaEn': leida
        ? inicio.add(const Duration(minutes: 10)).toIso8601String()
        : null,
    'consumo': recibo
        ? null
        : {'actualLitros': '6200', 'anteriorLitros': '5637'},
    'recibo': recibo
        ? {
            'numeroRecibo': 'EA-${periodo.replaceAll('-', '')}-001',
            'fechaDisponibilidad': inicio.toIso8601String().substring(0, 10),
            'fechaVencimiento': inicio
                .add(const Duration(days: 7))
                .toIso8601String()
                .substring(0, 10),
          }
        : null,
  };
}

String cursorAviso(Map<String, dynamic> aviso) =>
    '${aviso['registradaEn']}~${aviso['id']}';

Map<String, dynamic> respuestaNotificaciones({
  int limite = 20,
  bool soloPendientes = false,
  String? antesDe,
  int disponibles = 45,
  Set<String> leidas = const {},
}) {
  var avisos = List.generate(
    disponibles,
    (i) => avisoPrueba(i, leidas: leidas),
  );
  if (soloPendientes) {
    avisos = avisos.where((a) => a['leida'] == false).toList();
  }
  if (antesDe != null) {
    final partes = antesDe.split('~');
    final fecha = DateTime.parse(partes[0]);
    avisos = avisos.where((a) {
      final orden = DateTime.parse(a['registradaEn']).compareTo(fecha);
      return orden < 0 ||
          (orden == 0 && (a['id'] as String).compareTo(partes[1]) < 0);
    }).toList();
  }
  final hayMas = avisos.length > limite;
  final pagina = avisos.take(limite).toList();
  return {
    'codigoSuministro': '0013100390',
    'notificaciones': pagina,
    'hayMas': hayMas,
    'siguienteAntesDe': hayMas ? cursorAviso(pagina.last) : null,
  };
}

PaginaNotificaciones paginaNotificacionesPrueba({
  int limite = 20,
  bool soloPendientes = false,
  String? antesDe,
  int disponibles = 45,
  Set<String> leidas = const {},
}) => PaginaNotificaciones.desdeJson(
  respuestaNotificaciones(
    limite: limite,
    soloPendientes: soloPendientes,
    antesDe: antesDe,
    disponibles: disponibles,
    leidas: leidas,
  ),
  '0013100390',
  limite: limite,
  soloPendientes: soloPendientes,
  antesDe: antesDe,
);
