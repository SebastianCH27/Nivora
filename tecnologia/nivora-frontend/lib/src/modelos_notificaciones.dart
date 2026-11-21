Map<String, dynamic> _objeto(Object? valor) {
  if (valor is! Map<String, dynamic>) throw const FormatException();
  return valor;
}

String _texto(Map<String, dynamic> json, String clave) {
  final valor = json[clave];
  if (valor is! String || valor.trim().isEmpty) throw const FormatException();
  return valor;
}

final _periodo = RegExp(r'^[1-9][0-9]{3}-(0[1-9]|1[0-2])$');
final _id = RegExp(
  r'^(ALERTA_CONSUMO|RECIBO_DISPONIBLE)-([1-9][0-9]{3}-(0[1-9]|1[0-2]))$',
);

bool idNotificacionValido(String valor) => _id.hasMatch(valor);

DateTime _instante(String valor) {
  if (!RegExp(
    r'^[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]{1,6})?Z$',
  ).hasMatch(valor)) {
    throw const FormatException();
  }
  final fecha = DateTime.tryParse(valor);
  if (fecha == null ||
      fecha.toIso8601String().substring(0, 19) != valor.substring(0, 19)) {
    throw const FormatException();
  }
  return fecha;
}

DateTime _calendario(String valor) {
  if (!RegExp(r'^[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}$').hasMatch(valor)) {
    throw const FormatException();
  }
  final fecha = DateTime.tryParse('${valor}T00:00:00Z');
  if (fecha == null || fecha.toIso8601String().substring(0, 10) != valor) {
    throw const FormatException();
  }
  return fecha;
}

({DateTime fecha, String id}) _cursor(String valor) {
  final partes = valor.split('~');
  if (partes.length != 2 || !idNotificacionValido(partes[1])) {
    throw const FormatException();
  }
  return (fecha: _instante(partes[0]), id: partes[1]);
}

bool parametrosNotificacionesValidos(int limite, String? antesDe) {
  if (limite < 1 || limite > 60) return false;
  try {
    if (antesDe != null) _cursor(antesDe);
    return true;
  } on FormatException {
    return false;
  }
}

int _orden(DateTime fechaA, String idA, DateTime fechaB, String idB) {
  final porFecha = fechaA.compareTo(fechaB);
  return porFecha != 0 ? porFecha : idA.compareTo(idB);
}

class ConsumoNotificacion {
  const ConsumoNotificacion(this.actualLitros, this.anteriorLitros);
  final String actualLitros;
  final String anteriorLitros;

  factory ConsumoNotificacion.desdeJson(Object? valor) {
    final json = _objeto(valor);
    final actual = _texto(json, 'actualLitros');
    final anterior = _texto(json, 'anteriorLitros');
    final entero = RegExp(r'^[0-9]+$');
    if (!entero.hasMatch(actual) ||
        !entero.hasMatch(anterior) ||
        BigInt.parse(actual) <= BigInt.parse(anterior)) {
      throw const FormatException();
    }
    return ConsumoNotificacion(actual, anterior);
  }
}

class ReciboNotificacion {
  const ReciboNotificacion(this.numero, this.disponibleDesde, this.vencimiento);
  final String numero;
  final DateTime disponibleDesde;
  final DateTime vencimiento;

  factory ReciboNotificacion.desdeJson(Object? valor) {
    final json = _objeto(valor);
    return ReciboNotificacion(
      _texto(json, 'numeroRecibo'),
      _calendario(_texto(json, 'fechaDisponibilidad')),
      _calendario(_texto(json, 'fechaVencimiento')),
    );
  }
}

class NotificacionConsulta {
  const NotificacionConsulta({
    required this.id,
    required this.tipo,
    required this.periodo,
    required this.titulo,
    required this.registradaEn,
    required this.leida,
    required this.leidaEn,
    required this.consumo,
    required this.recibo,
  });

