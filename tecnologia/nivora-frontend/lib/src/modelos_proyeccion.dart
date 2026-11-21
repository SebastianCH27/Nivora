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
  final fecha = DateTime.parse(_texto(json, clave));
  if (!fecha.isUtc) throw const FormatException();
  return fecha;
}

String _decimal(Object? valor, int escala) {
  if (valor is! String ||
      !RegExp('^[0-9]+(?:\\.[0-9]{1,$escala})?\$').hasMatch(valor)) {
    throw const FormatException();
  }
  return valor;
}

BigInt _escalado(String valor, int escala) {
  final partes = valor.split('.');
  return BigInt.parse(partes[0]) * BigInt.from(10).pow(escala) +
      BigInt.parse(partes.length == 1 ? '0' : partes[1].padRight(escala, '0'));
}

String _litros(Object? valor, Object? metrosCubicos) {
  if (valor is! String ||
      !RegExp(r'^[0-9]+$').hasMatch(valor) ||
      BigInt.parse(valor) != _escalado(_decimal(metrosCubicos, 3), 3)) {
    throw const FormatException();
  }
  return valor;
}

class ObservadoProyeccion {
  const ObservadoProyeccion({
    required this.estado,
    required this.litros,
    required this.metrosCubicos,
    required this.desde,
    required this.hasta,
  });

  factory ObservadoProyeccion.desdeJson(
    Object? valor,
    DateTime inicio,
    DateTime fin,
    DateTime calculadoEn,
  ) {
    final json = _objeto(valor);
    final estado = _texto(json, 'estado');
    if (!{
      'COMPLETO',
      'PARCIAL',
      'SIN_DATOS',
      'INCOMPATIBLE',
    }.contains(estado)) {
      throw const FormatException();
    }
    String? litros;
    String? m3;
    DateTime? desde;
    DateTime? hasta;
    if (estado == 'COMPLETO' || estado == 'PARCIAL') {
      litros = _litros(json['litros'], json['metrosCubicos']);
      m3 = json['metrosCubicos'] as String;
      desde = _fecha(json, 'desde');
      hasta = _fecha(json, 'hasta');
      if (desde.isBefore(inicio) ||
          hasta.isAfter(fin) ||
          desde.isAfter(hasta) ||
          hasta.isAfter(calculadoEn)) {
        throw const FormatException();
      }
    } else if (json['litros'] != null ||
        json['metrosCubicos'] != null ||
        json['desde'] != null ||
        json['hasta'] != null) {
      throw const FormatException();
    }
    return ObservadoProyeccion(
      estado: estado,
      litros: litros,
      metrosCubicos: m3,
      desde: desde,
      hasta: hasta,
    );
  }

  final String estado;
  final String? litros;
  final String? metrosCubicos;
  final DateTime? desde;
  final DateTime? hasta;
}

class VolumenProyectado {
  const VolumenProyectado({
    required this.litros,
    required this.metrosCubicos,
    required this.promedioLitrosPorDia,
  });

  factory VolumenProyectado.desdeJson(
    Object? valor,
    ObservadoProyeccion observado,
    DateTime inicio,
    DateTime fin,
  ) {
    final json = _objeto(valor);
    final litros = _litros(json['litros'], json['metrosCubicos']);
    final segundos = _escalado(_decimal(json['segundosObservados'], 9), 9);
    final segundosMes = _escalado(_decimal(json['segundosDelMes'], 9), 9);
    final esperado =
        BigInt.from(observado.hasta!.difference(inicio).inMicroseconds) *
        BigInt.from(1000);
    final mesEsperado =
        BigInt.from(fin.difference(inicio).inMicroseconds) * BigInt.from(1000);
    if (segundos != esperado ||
        segundosMes != mesEsperado ||
        segundos < BigInt.from(86400000000000) ||
        segundos >= segundosMes) {
      throw const FormatException();
    }
    return VolumenProyectado(
      litros: litros,
      metrosCubicos: json['metrosCubicos'] as String,
      promedioLitrosPorDia: _decimal(json['promedioLitrosPorDia'], 3),
    );
  }

  final String litros;
  final String metrosCubicos;
  final String promedioLitrosPorDia;
}

class CostoProyectado {
  const CostoProyectado({
    required this.estado,
    required this.tarifa,
    required this.desglose,
  });

  factory CostoProyectado.desdeJson(
    Object? valor,
    DateTime inicio,
    DateTime fin,
  ) {
    final json = _objeto(valor);
    final estado = _texto(json, 'estado');
    if (!{
      'ESTIMADO',
      'CONCEPTOS_INCOMPLETOS',
      'SIN_TARIFA_COMPATIBLE',
    }.contains(estado)) {
      throw const FormatException();
    }
    TarifaCosto? tarifa;
    DesgloseCosto? desglose;
    if (estado == 'SIN_TARIFA_COMPATIBLE') {
      if (json['tarifa'] != null || json['desglose'] != null) {
        throw const FormatException();
      }
    } else {
      tarifa = TarifaCosto.desdeJson(json['tarifa'], inicio, fin);
      if (json['fuenteTarifaria'] != tarifa.fuente ||
          json['revisionTarifaria'] != tarifa.revision) {
        throw const FormatException();
      }
      desglose = DesgloseCosto.desdeJson(json['desglose'], estado, tarifa);
    }
    return CostoProyectado(estado: estado, tarifa: tarifa, desglose: desglose);
  }

