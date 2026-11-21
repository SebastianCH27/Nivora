// Los cargos emitidos se conservan como texto. Las comprobaciones y la
// conversión de litros a m³ usan enteros, sin redondear importes del recibo.
bool periodoReciboValido(String valor) =>
    RegExp(r'^[1-9][0-9]{3}-(0[1-9]|1[0-2])$').hasMatch(valor);

bool parametrosRecibosValidos(int limite, String? antesDe) =>
    limite >= 1 &&
    limite <= 60 &&
    (antesDe == null || periodoReciboValido(antesDe));

Map<String, dynamic> _objeto(Object? valor) {
  if (valor is! Map<String, dynamic>) throw const FormatException();
  return valor;
}

String _texto(Map<String, dynamic> json, String clave, int maximo) {
  final valor = json[clave];
  if (valor is! String ||
      valor.isEmpty ||
      valor.trim() != valor ||
      valor.length > maximo ||
      RegExp(r'[\x00-\x1f\x7f-\x9f]').hasMatch(valor)) {
    throw const FormatException();
  }
  return valor;
}

DateTime _instante(Object? valor) {
  if (valor is! String) throw const FormatException();
  final fecha = DateTime.parse(valor);
  if (!fecha.isUtc) throw const FormatException();
  return fecha;
}

DateTime _fechaCivil(Object? valor) {
  if (valor is! String ||
      !RegExp(r'^[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}$').hasMatch(valor)) {
    throw const FormatException();
  }
  final fecha = DateTime.parse('${valor}T00:00:00Z');
  if (fecha.toIso8601String().substring(0, 10) != valor) {
    throw const FormatException();
  }
  return fecha;
}

BigInt _fijo(String valor, int escala) {
  final partes = valor.split('.');
  return BigInt.parse(partes.first) * BigInt.from(10).pow(escala) +
      BigInt.parse(partes.length == 1 ? '0' : partes[1].padRight(escala, '0'));
}

String _decimal(Object? valor, int escala, int enteros) {
  if (valor is! String ||
      !RegExp('^[0-9]+(?:\\.[0-9]{1,$escala})?\$').hasMatch(valor) ||
      _fijo(valor, escala) >= BigInt.from(10).pow(enteros + escala)) {
    throw const FormatException();
  }
  return valor;
}

String _sinCerosFinales(String valor, {int minimo = 0}) {
  final partes = valor.split('.');
  if (partes.length == 1) return valor;
  var fraccion = partes[1];
  while (fraccion.length > minimo && fraccion.endsWith('0')) {
    fraccion = fraccion.substring(0, fraccion.length - 1);
  }
  return fraccion.isEmpty ? partes.first : '${partes.first}.$fraccion';
}

class VentanaRecibo {
  const VentanaRecibo({required this.inicio, required this.fin});

  factory VentanaRecibo.desdeJson(
    Object? valor,
    String periodo, {
    bool civil = false,
  }) {
    final json = _objeto(valor);
    final inicio = _instante(json['inicio']);
    final fin = _instante(json['finExclusivo']);
    final primero = DateTime.utc(
      int.parse(periodo.substring(0, 4)),
      int.parse(periodo.substring(5)),
      1,
      5,
    );
    final siguiente = DateTime.utc(primero.year, primero.month + 1, 1, 5);
    bool corte(DateTime fecha, DateTime mes) =>
        fecha.year == mes.year &&
        fecha.month == mes.month &&
        fecha.hour == 5 &&
        fecha.minute == 0 &&
        fecha.second == 0 &&
        fecha.millisecond == 0 &&
        fecha.microsecond == 0;
    if (!inicio.isBefore(fin) ||
        !corte(inicio, primero) ||
        !corte(fin, siguiente) ||
        (civil && (inicio != primero || fin != siguiente))) {
      throw const FormatException();
    }
    return VentanaRecibo(inicio: inicio, fin: fin);
  }

  final DateTime inicio;
  final DateTime fin;
}

class ReciboEmitido {
  const ReciboEmitido({
    required this.numero,
    required this.periodo,
    required this.consumoLitros,
    required this.cargoFijo,
    required this.importeConsumo,
    required this.importeTotal,
    required this.disponibleDesde,
    required this.vencimiento,
    required this.codigoTarifa,
    required this.versionTarifa,
    required this.recibidoEn,
    required this.facturado,
    required this.analisis,
    required this.coincideConAnalisis,
  });

