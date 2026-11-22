enum PermisoNotificaciones {
  concedido('CONCEDIDO', 'Concedido'),
  denegado('DENEGADO', 'Denegado'),
  pendiente('PENDIENTE', 'Pendiente');

  const PermisoNotificaciones(this.codigo, this.etiqueta);
  final String codigo;
  final String etiqueta;

  static PermisoNotificaciones leer(Object? valor) => values.firstWhere(
    (p) => p.codigo == valor,
    orElse: () => throw const FormatException(),
  );
}

bool idDispositivoValido(String id) =>
    RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
        .hasMatch(id);

class PreferenciaNotificaciones {
  const PreferenciaNotificaciones({
    required this.idDispositivo,
    required this.permiso,
    required this.habilitadas,
    required this.actualizadaEn,
  });

  factory PreferenciaNotificaciones.desdeJson(Object? valor, String id) {
    if (valor is! Map<String, dynamic> ||
        !idDispositivoValido(id) ||
        valor['idDispositivo'] != id ||
        valor['habilitadas'] is! bool ||
        valor['actualizadaEn'] is! String) {
      throw const FormatException();
    }
    final permiso = PermisoNotificaciones.leer(valor['permiso']);
    final fecha = DateTime.tryParse(valor['actualizadaEn'] as String);
    if (fecha == null ||
        !fecha.isUtc ||
        (valor['habilitadas'] == true &&
            permiso != PermisoNotificaciones.concedido)) {
      throw const FormatException();
    }
    return PreferenciaNotificaciones(
      idDispositivo: id,
      permiso: permiso,
      habilitadas: valor['habilitadas'] as bool,
      actualizadaEn: fecha,
    );
  }

  final String idDispositivo;
  final PermisoNotificaciones permiso;
  final bool habilitadas;
  final DateTime actualizadaEn;
}
