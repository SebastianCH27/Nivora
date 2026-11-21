import 'modelos_costos.dart';

Map<String, dynamic> _objeto(Object? valor) {
  if (valor is! Map<String, dynamic>) throw const FormatException();
  return valor;
}

String _texto(Map<String, dynamic> json, String clave) {
  final valor = json[clave];
  if (valor is! String || valor.isEmpty) throw const FormatException();
  return valor;
}

DateTime _fecha(Map<String, dynamic> json, String clave) {
  final valor = DateTime.parse(_texto(json, clave));
  if (!valor.isUtc) throw const FormatException();
  return valor;
}

bool periodoComparacionValido(String valor) =>
    RegExp(r'^[1-9][0-9]{3}-(0[1-9]|1[0-2])$').hasMatch(valor);

int _ordinal(String periodo) =>
    int.parse(periodo.substring(0, 4)) * 12 + int.parse(periodo.substring(5));

bool rangoComparacionValido(String? base, String? comparado) =>
    (base == null && comparado == null) ||
    (base != null &&
        comparado != null &&
        periodoComparacionValido(base) &&
        periodoComparacionValido(comparado) &&
        (_ordinal(base) - _ordinal(comparado)).abs() <= 12);

String _decimal(Object? valor, int escala, {bool conSigno = false}) {
  final signo = conSigno ? '-?' : '';
  if (valor is! String ||
      !RegExp('^$signo[0-9]+(?:\\.[0-9]{1,$escala})?\$').hasMatch(valor)) {
    throw const FormatException();
  }
  return valor;
}

BigInt _fijo(String valor, int escala) {
  final negativo = valor.startsWith('-');
  final partes = (negativo ? valor.substring(1) : valor).split('.');
  final numero =
      BigInt.parse(partes[0]) * BigInt.from(10).pow(escala) +
      BigInt.parse(partes.length == 1 ? '0' : partes[1].padRight(escala, '0'));
  return negativo ? -numero : numero;
}

BigInt _nanos(Duration duracion) =>
    BigInt.from(duracion.inMicroseconds) * BigInt.from(1000);

bool? _indicador(Object? valor) {
  if (valor == null) return null;
  if (valor is! bool) throw const FormatException();
  return valor;
}

class PeriodoComparacion {
  const PeriodoComparacion({
    required this.estimacion,
    required this.inicio,
    required this.fin,
    required this.periodoCompleto,
    required this.duracionTramo,
  });

  factory PeriodoComparacion.desdeJson(Object? valor, DateTime consultadoEn) {
    final json = _objeto(valor);
    final estimacion = _objeto(json['estimacion']);
    final periodo = _texto(estimacion, 'periodo');
    final inicio = _fecha(estimacion, 'inicioMes');
    final fin = _fecha(estimacion, 'finMesExclusivo');
    final enCurso = json['enCurso'];
    final completo = json['periodoCompleto'];
    if (!periodoComparacionValido(periodo) ||
        enCurso is! bool ||
        completo is! bool ||
        inicio.isAfter(consultadoEn) ||
        enCurso != consultadoEn.isBefore(fin)) {
      throw const FormatException();
    }
    final costo = PeriodoCosto.desdeJson(
      estimacion,
      periodo,
      consultadoEn,
      enCurso: enCurso,
      inicioPeriodo: inicio,
      finPeriodo: fin,
    );
    if (_fijo(_decimal(json['duracionPeriodoSegundos'], 9), 9) !=
            _nanos(fin.difference(inicio)) ||
        completo !=
            (!enCurso &&
                costo.cobertura == 'COMPLETO' &&
                costo.desde == inicio &&
                costo.hasta == fin)) {
      throw const FormatException();
    }
    Duration? duracion;
    if (costo.desde != null && costo.hasta != null) {
      duracion = costo.hasta!.difference(costo.desde!);
      if (_fijo(_decimal(json['duracionTramoSegundos'], 9), 9) !=
          _nanos(duracion)) {
        throw const FormatException();
      }
    } else if (json['duracionTramoSegundos'] != null) {
      throw const FormatException();
    }
    return PeriodoComparacion(
      estimacion: costo,
      inicio: inicio,
      fin: fin,
      periodoCompleto: completo,
      duracionTramo: duracion,
    );
  }

  final PeriodoCosto estimacion;
  final DateTime inicio;
  final DateTime fin;
  final bool periodoCompleto;
  final Duration? duracionTramo;
}

class ComparacionConsumo {
  const ComparacionConsumo({
    required this.consultadoEn,
    required this.base,
    required this.comparado,
    required this.estado,
    required this.diferenciaLitros,
    required this.diferenciaMetrosCubicos,
    required this.diferenciaCosto,
    required this.mismaDuracion,
    required this.mismaTarifa,
  });

