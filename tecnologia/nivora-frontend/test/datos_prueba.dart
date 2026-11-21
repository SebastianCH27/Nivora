import 'package:nivora_frontend/src/api_nivora.dart';

import 'datos_costos_prueba.dart';
import 'datos_proyeccion_prueba.dart';
import 'datos_comparacion_prueba.dart';
import 'datos_recibos_prueba.dart';
import 'datos_estadisticas_prueba.dart';
import 'datos_notificaciones_prueba.dart';

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
  int consultasHistorial = 0;
  int consultasCostos = 0;
  int consultasProyeccion = 0;
  int consultasComparacion = 0;
  int consultasRecibos = 0;
  int consultasEstadisticas = 0;
  int consultasNotificaciones = 0;
  final rangosNotificaciones =
      <({int limite, bool soloPendientes, String? antesDe})>[];
  final marcasNotificaciones = <String>[];
  Future<PaginaNotificaciones> Function(
    int limite,
    bool soloPendientes,
    String? antesDe,
  )?
  alNotificaciones;
  Future<void> Function(String id)? alMarcarNotificacion;
  final leidasNotificaciones = <String>{};
  final rangosEstadisticas = <int>[];
  Future<EstadisticasConsumo> Function(int meses)? alEstadisticas;
  final rangosRecibos = <({int limite, String? antesDe})>[];
  Future<PaginaRecibos> Function(int limite, String? antesDe)? alRecibos;
  final rangosComparacion = <({String? base, String? comparado})>[];
  Future<ComparacionConsumo> Function(String? base, String? comparado)?
  alComparar;
  Future<ProyeccionConsumo> Function()? alProyeccion;
  final rangosCostos = <int>[];
  Future<CostosConsumo> Function(int meses)? alCostos;
  final rangosHistorial = <({int meses, String? hasta})>[];
  Future<HistorialMensual> Function(int meses, String? hasta)? alHistorial;
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
  Future<HistorialMensual> historial(
    SesionConsulta sesion, {
    int meses = 12,
    String? hasta,
  }) async {
    consultasHistorial++;
    rangosHistorial.add((meses: meses, hasta: hasta));
    if (alHistorial != null) return await alHistorial!(meses, hasta);
    return HistorialMensual.desdeJson(
      respuestaHistorial(meses: meses, hasta: hasta),
      sesion.suministro,
      meses: meses,
      hasta: hasta,
    );
  }

  @override
  void cerrar() {}

  @override
  Future<CostosConsumo> costos(SesionConsulta sesion, {int meses = 3}) async {
    consultasCostos++;
    rangosCostos.add(meses);
    if (alCostos != null) return await alCostos!(meses);
    return CostosConsumo.desdeJson(
      respuestaCostos(meses: meses),
      sesion.suministro,
      meses: meses,
    );
  }

  @override
  Future<ProyeccionConsumo> proyeccion(SesionConsulta sesion) async {
    consultasProyeccion++;
    return alProyeccion == null ? proyeccionPrueba() : await alProyeccion!();
  }

  @override
  Future<ComparacionConsumo> comparar(
    SesionConsulta sesion, {
    String? periodoBase,
    String? periodoComparado,
  }) async {
    consultasComparacion++;
    rangosComparacion.add((base: periodoBase, comparado: periodoComparado));
    return alComparar == null
        ? comparacionPrueba(base: periodoBase, comparado: periodoComparado)
        : await alComparar!(periodoBase, periodoComparado);
  }

  @override
  Future<PaginaRecibos> recibos(
    SesionConsulta sesion, {
    int limite = 12,
    String? antesDe,
  }) async {
    consultasRecibos++;
    rangosRecibos.add((limite: limite, antesDe: antesDe));
    return alRecibos == null
        ? paginaRecibosPrueba(limite: limite, antesDe: antesDe)
        : await alRecibos!(limite, antesDe);
  }

  @override
  Future<PaginaNotificaciones> notificaciones(
    SesionConsulta sesion, {
    int limite = 20,
    bool soloPendientes = false,
    String? antesDe,
  }) async {
    consultasNotificaciones++;
    rangosNotificaciones.add((
      limite: limite,
      soloPendientes: soloPendientes,
      antesDe: antesDe,
    ));
    return alNotificaciones == null
        ? paginaNotificacionesPrueba(
            limite: limite,
            soloPendientes: soloPendientes,
            antesDe: antesDe,
            leidas: leidasNotificaciones,
          )
        : await alNotificaciones!(limite, soloPendientes, antesDe);
  }

  @override
  Future<void> marcarNotificacionLeida(SesionConsulta sesion, String id) async {
    marcasNotificaciones.add(id);
    await alMarcarNotificacion?.call(id);
    leidasNotificaciones.add(id);
  }

  @override
  Future<EstadisticasConsumo> estadisticas(
    SesionConsulta sesion, {
    int meses = 12,
  }) async {
    consultasEstadisticas++;
    rangosEstadisticas.add(meses);
    return alEstadisticas == null
        ? estadisticasPrueba(meses: meses)
        : await alEstadisticas!(meses);
  }
}

Map<String, dynamic> respuestaHistorial({int meses = 12, String? hasta}) {
  const actual = '2026-11';
  final ultimo = hasta ?? moverMes(actual, -1);
  return {
    'fuente': 'empresa-agua',
    'codigoSuministro': '0013100390',
    'zonaHoraria': 'America/Lima',
    'consultadoEn': '2026-11-20T18:00:00Z',
    'periodos': List.generate(meses, (i) {
      final periodo = moverMes(ultimo, -i);
      final inicio = DateTime.utc(
        int.parse(periodo.substring(0, 4)),
        int.parse(periodo.substring(5)),
        1,
        5,
      );
      final fin = DateTime.utc(inicio.year, inicio.month + 1, 1, 5);
      final estado = [
        'COMPLETO',
        'PARCIAL',
        'SIN_DATOS',
        'INCOMPATIBLE',
      ][i % 4];
      final conValor = i % 4 < 2;
      return {
        'periodo': periodo,
        'inicioMes': inicio.toIso8601String(),
        'finMesExclusivo': fin.toIso8601String(),
        'consumo': <String, dynamic>{
          'estado': estado,
          'motivo': estado == 'COMPLETO' ? null : 'LECTURAS_INSUFICIENTES',
          'litros': conValor ? (i == 0 ? '0' : '1234') : null,
          'metrosCubicos': conValor ? (i == 0 ? '0.000' : '1.234') : null,
          'desde': conValor ? inicio.toIso8601String() : null,
          'hasta': conValor
              ? (estado == 'COMPLETO'
                        ? fin
                        : fin.subtract(const Duration(days: 1)))
                    .toIso8601String()
              : null,
        },
      };
    }),
  };
}
