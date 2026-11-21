// Se conservan las cantidades del servidor como texto. Las unidades y el
// resumen se comprueban con enteros, incluido el promedio con HALF_UP.
Map<String, dynamic> _objeto(Object? valor) {
  if (valor is! Map<String, dynamic>) throw const FormatException();
  return valor;
}

String _texto(Map<String, dynamic> json, String clave) {
  final valor = json[clave];
  if (valor is! String || valor.trim().isEmpty) throw const FormatException();
  return valor;
}

DateTime _fecha(Object? valor) {
  if (valor is! String) throw const FormatException();
  final fecha = DateTime.parse(valor);
  if (!fecha.isUtc) throw const FormatException();
  return fecha;
}

bool _periodoValido(String valor) =>
    RegExp(r'^[1-9][0-9]{3}-(0[1-9]|1[0-2])$').hasMatch(valor);
int _indice(String periodo) =>
    int.parse(periodo.substring(0, 4)) * 12 +
    int.parse(periodo.substring(5)) -
    1;
String _mes(String periodo, int movimiento) {
  final indice = _indice(periodo) + movimiento;
  return '${(indice ~/ 12).toString().padLeft(4, '0')}-${(indice % 12 + 1).toString().padLeft(2, '0')}';
}

String _decimal(Object? valor, int escala, {bool signo = false}) {
  if (valor is! String ||
      !RegExp('^${signo ? '-?' : ''}[0-9]+(?:\\.[0-9]{1,$escala})?\$')
          .hasMatch(valor)) {
    throw const FormatException();
  }
  return valor;
}

BigInt _fijo(String valor, int escala) {
  final negativo = valor.startsWith('-');
  final partes = (negativo ? valor.substring(1) : valor).split('.');
  final numero =
      BigInt.parse(partes.first) * BigInt.from(10).pow(escala) +
      BigInt.parse(partes.length == 1 ? '0' : partes[1].padRight(escala, '0'));
  return negativo ? -numero : numero;
}

class CantidadEstadistica {
  const CantidadEstadistica({
    required this.litros,
    required this.metrosCubicos,
  });

  factory CantidadEstadistica.desdeJson(Object? valor, {bool signo = false}) {
    final json = _objeto(valor);
    final litros = _decimal(json['litros'], 3, signo: signo);
    final m3 = _decimal(json['metrosCubicos'], 6, signo: signo);
    if (_fijo(litros, 3) != _fijo(m3, 6)) throw const FormatException();
    return CantidadEstadistica(litros: litros, metrosCubicos: m3);
  }

  final String litros;
  final String metrosCubicos;
  BigInt get milesimasLitro => _fijo(litros, 3);
}

class ConsumoEstadistico {
  const ConsumoEstadistico({
    required this.estado,
    required this.motivo,
    required this.tipoMedicion,
    required this.litros,
    required this.metrosCubicos,
    required this.desde,
    required this.hasta,
  });

  factory ConsumoEstadistico.desdeJson(
    Object? valor,
    DateTime inicio,
    DateTime fin,
    DateTime consultadoEn, {
    required bool cerrado,
  }) {
    final json = _objeto(valor);
    final estado = _texto(json, 'estado');
    final motivo = json['motivo'];
    final tipo = json['tipoMedicion'];
    if (!{
          'COMPLETO',
          'PARCIAL',
          'SIN_DATOS',
          'INCOMPATIBLE',
        }.contains(estado) ||
        (motivo != null && motivo is! String) ||
        (tipo != null && tipo is! String)) {
      throw const FormatException();
    }
    final calculable = estado == 'COMPLETO' || estado == 'PARCIAL';
    final litros = json['litros'];
    final m3 = json['metrosCubicos'];
    final desde = json['desde'] == null ? null : _fecha(json['desde']);
    final hasta = json['hasta'] == null ? null : _fecha(json['hasta']);
    if (calculable) {
      if (litros is! String ||
          !RegExp(r'^[0-9]+$').hasMatch(litros) ||
          _fijo(_decimal(m3, 3), 3) != BigInt.parse(litros) ||
          desde == null ||
          hasta == null ||
          desde.isBefore(inicio) ||
          hasta.isAfter(fin) ||
          hasta.isAfter(consultadoEn) ||
          desde.isAfter(hasta) ||
          (estado == 'COMPLETO' &&
              (desde != inicio || (cerrado && hasta != fin)))) {
        throw const FormatException();
      }
    } else if (litros != null || m3 != null || desde != null || hasta != null) {
      throw const FormatException();
    }
    return ConsumoEstadistico(
      estado: estado,
      motivo: motivo as String?,
      tipoMedicion: tipo as String?,
      litros: calculable ? litros as String : null,
      metrosCubicos: calculable ? m3 as String : null,
      desde: desde,
      hasta: hasta,
    );
  }