  factory ComparacionConsumo.desdeJson(
    Object? valor,
    String suministro, {
    String? periodoBase,
    String? periodoComparado,
  }) {
    final json = _objeto(valor);
    if (!rangoComparacionValido(periodoBase, periodoComparado) ||
        json['fuente'] != 'empresa-agua' ||
        json['codigoSuministro'] != suministro ||
        json['moneda'] != 'PEN' ||
        json['zonaHoraria'] != 'America/Lima') {
      throw const FormatException();
    }
    final consultadoEn = _fecha(json, 'consultadoEn');
    final base = PeriodoComparacion.desdeJson(json['base'], consultadoEn);
    final comparado = PeriodoComparacion.desdeJson(
      json['comparado'],
      consultadoEn,
    );
    final a = base.estimacion;
    final b = comparado.estimacion;
    if (!rangoComparacionValido(a.periodo, b.periodo) ||
        (periodoBase != null &&
            (a.periodo != periodoBase || b.periodo != periodoComparado)) ||
        (periodoBase == null &&
            (_ordinal(b.periodo) - _ordinal(a.periodo) != 1 || !b.enCurso))) {
      throw const FormatException();
    }
    String? litros;
    String? m3;
    final conVolumen = a.litros != null && b.litros != null;
    if (conVolumen) {
      final dato = json['diferenciaLitros'];
      if (dato is! String || !RegExp(r'^-?[0-9]+$').hasMatch(dato)) {
        throw const FormatException();
      }
      litros = dato;
      m3 = _decimal(json['diferenciaMetrosCubicos'], 3, conSigno: true);
      final esperado = BigInt.parse(b.litros!) - BigInt.parse(a.litros!);
      if (BigInt.parse(litros) != esperado || _fijo(m3, 3) != esperado) {
        throw const FormatException();
      }
    } else if (json['diferenciaLitros'] != null ||
        json['diferenciaMetrosCubicos'] != null) {
      throw const FormatException();
    }
    final totalA = a.desglose?.totalEstimado;
    final totalB = b.desglose?.totalEstimado;
    final conCostos = conVolumen && totalA != null && totalB != null;
    String? diferenciaCosto;
    if (conCostos) {
      diferenciaCosto = _decimal(
        json['diferenciaCostoEstimado'],
        2,
        conSigno: true,
      );
      if (_fijo(diferenciaCosto, 2) != _fijo(totalB, 2) - _fijo(totalA, 2)) {
        throw const FormatException();
      }
    } else if (json['diferenciaCostoEstimado'] != null) {
      throw const FormatException();
    }
    final esperado = !conCostos
        ? 'NO_CALCULABLE'
        : base.periodoCompleto && comparado.periodoCompleto
        ? 'COMPLETA'
        : 'PARCIAL';
    final estado = _texto(json, 'estado');
    if (estado != esperado) throw const FormatException();
    final mismaDuracion = _indicador(json['mismaDuracionTramo']);
    final duracionEsperada =
        base.duracionTramo == null || comparado.duracionTramo == null
        ? null
        : base.duracionTramo == comparado.duracionTramo;
    if (mismaDuracion != duracionEsperada) throw const FormatException();
    final mismaTarifa = _indicador(json['mismaVersionTarifaria']);
    if ((a.tarifa == null || b.tarifa == null)
        ? mismaTarifa != null
        : mismaTarifa == null) {
      throw const FormatException();
    }
    if (mismaTarifa == true &&
        (a.tarifa!.fuente != b.tarifa!.fuente ||
            a.tarifa!.codigo != b.tarifa!.codigo ||
            a.tarifa!.version != b.tarifa!.version)) {
      throw const FormatException();
    }
    return ComparacionConsumo(
      consultadoEn: consultadoEn,
      base: base,
      comparado: comparado,
      estado: estado,
      diferenciaLitros: litros,
      diferenciaMetrosCubicos: m3,
      diferenciaCosto: diferenciaCosto,
      mismaDuracion: mismaDuracion,
      mismaTarifa: mismaTarifa,
    );
  }

  final DateTime consultadoEn;
  final PeriodoComparacion base;
  final PeriodoComparacion comparado;
  final String estado;
  final String? diferenciaLitros;
  final String? diferenciaMetrosCubicos;
  final String? diferenciaCosto;
  final bool? mismaDuracion;
  final bool? mismaTarifa;
  int? get sentidoConsumo =>
      diferenciaLitros == null ? null : BigInt.parse(diferenciaLitros!).sign;
  int? get sentidoCosto =>
      diferenciaCosto == null ? null : _fijo(diferenciaCosto!, 2).sign;
}
