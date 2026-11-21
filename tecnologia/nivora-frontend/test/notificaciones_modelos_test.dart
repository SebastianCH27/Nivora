import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';

import 'datos_notificaciones_prueba.dart';

void main() {
  test('mezcla avisos y conserva microsegundos, fechas de Lima y ceros del suministro', () {
    final pagina = paginaNotificacionesPrueba();
    expect(pagina.avisos, hasLength(20));
    expect(pagina.avisos.first.recibo!.vencimiento, DateTime.utc(2026, 11, 8));
    expect(pagina.avisos[1].consumo!.anteriorLitros, '5637');
    expect(pagina.avisos[3].leidaEn, isNotNull);
    expect(pagina.avisos.first.registradaEn.microsecond, 456);
    expect(() => pagina.avisos.clear(), throwsUnsupportedError);
  });

  test('pagina por fecha, tipo y mes, incluso cuando coinciden las fechas', () {
    final primera = paginaNotificacionesPrueba(limite: 1);
    final segunda = paginaNotificacionesPrueba(
      limite: 1,
      antesDe: primera.siguienteAntesDe,
    );
    expect(primera.avisos.single.tipo, 'RECIBO_DISPONIBLE');
    expect(segunda.avisos.single.tipo, 'ALERTA_CONSUMO');
    expect(
      segunda.avisos.single.registradaEn,
      primera.avisos.single.registradaEn,
    );
  });

  test('vacío es válido y no inventa cursor ni conteo total', () {
    final pagina = paginaNotificacionesPrueba(disponibles: 0);
    expect(pagina.avisos, isEmpty);
    expect(pagina.hayMas, isFalse);
  });

  test('conserva volúmenes superiores a 2^53 sin double', () {
    final json = avisoPrueba(1);
    json['consumo'] = {
      'actualLitros': '9007199254740993',
      'anteriorLitros': '9007199254740992',
    };
    final aviso = NotificacionConsulta.desdeJson(json);
    expect(
      metrosCubicosNotificacion(aviso.consumo!.actualLitros),
      '9007199254740,993',
    );
  });

  final invalidas = <String, void Function(Map<String, dynamic>)>{
    'suministro distinto': (j) => j['codigoSuministro'] = '13100390',
    'lista ausente': (j) => j['notificaciones'] = null,
    'más elementos que el límite': (j) =>
        j['notificaciones'].add(avisoPrueba(20)),
    'id repetido': (j) => j['notificaciones'][1] = j['notificaciones'][0],
    'orden invertido con fecha igual': (j) {
      final primero = j['notificaciones'][0];
      j['notificaciones'][0] = j['notificaciones'][1];
      j['notificaciones'][1] = primero;
    },
    'tipo desconocido': (j) => j['notificaciones'][0]['tipo'] = 'OTRO',
    'mes incompatible con id': (j) =>
        j['notificaciones'][0]['periodo'] = '2026-09',
    'leída sin fecha': (j) => j['notificaciones'][0]['leida'] = true,
    'pendiente con fecha': (j) =>
        j['notificaciones'][0]['leidaEn'] = '2026-11-02T00:00:00Z',
    'fecha inexistente': (j) =>
        j['notificaciones'][0]['registradaEn'] = '2026-02-30T00:00:00Z',
    'hora sin zona': (j) =>
        j['notificaciones'][0]['registradaEn'] = '2026-11-01T08:00:00',
    'calendario imposible': (j) =>
        j['notificaciones'][0]['recibo']['fechaVencimiento'] = '2026-11-31',
    'alerta sin consumo': (j) => j['notificaciones'][1]['consumo'] = null,
    'alerta sin superación': (j) =>
        j['notificaciones'][1]['consumo']['actualLitros'] = '5637',
    'recibo con consumo ajeno': (j) => j['notificaciones'][0]['consumo'] = {
      'actualLitros': '6',
      'anteriorLitros': '5',
    },
    'hayMás incoherente': (j) => j['hayMas'] = false,
    'cursor ajeno al último': (j) =>
        j['siguienteAntesDe'] = cursorAviso(j['notificaciones'][0]),
    'cursor inválido': (j) =>
        j['siguienteAntesDe'] = '2026-11-01~RECIBO_DISPONIBLE-2026-10',
  };
  for (final caso in invalidas.entries) {
    test('rechaza ${caso.key}', () {
      final json = respuestaNotificaciones();
      caso.value(json);
      expect(
        () => PaginaNotificaciones.desdeJson(json, '0013100390'),
        throwsFormatException,
      );
    });
  }

  test('rechaza leídas en el filtro de pendientes', () {
    expect(
      () => PaginaNotificaciones.desdeJson(
        respuestaNotificaciones(),
        '0013100390',
        soloPendientes: true,
      ),
      throwsFormatException,
    );
  });

  test('rechaza página que vuelve a incluir su corte exclusivo', () {
    final primera = respuestaNotificaciones();
    expect(
      () => PaginaNotificaciones.desdeJson(
        primera,
        '0013100390',
        antesDe: primera['siguienteAntesDe'],
      ),
      throwsFormatException,
    );
  });

  test('204 puede actualizar el estado sin inventar la fecha de lectura', () {
    final aviso = paginaNotificacionesPrueba().avisos.first;
    final leida = aviso.comoLeida();
    expect(aviso.leida, isFalse);
    expect(leida.leida, isTrue);
    expect(leida.leidaEn, isNull);
    expect(leida.recibo, same(aviso.recibo));
  });
}