  final String estado;
  final String? motivo;
  final String? tipoMedicion;
  final String? litros;
  final String? metrosCubicos;
  final DateTime? desde;
  final DateTime? hasta;
}

class MesEstadistico {
  const MesEstadistico({
    required this.periodo,
    required this.inicio,
    required this.fin,
    required this.consumo,
  });

  factory MesEstadistico.desdeJson(Object? valor, DateTime consultadoEn) {
    final json = _objeto(valor);
    final periodo = _texto(json, 'periodo');
    if (!_periodoValido(periodo)) throw const FormatException();
    final inicio = _fecha(json['inicioMes']);
    final fin = _fecha(json['finMesExclusivo']);
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
    if (!corte(inicio, primero) ||
        !corte(fin, siguiente) ||
        !inicio.isBefore(fin) ||
        fin.isAfter(consultadoEn)) {
      throw const FormatException();
    }
    return MesEstadistico(
      periodo: periodo,
      inicio: inicio,
      fin: fin,
      consumo: ConsumoEstadistico.desdeJson(
        json['consumo'],
        inicio,
        fin,
        consultadoEn,
        cerrado: true,
      ),
    );
  }

  final String periodo;
  final DateTime inicio;
  final DateTime fin;
  final ConsumoEstadistico consumo;
}

void _duracion(Map<String, dynamic> json, ConsumoEstadistico consumo) {
  if (consumo.desde == null) {
    if (json['duracionSegundos'] != null) throw const FormatException();
    return;
  }
  final nanosegundos =
      BigInt.from(consumo.hasta!.difference(consumo.desde!).inMicroseconds) *
      BigInt.from(1000);
  if (_fijo(_decimal(json['duracionSegundos'], 9), 9) != nanosegundos) {
    throw const FormatException();
  }
}

class EstadisticasConsumo {
  const EstadisticasConsumo({
    required this.consultadoEn,
    required this.periodoActual,
    required this.serie,
    required this.conteos,
    required this.total,
    required this.promedio,
    required this.minimo,
    required this.maximo,
  });