  final String estado;
  final TarifaCosto? tarifa;
  final DesgloseCosto? desglose;
}

class ProyeccionConsumo {
  const ProyeccionConsumo({
    required this.periodo,
    required this.calculadoEn,
    required this.estado,
    required this.observado,
    required this.volumen,
    required this.costo,
    required this.actualizacion,
    required this.ultimaLectura,
  });

  factory ProyeccionConsumo.desdeJson(Object? valor, String suministro) {
    final json = _objeto(valor);
    final periodo = _texto(json, 'periodo');
    if (json['fuente'] != 'empresa-agua' ||
        json['codigoSuministro'] != suministro ||
        json['moneda'] != 'PEN' ||
        json['zonaHoraria'] != 'America/Lima' ||
        !RegExp(r'^[1-9][0-9]{3}-(0[1-9]|1[0-2])$').hasMatch(periodo)) {
      throw const FormatException();
    }
    final inicio = DateTime.utc(
      int.parse(periodo.substring(0, 4)),
      int.parse(periodo.substring(5)),
      1,
      5,
    );
    final fin = DateTime.utc(inicio.year, inicio.month + 1, 1, 5);
    final calculadoEn = _fecha(json, 'calculadoEn');
    if (_fecha(json, 'inicioMes') != inicio ||
        _fecha(json, 'finMesExclusivo') != fin ||
        calculadoEn.isBefore(inicio) ||
        calculadoEn.isAfter(fin)) {
      throw const FormatException();
    }
    final supuestos = _objeto(json['supuestos']);
    if (supuestos['metodo'] != 'RITMO_PROMEDIO_CONSTANTE' ||
        supuestos['minimoObservadoSegundos'] != 86400) {
      throw const FormatException();
    }
    final estado = _texto(json, 'estado');
    if (!{
      'PROYECTADO',
      'SIN_CONSUMO_CALCULABLE',
      'COBERTURA_INCOMPLETA',
      'TIEMPO_INSUFICIENTE',
      'PERIODO_CERRADO',
    }.contains(estado)) {
      throw const FormatException();
    }
    final observado = ObservadoProyeccion.desdeJson(
      json['observado'],
      inicio,
      fin,
      calculadoEn,
    );
    final conConsumo = observado.litros != null;
    final completo =
        observado.estado == 'COMPLETO' && observado.desde == inicio;
    final duracion = conConsumo
        ? observado.hasta!.difference(inicio)
        : Duration.zero;
    final cerrado = observado.hasta == fin;
    final compatible = switch (estado) {
      'PROYECTADO' =>
        completo && !cerrado && duracion >= const Duration(days: 1),
      'SIN_CONSUMO_CALCULABLE' => !conConsumo,
      'COBERTURA_INCOMPLETA' => conConsumo && !completo,
      'TIEMPO_INSUFICIENTE' =>
        completo && !cerrado && duracion < const Duration(days: 1),
      _ => completo && cerrado,
    };
    if (!compatible) throw const FormatException();
    VolumenProyectado? volumen;
    CostoProyectado? costo;
    if (estado == 'PROYECTADO') {
      volumen = VolumenProyectado.desdeJson(
        json['proyeccion'],
        observado,
        inicio,
        fin,
      );
      costo = CostoProyectado.desdeJson(json['costoProyectado'], inicio, fin);
    } else if (json['proyeccion'] != null || json['costoProyectado'] != null) {
      throw const FormatException();
    }
    final actualizacion = _objeto(json['actualizacion']);
    final sincronizacion = _texto(actualizacion, 'estado');
    if (!{
      'SIN_LECTURAS',
      'PENDIENTE',
      'CORTE_CONFIRMADO',
      'CON_INCIDENCIAS',
    }.contains(sincronizacion)) {
      throw const FormatException();
    }
    final ultima = actualizacion['ultimaLectura'] == null
        ? null
        : _fecha(actualizacion, 'ultimaLectura');
    final confirmada = actualizacion['confirmadoHasta'] == null
        ? null
        : _fecha(actualizacion, 'confirmadoHasta');
    if ((ultima != null && ultima.isAfter(calculadoEn)) ||
        (confirmada != null && confirmada.isAfter(calculadoEn))) {
      throw const FormatException();
    }
    return ProyeccionConsumo(
      periodo: periodo,
      calculadoEn: calculadoEn,
      estado: estado,
      observado: observado,
      volumen: volumen,
      costo: costo,
      actualizacion: sincronizacion,
      ultimaLectura: ultima,
    );
  }

  final String periodo;
  final DateTime calculadoEn;
  final String estado;
  final ObservadoProyeccion observado;
  final VolumenProyectado? volumen;
  final CostoProyectado? costo;
  final String actualizacion;
  final DateTime? ultimaLectura;
}
