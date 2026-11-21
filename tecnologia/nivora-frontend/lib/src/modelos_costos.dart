// Los importes del servidor se conservan como texto y se validan con enteros.
// El frontend no vuelve a calcular tarifas, redondeos ni impuestos.
Map<String, dynamic> _objeto(Object? valor) {
  if (valor is! Map<String, dynamic>) throw const FormatException();
  return valor;
}

String _texto(Map<String, dynamic> json, String clave) {
  final valor = json[clave];
  if (valor is! String || valor.trim().isEmpty) throw const FormatException();
  return valor;
}

DateTime _instante(Map<String, dynamic> json, String clave) {
  final valor = DateTime.parse(_texto(json, clave));
  if (!valor.isUtc) throw const FormatException();
  return valor;
}

String _decimal(Object? valor, {required int escala}) {
  if (valor is! String ||
      !RegExp('^[0-9]+(?:\\.[0-9]{1,$escala})?\$').hasMatch(valor)) {
    throw const FormatException();
  }
  return valor;
}

BigInt _entero(String valor, int escala) {
  final partes = valor.split('.');
  return BigInt.parse(partes[0]) * BigInt.from(10).pow(escala) +
      BigInt.parse(partes.length == 1 ? '0' : partes[1].padRight(escala, '0'));
}

String? _revision(Object? valor) {
  if (valor == null) return null;
  if (valor is! String || !RegExp(r'^[1-9][0-9]*$').hasMatch(valor)) {
    throw const FormatException();
  }
  return valor;
}

DateTime _fechaLocal(String valor) {
  if (!RegExp(r'^[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}$').hasMatch(valor)) {
    throw const FormatException();
  }
  final fecha = DateTime.parse('${valor}T00:00:00Z');
  if (fecha.toIso8601String().substring(0, 10) != valor) {
    throw const FormatException();
  }
  return fecha;
}

class TarifaCosto {
  const TarifaCosto({
    required this.codigo,
    required this.version,
    required this.nombre,
    required this.desde,
    required this.hasta,
    required this.igvPorcentaje,
    required this.fuente,
    required this.revision,
  });

  factory TarifaCosto.desdeJson(Object? valor, DateTime inicio, DateTime fin) {
    final json = _objeto(valor);
    final version = json['versionTarifa'];
    final desde = _fechaLocal(_texto(json, 'aplicableDesde'));
    final hasta = json['aplicableHasta'] == null
        ? null
        : _fechaLocal(_texto(json, 'aplicableHasta'));
    final fuente = _texto(json, 'fuenteTarifaria');
    final igv = json['igvPorcentaje'] == null
        ? null
        : _decimal(json['igvPorcentaje'], escala: 4);
    // Las vigencias son fechas de Lima y el extremo final es exclusivo.
    final inicioLocal = DateTime.utc(inicio.year, inicio.month, inicio.day);
    final finLocal = DateTime.utc(fin.year, fin.month, fin.day);
    if (version is! int ||
        version < 1 ||
        !{'empresa-agua', 'nivora'}.contains(fuente) ||
        desde.isAfter(inicioLocal) ||
        (hasta != null && hasta.isBefore(finLocal)) ||
        (igv != null && _entero(igv, 4) > BigInt.from(1000000))) {
      throw const FormatException();
    }
    return TarifaCosto(
      codigo: _texto(json, 'codigoTarifa'),
      version: version,
      nombre: _texto(json, 'nombreTarifa'),
      desde: desde,
      hasta: hasta,
      igvPorcentaje: igv,
      fuente: fuente,
      revision: _revision(json['revisionTarifaria']),
    );
  }

  final String codigo;
  final int version;
  final String nombre;
  final DateTime desde;
  final DateTime? hasta;
  final String? igvPorcentaje;
  final String fuente;
  final String? revision;
}

class DesgloseCosto {
  const DesgloseCosto({
    required this.agua,
    required this.alcantarillado,
    required this.cargoFijo,
    required this.subtotalConocido,
    required this.igv,
    required this.totalEstimado,
  });