  factory ReciboEmitido.desdeJson(Object? valor) {
    final json = _objeto(valor);
    final periodo = json['periodo'];
    final version = json['versionTarifa'];
    final coincide = json['coincideConPeriodoAnalisis'];
    if (periodo is! String ||
        !periodoReciboValido(periodo) ||
        version is! int ||
        version < 1 ||
        coincide is! bool) {
      throw const FormatException();
    }
    final facturado = VentanaRecibo.desdeJson(
      json['periodoFacturado'],
      periodo,
      civil: true,
    );
    final analisis = VentanaRecibo.desdeJson(json['periodoAnalisis'], periodo);
    final disponible = _fechaCivil(json['fechaDisponibilidad']);
    final vencimiento = _fechaCivil(json['fechaVencimiento']);
    final despuesDelMes = DateTime.utc(
      facturado.fin.year,
      facturado.fin.month,
      1,
    );
    final fijo = _decimal(json['cargoFijo'], 2, 10);
    final consumo = _decimal(json['importeConsumo'], 2, 10);
    final total = _decimal(json['importeTotal'], 2, 10);
    if (disponible.isBefore(despuesDelMes) ||
        vencimiento.isBefore(disponible) ||
        _fijo(fijo, 2) + _fijo(consumo, 2) != _fijo(total, 2) ||
        coincide !=
            (facturado.inicio == analisis.inicio &&
                facturado.fin == analisis.fin)) {
      throw const FormatException();
    }
    return ReciboEmitido(
      numero: _texto(json, 'numeroRecibo', 40),
      periodo: periodo,
      consumoLitros: _decimal(json['consumoLitros'], 3, 12),
      cargoFijo: fijo,
      importeConsumo: consumo,
      importeTotal: total,
      disponibleDesde: disponible,
      vencimiento: vencimiento,
      codigoTarifa: _texto(json, 'codigoTarifa', 30),
      versionTarifa: version,
      recibidoEn: _instante(json['recibidoEn']),
      facturado: facturado,
      analisis: analisis,
      coincideConAnalisis: coincide,
    );
  }

  final String numero;
  final String periodo;
  final String consumoLitros;
  final String cargoFijo;
  final String importeConsumo;
  final String importeTotal;
  final DateTime disponibleDesde;
  final DateTime vencimiento;
  final String codigoTarifa;
  final int versionTarifa;
  final DateTime recibidoEn;
  final VentanaRecibo facturado;
  final VentanaRecibo analisis;
  final bool coincideConAnalisis;

  String get litrosParaMostrar => _sinCerosFinales(consumoLitros);
  String get metrosCubicos {
    final mililitros = _fijo(consumoLitros, 3);
    final escala = BigInt.from(1000000);
    return _sinCerosFinales(
      '${mililitros ~/ escala}.${(mililitros % escala).toString().padLeft(6, '0')}',
      minimo: 3,
    );
  }
}

class PaginaRecibos {
  const PaginaRecibos({required this.recibos, required this.siguienteAntesDe});

  factory PaginaRecibos.desdeJson(
    Object? valor,
    String suministro, {
    int limite = 12,
    String? antesDe,
  }) {
    final json = _objeto(valor);
    final lista = json['recibos'];
    final hayMas = json['hayMas'];
    final siguiente = json['siguienteAntesDe'];
    if (!parametrosRecibosValidos(limite, antesDe) ||
        json['codigoSuministro'] != suministro ||
        json['moneda'] != 'PEN' ||
        json['zonaFechas'] != 'America/Lima' ||
        lista is! List ||
        lista.length > limite ||
        hayMas is! bool) {
      throw const FormatException();
    }
    final recibos = lista.map(ReciboEmitido.desdeJson).toList();
    final numeros = <String>{};
    String? anterior = antesDe;
    for (final recibo in recibos) {
      if ((anterior != null && recibo.periodo.compareTo(anterior) >= 0) ||
          !numeros.add(recibo.numero)) {
        throw const FormatException();
      }
      anterior = recibo.periodo;
    }
    if (hayMas
        ? (recibos.length != limite || siguiente != recibos.last.periodo)
        : siguiente != null) {
      throw const FormatException();
    }
    return PaginaRecibos(
      recibos: List.unmodifiable(recibos),
      siguienteAntesDe: hayMas ? siguiente as String : null,
    );
  }

  final List<ReciboEmitido> recibos;
  final String? siguienteAntesDe;
  bool get hayMas => siguienteAntesDe != null;
}
