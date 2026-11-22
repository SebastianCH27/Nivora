import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';

import 'datos_dispositivo_prueba.dart';

void main() {
  for (final permiso in PermisoNotificaciones.values) {
    test('lee ${permiso.codigo} desactivado', () {
      final p = PreferenciaNotificaciones.desdeJson(
        respuestaPreferencia(permiso: permiso.codigo),
        dispositivoId,
      );
      expect(p.permiso, permiso);
      expect(p.habilitadas, false);
      expect(p.actualizadaEn.isUtc, true);
    });
  }
  test(
    'lee activación concedida',
    () => expect(preferenciaPrueba(habilitadas: true).habilitadas, true),
  );
  for (final cambio in <String, Object?>{
    'idDispositivo': 'aaaaaaaa-1234-4123-8123-123456789abc',
    'permiso': 'AUTORIZADO',
    'habilitadas': 'true',
    'actualizadaEn': null,
  }.entries) {
    test('rechaza campo ${cambio.key}', () {
      final json = respuestaPreferencia()..[cambio.key] = cambio.value;
      expect(
        () => PreferenciaNotificaciones.desdeJson(json, dispositivoId),
        throwsFormatException,
      );
    });
  }
  for (final permiso in ['DENEGADO', 'PENDIENTE']) {
    test('no acepta activación $permiso', () {
      expect(
        () => PreferenciaNotificaciones.desdeJson(
          respuestaPreferencia(permiso: permiso, habilitadas: true),
          dispositivoId,
        ),
        throwsFormatException,
      );
    });
  }
  for (final id in [
    'ABC',
    dispositivoId.toUpperCase(),
    '1-1-1-1-1',
    '../otro',
  ]) {
    test('rechaza UUID $id', () => expect(idDispositivoValido(id), false));
  }
}