  factory DesgloseCosto.desdeJson(
    Object? valor,
    String estado,
    TarifaCosto tarifa,
  ) {
    final json = _objeto(valor);
    String importe(String clave) => _decimal(json[clave], escala: 2);
    String? opcional(String clave) =>
        json[clave] == null ? null : importe(clave);
    final agua = importe('agua');
    final alcantarillado = opcional('alcantarillado');
    final fijo = importe('cargoFijo');
    final subtotal = importe('subtotalConocido');
    final igv = opcional('igv');
    final total = opcional('totalEstimado');
    final subtotalEsperado =
        _entero(agua, 2) +
        _entero(fijo, 2) +
        (alcantarillado == null ? BigInt.zero : _entero(alcantarillado, 2));
    if (_entero(subtotal, 2) != subtotalEsperado) throw const FormatException();
    if (estado == 'ESTIMADO') {
      if (alcantarillado == null ||
          tarifa.igvPorcentaje == null ||
          igv == null ||
          total == null ||
          _entero(total, 2) != _entero(subtotal, 2) + _entero(igv, 2)) {
        throw const FormatException();
      }
    } else if (total != null ||
        igv != null ||
        (alcantarillado != null && tarifa.igvPorcentaje != null)) {
      throw const FormatException();
    }
    return DesgloseCosto(
      agua: agua,
      alcantarillado: alcantarillado,
      cargoFijo: fijo,
      subtotalConocido: subtotal,
      igv: igv,
      totalEstimado: total,
    );
  }

  final String agua;
  final String? alcantarillado;
  final String cargoFijo;
  final String subtotalConocido;
  final String? igv;
  final String? totalEstimado;
}

class PeriodoCosto {
  const PeriodoCosto({
    required this.periodo,
    required this.estado,
    required this.cobertura,
    required this.litros,
    required this.metrosCubicos,
    required this.desde,
    required this.hasta,
    required this.tarifa,
    required this.desglose,
    required this.enCurso,
  });

  factory PeriodoCosto.desdeJson(
    Object? valor,
    String periodoEsperado,
    DateTime consultadoEn, {
    required bool enCurso,
    DateTime? inicioPeriodo,
    DateTime? finPeriodo,
  }) {
    final json = _objeto(valor);
    if (json['periodo'] != periodoEsperado ||
        !RegExp(r'^[1-9][0-9]{3}-(0[1-9]|1[0-2])$').hasMatch(periodoEsperado)) {
      throw const FormatException();
    }
    final primero = DateTime.utc(
      int.parse(periodoEsperado.substring(0, 4)),
      int.parse(periodoEsperado.substring(5)),
      1,
      5,
    );
    final siguiente = DateTime.utc(primero.year, primero.month + 1, 1, 5);
    final inicio = inicioPeriodo ?? primero;
    final fin = finPeriodo ?? siguiente;
    bool corte(DateTime fecha, DateTime mes) =>
        fecha.isUtc &&
        fecha.year == mes.year &&
        fecha.month == mes.month &&
        fecha.hour == 5 &&
        fecha.minute == 0 &&
        fecha.second == 0 &&
        fecha.millisecond == 0 &&
        fecha.microsecond == 0;
    if ((inicioPeriodo == null) != (finPeriodo == null) ||
        !corte(inicio, primero) ||
        !corte(fin, siguiente) ||
        !inicio.isBefore(fin)) {
      throw const FormatException();
    }
    if (_instante(json, 'inicioMes') != inicio ||
        _instante(json, 'finMesExclusivo') != fin) {
      throw const FormatException();
    }
    final estado = _texto(json, 'estado');
    final consumo = _objeto(json['consumo']);
    final cobertura = _texto(consumo, 'estado');
    if (!{
          'ESTIMADO',
          'CONCEPTOS_INCOMPLETOS',
          'SIN_CONSUMO_CALCULABLE',
          'SIN_TARIFA_COMPATIBLE',
        }.contains(estado) ||
        !{
          'COMPLETO',
          'PARCIAL',
          'SIN_DATOS',
          'INCOMPATIBLE',
        }.contains(cobertura)) {
      throw const FormatException();
    }
    final conConsumo = cobertura == 'COMPLETO' || cobertura == 'PARCIAL';
    final litros = consumo['litros'];
    final m3 = consumo['metrosCubicos'];
    final desde = consumo['desde'] == null ? null : _instante(consumo, 'desde');
    final hasta = consumo['hasta'] == null ? null : _instante(consumo, 'hasta');
    if (conConsumo) {
      if (litros is! String ||
          !RegExp(r'^[0-9]+$').hasMatch(litros) ||
          _entero(_decimal(m3, escala: 3), 3) != BigInt.parse(litros) ||
          desde == null ||
          hasta == null ||
          desde.isBefore(inicio) ||
          hasta.isAfter(fin) ||
          hasta.isAfter(consultadoEn) ||
          desde.isAfter(hasta) ||
          (cobertura == 'COMPLETO' &&
              (desde != inicio || (!enCurso && hasta != fin))) ||
          estado == 'SIN_CONSUMO_CALCULABLE') {
        throw const FormatException();
      }
    } else if (litros != null ||
        m3 != null ||
        desde != null ||
        hasta != null ||
        estado != 'SIN_CONSUMO_CALCULABLE') {
      throw const FormatException();
    }
    TarifaCosto? tarifa;
    DesgloseCosto? desglose;
    if (estado == 'ESTIMADO' || estado == 'CONCEPTOS_INCOMPLETOS') {
      tarifa = TarifaCosto.desdeJson(json['tarifa'], inicio, fin);
      desglose = DesgloseCosto.desdeJson(json['desglose'], estado, tarifa);
    } else if (json['tarifa'] != null || json['desglose'] != null) {
      throw const FormatException();
    }
    return PeriodoCosto(
      periodo: periodoEsperado,
      estado: estado,
      cobertura: cobertura,
      litros: conConsumo ? litros as String : null,
      metrosCubicos: conConsumo ? m3 as String : null,
      desde: desde,
      hasta: hasta,
      tarifa: tarifa,
      desglose: desglose,
      enCurso: enCurso,
    );
  }

