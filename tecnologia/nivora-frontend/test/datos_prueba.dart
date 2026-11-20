import 'package:nivora_frontend/src/api_nivora.dart';

Map<String, dynamic> respuestaSesion({String suministro = '0013100390'}) => {
  'tipoToken': 'Bearer',
  'token': 'nc_${'a' * 43}',
  'sesion': {
    'idSesion': 'sesion-prueba',
    'codigoSuministro': suministro,
    'nombre': 'Persona de prueba',
    'rol': 'CONSULTA',
    'creadaEn': DateTime.now().toUtc().toIso8601String(),
    'expiraEn': DateTime.now()
        .toUtc()
        .add(const Duration(minutes: 30))
        .toIso8601String(),
  },
};

Map<String, dynamic> respuestaConsumo({String estado = 'PARCIAL'}) => {
  'codigoSuministro': '0013100390',
  'periodo': '2026-11',
  'zonaHoraria': 'America/Lima',
  'consumo': {
    'estado': estado,
    'motivo': 'FALTA_LECTURA_INICIAL',
    'litros': estado == 'PARCIAL' ? '1250' : null,
    'metrosCubicos': estado == 'PARCIAL' ? '1.250' : null,
    'desde': '2026-11-02T05:00:00Z',
    'hasta': '2026-11-19T18:00:00Z',
  },
  'actualizacion': {
    'estado': 'PENDIENTE',
    'ultimaLectura': '2026-11-19T18:00:00Z',
    'confirmadoHasta': null,
  },
};

SesionConsulta sesionPrueba() => SesionConsulta.desdeJson(respuestaSesion());
ConsumoActual consumoPrueba() =>
    ConsumoActual.desdeJson(respuestaConsumo(), '0013100390');

class ServicioPrueba implements ServicioNivora {
  int ingresos = 0;
  int consultas = 0;
  int salidas = 0;
  Future<SesionConsulta> Function()? alIngresar;
  Future<ConsumoActual> Function()? alConsultar;
  Future<void> Function()? alSalir;

  @override
  Future<SesionConsulta> ingresar(String suministro, String dni) async {
    ingresos++;
    return alIngresar == null ? sesionPrueba() : await alIngresar!();
  }

  @override
  Future<ConsumoActual> consumo(SesionConsulta sesion) async {
    consultas++;
    return alConsultar == null ? consumoPrueba() : await alConsultar!();
  }

  @override
  Future<void> salir(SesionConsulta sesion) async {
    salidas++;
    await alSalir?.call();
  }

  @override
  void cerrar() {}
}
