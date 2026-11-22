import 'package:nivora_frontend/src/api_nivora.dart';
import 'package:nivora_frontend/src/dispositivo_notificaciones.dart';

const dispositivoId = '12345678-1234-4123-8123-123456789abc';

class DispositivoPrueba implements DispositivoNotificaciones {
  String id = dispositivoId;
  PermisoNotificaciones permiso = PermisoNotificaciones.pendiente;
  PermisoNotificaciones respuesta = PermisoNotificaciones.concedido;
  int solicitudes = 0;
  int consultas = 0;
  int ajustes = 0;
  FalloApi? fallo;
  Future<PermisoNotificaciones> Function()? alConsultar;
  @override
  Future<String> identificar() async {
    if (fallo != null) throw fallo!;
    return id;
  }

  @override
  Future<PermisoNotificaciones> consultarPermiso() async {
    consultas++;
    return alConsultar == null ? permiso : await alConsultar!();
  }

  @override
  Future<PermisoNotificaciones> solicitarPermiso() async {
    solicitudes++;
    return permiso = respuesta;
  }

  @override
  Future<void> abrirAjustes() async {
    ajustes++;
  }
}

Map<String, dynamic> respuestaPreferencia({
  String id = dispositivoId,
  String permiso = 'CONCEDIDO',
  bool habilitadas = false,
}) => {
  'idDispositivo': id,
  'permiso': permiso,
  'habilitadas': habilitadas,
  'actualizadaEn': '2026-11-21T22:00:00Z',
};
PreferenciaNotificaciones preferenciaPrueba({bool habilitadas = false}) =>
    PreferenciaNotificaciones.desdeJson(
      respuestaPreferencia(habilitadas: habilitadas),
      dispositivoId,
    );