  final String periodo;
  final String estado;
  final String cobertura;
  final String? litros;
  final String? metrosCubicos;
  final DateTime? desde;
  final DateTime? hasta;
  final TarifaCosto? tarifa;
  final DesgloseCosto? desglose;
  final bool enCurso;
}

class CostosConsumo {
  const CostosConsumo({
    required this.consultadoEn,
    required this.actual,
    required this.anteriores,
    required this.revisionTarifaria,
    required this.revisionAdministrativa,
  });

  factory CostosConsumo.desdeJson(
    Object? valor,
    String suministro, {
    required int meses,
  }) {
    final json = _objeto(valor);
    final consultadoEn = _instante(json, 'consultadoEn');
    final lima = consultadoEn.subtract(const Duration(hours: 5));
    if (meses < 1 ||
        meses > 12 ||
        lima.year < 1000 ||
        lima.year > 9999 ||
        json['codigoSuministro'] != suministro ||
        json['fuente'] != 'empresa-agua' ||
        json['moneda'] != 'PEN' ||
        json['zonaHoraria'] != 'America/Lima') {
      throw const FormatException();
    }
    String mes(int desplazamiento) {
      final fecha = DateTime.utc(lima.year, lima.month + desplazamiento);
      return '${fecha.year.toString().padLeft(4, '0')}-'
          '${fecha.month.toString().padLeft(2, '0')}';
    }

    final anteriores = json['anteriores'];
    if (anteriores is! List || anteriores.length != meses) {
      throw const FormatException();
    }
    return CostosConsumo(
      consultadoEn: consultadoEn,
      revisionTarifaria: _revision(json['revisionTarifaria']),
      revisionAdministrativa: _revision(
        json['revisionTarifariaAdministrativa'],
      ),
      actual: PeriodoCosto.desdeJson(
        json['actual'],
        mes(0),
        consultadoEn,
        enCurso: true,
      ),
      anteriores: List.unmodifiable(
        List.generate(
          meses,
          (i) => PeriodoCosto.desdeJson(
            anteriores[i],
            mes(-i - 1),
            consultadoEn,
            enCurso: false,
          ),
        ),
      ),
    );
  }

  final DateTime consultadoEn;
  final PeriodoCosto actual;
  final List<PeriodoCosto> anteriores;
  final String? revisionTarifaria;
  final String? revisionAdministrativa;
}