  factory NotificacionConsulta.desdeJson(Object? valor) {
    final json = _objeto(valor);
    final id = _texto(json, 'id');
    final tipo = _texto(json, 'tipo');
    final periodo = _texto(json, 'periodo');
    final registrada = _instante(_texto(json, 'registradaEn'));
    final leida = json['leida'];
    final leidaEn = json['leidaEn'] == null
        ? null
        : _instante(_texto(json, 'leidaEn'));
    if (!idNotificacionValido(id) ||
        !_periodo.hasMatch(periodo) ||
        id != '$tipo-$periodo' ||
        leida is! bool ||
        leida != (leidaEn != null) ||
        (leidaEn != null && leidaEn.isBefore(registrada))) {
      throw const FormatException();
    }
    final esAlerta = tipo == 'ALERTA_CONSUMO';
    if ((esAlerta && json['recibo'] != null) ||
        (!esAlerta && json['consumo'] != null)) {
      throw const FormatException();
    }
    return NotificacionConsulta(
      id: id,
      tipo: tipo,
      periodo: periodo,
      titulo: _texto(json, 'titulo'),
      registradaEn: registrada,
      leida: leida,
      leidaEn: leidaEn,
      consumo: esAlerta ? ConsumoNotificacion.desdeJson(json['consumo']) : null,
      recibo: esAlerta ? null : ReciboNotificacion.desdeJson(json['recibo']),
    );
  }

  final String id;
  final String tipo;
  final String periodo;
  final String titulo;
  final DateTime registradaEn;
  final bool leida;
  final DateTime? leidaEn;
  final ConsumoNotificacion? consumo;
  final ReciboNotificacion? recibo;

  // El PUT confirma la lectura, pero no devuelve su fecha. Se recupera al
  // actualizar; nunca se sustituye por la hora del teléfono.
  NotificacionConsulta comoLeida() => NotificacionConsulta(
    id: id,
    tipo: tipo,
    periodo: periodo,
    titulo: titulo,
    registradaEn: registradaEn,
    leida: true,
    leidaEn: leidaEn,
    consumo: consumo,
    recibo: recibo,
  );
}

class PaginaNotificaciones {
  PaginaNotificaciones({
    required List<NotificacionConsulta> avisos,
    required this.siguienteAntesDe,
  }) : avisos = List.unmodifiable(avisos);

  factory PaginaNotificaciones.desdeJson(
    Object? valor,
    String suministro, {
    int limite = 20,
    bool soloPendientes = false,
    String? antesDe,
  }) {
    final json = _objeto(valor);
    final lista = json['notificaciones'];
    final hayMas = json['hayMas'];
    final siguiente = json['siguienteAntesDe'];
    if (!parametrosNotificacionesValidos(limite, antesDe) ||
        json['codigoSuministro'] != suministro ||
        lista is! List ||
        lista.length > limite ||
        hayMas is! bool ||
        (siguiente != null && siguiente is! String) ||
        hayMas != (siguiente != null)) {
      throw const FormatException();
    }
    final avisos = lista.map(NotificacionConsulta.desdeJson).toList();
    final ids = <String>{};
    final corte = antesDe == null ? null : _cursor(antesDe);
    for (var i = 0; i < avisos.length; i++) {
      final aviso = avisos[i];
      if (!ids.add(aviso.id) ||
          (soloPendientes && aviso.leida) ||
          (corte != null &&
              _orden(aviso.registradaEn, aviso.id, corte.fecha, corte.id) >=
                  0) ||
          (i > 0 &&
              _orden(
                    avisos[i - 1].registradaEn,
                    avisos[i - 1].id,
                    aviso.registradaEn,
                    aviso.id,
                  ) <=
                  0)) {
        throw const FormatException();
      }
    }
    if (siguiente != null) {
      final cursor = _cursor(siguiente as String);
      if (avisos.length != limite ||
          cursor.id != avisos.last.id ||
          cursor.fecha != avisos.last.registradaEn) {
        throw const FormatException();
      }
    }
    return PaginaNotificaciones(
      avisos: avisos,
      siguienteAntesDe: siguiente as String?,
    );
  }

  final List<NotificacionConsulta> avisos;
  final String? siguienteAntesDe;
  bool get hayMas => siguienteAntesDe != null;
}

String metrosCubicosNotificacion(String litros) {
  final cantidad = BigInt.parse(litros);
  return '${cantidad ~/ BigInt.from(1000)},'
      '${(cantidad % BigInt.from(1000)).toString().padLeft(3, '0')}';
}