  factory EstadisticasConsumo.desdeJson(
    Object? valor,
    String suministro, {
    required int meses,
  }) {
    final json = _objeto(valor);
    final consultadoEn = _fecha(json['consultadoEn']);
    final comparacion = _objeto(json['comparacion']);
    final actual = _objeto(comparacion['actual']);
    final periodoActual = _texto(actual, 'periodo');
    final lista = json['serieMensual'];
    if (meses < 1 ||
        meses > 12 ||
        json['fuente'] != 'empresa-agua' ||
        json['codigoSuministro'] != suministro ||
        json['zonaHoraria'] != 'America/Lima' ||
        !_periodoValido(periodoActual) ||
        lista is! List ||
        lista.length != meses) {
      throw const FormatException();
    }
    final lima = consultadoEn.subtract(const Duration(hours: 5));
    final distanciaActual =
        lima.year * 12 + lima.month - 1 - _indice(periodoActual);
    if (distanciaActual < 0 || distanciaActual > 1) {
      throw const FormatException();
    }
    final serie = lista
        .map((m) => MesEstadistico.desdeJson(m, consultadoEn))
        .toList();
    for (var i = 0; i < serie.length; i++) {
      if (serie[i].periodo != _mes(periodoActual, i - meses) ||
          (i > 0 && serie[i - 1].fin != serie[i].inicio)) {
        throw const FormatException();
      }
    }
    final ultimo = serie.last;
    final anterior = _objeto(comparacion['anterior']);
    final consumoAnterior = ConsumoEstadistico.desdeJson(
      anterior['consumo'],
      ultimo.inicio,
      ultimo.fin,
      consultadoEn,
      cerrado: true,
    );
    final a = ultimo.consumo;
    if (anterior['periodo'] != ultimo.periodo ||
        consumoAnterior.estado != a.estado ||
        consumoAnterior.litros != a.litros ||
        consumoAnterior.metrosCubicos != a.metrosCubicos ||
        consumoAnterior.desde != a.desde ||
        consumoAnterior.hasta != a.hasta) {
      throw const FormatException();
    }
    final limiteActual = DateTime.utc(
      int.parse(periodoActual.substring(0, 4)),
      int.parse(periodoActual.substring(5)) + 2,
      1,
      5,
    );
    final consumoActual = ConsumoEstadistico.desdeJson(
      actual['consumo'],
      ultimo.fin,
      limiteActual,
      consultadoEn,
      cerrado: false,
    );
    _duracion(actual, consumoActual);
    _duracion(anterior, consumoAnterior);
    final conDiferencia = consumoActual.litros != null && a.litros != null;
    if (conDiferencia) {
      final diferencia = CantidadEstadistica.desdeJson(
        comparacion['diferencia'],
        signo: true,
      );
      if (diferencia.milesimasLitro !=
          (BigInt.parse(consumoActual.litros!) - BigInt.parse(a.litros!)) *
              BigInt.from(1000)) {
        throw const FormatException();
      }
    } else if (comparacion['diferencia'] != null) {
      throw const FormatException();
    }
    final resumen = _objeto(json['resumen']);
    final conteos = <String, int>{};
    const claves = {
      'COMPLETO': 'mesesCompletos',
      'PARCIAL': 'mesesParciales',
      'SIN_DATOS': 'mesesSinDatos',
      'INCOMPATIBLE': 'mesesIncompatibles',
    };
    for (final entrada in claves.entries) {
      final cantidad = resumen[entrada.value];
      final esperado = serie
          .where((m) => m.consumo.estado == entrada.key)
          .length;
      if (cantidad is! int || cantidad != esperado) {
        throw const FormatException();
      }
      conteos[entrada.key] = cantidad;
    }
    final completos = serie
        .where((m) => m.consumo.estado == 'COMPLETO')
        .map((m) => BigInt.parse(m.consumo.litros!))
        .toList();
    final cantidades = <String, CantidadEstadistica?>{};
    for (final clave in ['total', 'promedio', 'minimo', 'maximo']) {
      final dato = resumen[clave];
      if ((dato == null) != completos.isEmpty) throw const FormatException();
      cantidades[clave] = dato == null
          ? null
          : CantidadEstadistica.desdeJson(dato);
    }
    if (completos.isNotEmpty) {
      final total = completos.reduce((a, b) => a + b);
      final n = BigInt.from(completos.length);
      final promedio = (total * BigInt.from(2000) + n) ~/ (n * BigInt.two);
      if (cantidades['total']!.milesimasLitro != total * BigInt.from(1000) ||
          cantidades['promedio']!.milesimasLitro != promedio ||
          cantidades['minimo']!.milesimasLitro !=
              completos.reduce((a, b) => a < b ? a : b) * BigInt.from(1000) ||
          cantidades['maximo']!.milesimasLitro !=
              completos.reduce((a, b) => a > b ? a : b) * BigInt.from(1000)) {
        throw const FormatException();
      }
    }
    return EstadisticasConsumo(
      consultadoEn: consultadoEn,
      periodoActual: periodoActual,
      serie: List.unmodifiable(serie),
      conteos: Map.unmodifiable(conteos),
      total: cantidades['total'],
      promedio: cantidades['promedio'],
      minimo: cantidades['minimo'],
      maximo: cantidades['maximo'],
    );
  }

  final DateTime consultadoEn;
  final String periodoActual;
  final List<MesEstadistico> serie;
  final Map<String, int> conteos;
  final CantidadEstadistica? total;
  final CantidadEstadistica? promedio;
  final CantidadEstadistica? minimo;
  final CantidadEstadistica? maximo;
}
