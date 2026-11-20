import 'dart:async';
import 'dart:convert';
import 'dart:io';

class FalloApi implements Exception {
  const FalloApi(this.mensaje, {this.estado, this.reintentarEn});
  final String mensaje;
  final int? estado;
  final Duration? reintentarEn;

  @override
  String toString() => mensaje;
}

Map<String, dynamic> objeto(Object? valor) {
  if (valor is! Map<String, dynamic>) throw const FormatException();
  return valor;
}

String texto(Map<String, dynamic> json, String clave) {
  final valor = json[clave];
  if (valor is! String || valor.isEmpty) throw const FormatException();
  return valor;
}

DateTime fecha(Map<String, dynamic> json, String clave) {
  final valor = DateTime.parse(texto(json, clave));
  if (!valor.isUtc) throw const FormatException();
  return valor;
}

class SesionConsulta {
  const SesionConsulta({
    required this.token,
    required this.suministro,
    required this.nombre,
    required this.expiraEn,
  });

  factory SesionConsulta.desdeJson(Object? valor) {
    final json = objeto(valor);
    final sesion = objeto(json['sesion']);
    final token = texto(json, 'token');
    final suministro = texto(sesion, 'codigoSuministro');
    if (json['tipoToken'] != 'Bearer' ||
        sesion['rol'] != 'CONSULTA' ||
        !RegExp(r'^nc_[A-Za-z0-9_-]{43}$').hasMatch(token) ||
        !RegExp(r'^[0-9]{1,20}$').hasMatch(suministro)) {
      throw const FormatException();
    }
    return SesionConsulta(
      token: token,
      suministro: suministro,
      nombre: texto(sesion, 'nombre'),
      expiraEn: fecha(sesion, 'expiraEn'),
    );
  }

  final String token;
  final String suministro;
  final String nombre;
  final DateTime expiraEn;
}

class ConsumoActual {
  const ConsumoActual({
    required this.periodo,
    required this.estado,
    required this.metrosCubicos,
    required this.litros,
    required this.desde,
    required this.hasta,
    required this.ultimaLectura,
    required this.actualizacion,
  });

  factory ConsumoActual.desdeJson(Object? valor, String suministro) {
    final json = objeto(valor);
    final consumo = objeto(json['consumo']);
    final actualizacion = objeto(json['actualizacion']);
    final estado = texto(consumo, 'estado');
    if (json['codigoSuministro'] != suministro ||
        !RegExp(r'^[0-9]{4}-(0[1-9]|1[0-2])$')
            .hasMatch(texto(json, 'periodo')) ||
        !{
          'COMPLETO',
          'PARCIAL',
          'SIN_DATOS',
          'INCOMPATIBLE',
        }.contains(estado)) {
      throw const FormatException();
    }
    String? cantidad(String clave) {
      final valor = consumo[clave];
      if (valor == null) return null;
      if (valor is! String ||
          !RegExp(r'^[0-9]+(?:\.[0-9]+)?$').hasMatch(valor)) {
        throw const FormatException();
      }
      return valor;
    }

    final m3 = cantidad('metrosCubicos');
    final litros = cantidad('litros');
    if ({'COMPLETO', 'PARCIAL'}.contains(estado) &&
        (m3 == null || litros == null)) {
      throw const FormatException();
    }
    return ConsumoActual(
      periodo: texto(json, 'periodo'),
      estado: estado,
      metrosCubicos: {'COMPLETO', 'PARCIAL'}.contains(estado) ? m3 : null,
      litros: {'COMPLETO', 'PARCIAL'}.contains(estado) ? litros : null,
      desde: consumo['desde'] == null ? null : fecha(consumo, 'desde'),
      hasta: consumo['hasta'] == null ? null : fecha(consumo, 'hasta'),
      ultimaLectura: actualizacion['ultimaLectura'] == null
          ? null
          : fecha(actualizacion, 'ultimaLectura'),
      actualizacion: texto(actualizacion, 'estado'),
    );
  }

  final String periodo;
  final String estado;
  final String? metrosCubicos;
  final String? litros;
  final DateTime? desde;
  final DateTime? hasta;
  final DateTime? ultimaLectura;
  final String actualizacion;
}

abstract class ServicioNivora {
  Future<SesionConsulta> ingresar(String suministro, String dni);
  Future<ConsumoActual> consumo(SesionConsulta sesion);
  Future<HistorialMensual> historial(
    SesionConsulta sesion, {
    int meses = 12,
    String? hasta,
  });
  Future<void> salir(SesionConsulta sesion);
  void cerrar();
}

class ApiNivora implements ServicioNivora {
  ApiNivora(
    this.base, {
    bool desarrollo = false,
    this.espera = const Duration(seconds: 20),
  }) : configuracionValida = _validar(base, desarrollo);

  final Uri base;
  final bool configuracionValida;
  final Duration espera;
  static const _ruta = '/api/v1/acceso/consulta';
  final Set<HttpClient> _clientes = {};

  static bool _validar(Uri uri, bool desarrollo) {
    final local = {
      'localhost',
      '127.0.0.1',
      '10.0.2.2',
      '::1',
    }.contains(uri.host);
    return uri.host.isNotEmpty &&
        uri.userInfo.isEmpty &&
        !uri.hasQuery &&
        !uri.hasFragment &&
        (uri.path.isEmpty || uri.path == '/') &&
        (uri.scheme == 'https' ||
            (desarrollo && local && uri.scheme == 'http'));
  }

  Future<Object?> _pedir(
    String metodo,
    String ruta,
    int esperado, {
    SesionConsulta? sesion,
    Map<String, String>? datos,
  }) async {
    if (!configuracionValida) {
      throw const FalloApi('No se pudo configurar la conexión con Nivora.');
    }
    final cliente = HttpClient()..connectionTimeout = espera;
    _clientes.add(cliente);
    try {
      return await (() async {
        final peticion = await cliente.openUrl(
          metodo,
          base.resolve('$_ruta$ruta'),
        );
        peticion.followRedirects = false;
        peticion.headers.set(HttpHeaders.acceptHeader, 'application/json');
        if (sesion != null) {
          peticion.headers.set(
            HttpHeaders.authorizationHeader,
            'Bearer ${sesion.token}',
          );
        }
        if (datos != null) {
          peticion.headers.contentType = ContentType.json;
          peticion.write(jsonEncode(datos));
        }
        final respuesta = await peticion.close();
        if (respuesta.statusCode != esperado) {
          final segundos = int.tryParse(
            respuesta.headers.value('retry-after') ?? '',
          );
          throw FalloApi(
            _mensaje(respuesta.statusCode),
            estado: respuesta.statusCode,
            reintentarEn: respuesta.statusCode == 429
                ? Duration(
                    seconds: segundos != null && segundos > 0 ? segundos : 60,
                  )
                : null,
          );
        }
        if (esperado == 204) return null;
        final bytes = <int>[];
        await for (final parte in respuesta) {
          bytes.addAll(parte);
          if (bytes.length > 1048576) throw const FormatException();
        }
        return jsonDecode(utf8.decode(bytes));
      })().timeout(espera);
    } on TimeoutException {
      throw const FalloApi(
        'La conexión está tardando demasiado. Inténtalo de nuevo.',
      );
    } on SocketException {
      throw const FalloApi(
        'No podemos conectar con Nivora. Revisa tu conexión e inténtalo de nuevo.',
      );
    } on HandshakeException {
      throw const FalloApi(
        'No se pudo establecer una conexión segura con Nivora.',
      );
    } on HttpException {
      throw const FalloApi('La conexión se interrumpió. Inténtalo de nuevo.');
    } on FormatException {
      throw const FalloApi(
        'Nivora devolvió una respuesta inesperada. Inténtalo más tarde.',
      );
    } finally {
      cliente.close(force: true);
      _clientes.remove(cliente);
    }
  }

  static String _mensaje(int estado) => switch (estado) {
    400 => 'Revisa el código de suministro y el DNI ingresados.',
    401 => 'No se pudo validar el acceso. Revisa los datos ingresados.',
    404 => 'La información del suministro aún no está disponible.',
    429 => 'Se realizaron varios intentos. Espera antes de volver a ingresar.',
    503 =>
      'El servicio no está disponible en este momento. Inténtalo más tarde.',
    _ => 'No se pudo completar la solicitud. Inténtalo más tarde.',
  };

  @override
  Future<SesionConsulta> ingresar(String suministro, String dni) async {
    final json = await _pedir(
      'POST',
      '/sesiones',
      201,
      datos: {'codigoSuministro': suministro, 'dni': dni},
    );
    try {
      final sesion = SesionConsulta.desdeJson(json);
      if (sesion.suministro != suministro ||
          !sesion.expiraEn.isAfter(DateTime.now())) {
        throw const FormatException();
      }
      return sesion;
    } on FormatException {
      throw const FalloApi(
        'No se pudo confirmar la sesión. Inténtalo de nuevo.',
      );
    }
  }

  @override
  Future<ConsumoActual> consumo(SesionConsulta sesion) async {
    final json = await _pedir('GET', '/consumo-actual', 200, sesion: sesion);
    try {
      return ConsumoActual.desdeJson(json, sesion.suministro);
    } on FormatException {
      throw const FalloApi(
        'No se pudo interpretar el consumo. Inténtalo más tarde.',
      );
    }
  }

  @override
  Future<void> salir(SesionConsulta sesion) async {
    try {
      await _pedir('DELETE', '/sesion', 204, sesion: sesion);
    } on FalloApi catch (fallo) {
      if (fallo.estado != 401) rethrow;
    }
  }

  @override
  Future<HistorialMensual> historial(
    SesionConsulta sesion, {
    int meses = 12,
    String? hasta,
  }) async {
    if (meses < 1 || meses > 12 || (hasta != null && !periodoValido(hasta))) {
      throw const FalloApi('Selecciona entre 1 y 12 meses anteriores.');
    }
    final parametros = {'meses': '$meses'};
    if (hasta != null) parametros['hasta'] = hasta;
    final consulta = Uri(queryParameters: parametros).query;
    final json = await _pedir(
      'GET',
      '/historial?$consulta',
      200,
      sesion: sesion,
    );
    try {
      return HistorialMensual.desdeJson(
        json,
        sesion.suministro,
        meses: meses,
        hasta: hasta,
      );
    } on FormatException {
      throw const FalloApi(
        'No se pudo interpretar el historial. Inténtalo más tarde.',
      );
    }
  }

  @override
  void cerrar() {
    for (final cliente in _clientes.toList()) {
      cliente.close(force: true);
    }
    _clientes.clear();
  }
}

bool periodoValido(String valor) =>
    RegExp(r'^[1-9][0-9]{3}-(0[1-9]|1[0-2])$').hasMatch(valor);

String moverMes(String periodo, int meses) {
  final fecha = DateTime.utc(
    int.parse(periodo.substring(0, 4)),
    int.parse(periodo.substring(5)) + meses,
  );
  return '${fecha.year.toString().padLeft(4, '0')}-'
      '${fecha.month.toString().padLeft(2, '0')}';
}

class ConsumoMensual {
  const ConsumoMensual({
    required this.periodo,
    required this.estado,
    required this.metrosCubicos,
    required this.litros,
    required this.desde,
    required this.hasta,
  });

  factory ConsumoMensual.desdeJson(Object? valor) {
    final json = objeto(valor);
    final periodo = texto(json, 'periodo');
    if (!periodoValido(periodo)) throw const FormatException();
    final inicio = DateTime.utc(
      int.parse(periodo.substring(0, 4)),
      int.parse(periodo.substring(5)),
      1,
      5,
    );
    final fin = DateTime.utc(inicio.year, inicio.month + 1, 1, 5);
    if (fecha(json, 'inicioMes') != inicio ||
        fecha(json, 'finMesExclusivo') != fin) {
      throw const FormatException();
    }
    final consumo = objeto(json['consumo']);
    final estado = texto(consumo, 'estado');
    if (!{
      'COMPLETO',
      'PARCIAL',
      'SIN_DATOS',
      'INCOMPATIBLE',
    }.contains(estado)) {
      throw const FormatException();
    }
    final conValor = estado == 'COMPLETO' || estado == 'PARCIAL';
    final litros = consumo['litros'];
    final m3 = consumo['metrosCubicos'];
    final desde = consumo['desde'] == null ? null : fecha(consumo, 'desde');
    final hasta = consumo['hasta'] == null ? null : fecha(consumo, 'hasta');
    if (conValor) {
      if (litros is! String ||
          !RegExp(r'^[0-9]+$').hasMatch(litros) ||
          m3 is! String ||
          !RegExp(r'^[0-9]+(?:\.[0-9]{1,3})?$').hasMatch(m3) ||
          desde == null ||
          hasta == null ||
          desde.isBefore(inicio) ||
          hasta.isAfter(fin) ||
          desde.isAfter(hasta)) {
        throw const FormatException();
      }
      // Compara unidades sin redondear con double ni perder decimales.
      final partes = m3.split('.');
      final convertido =
          BigInt.parse(partes[0]) * BigInt.from(1000) +
          BigInt.parse(partes.length == 1 ? '0' : partes[1].padRight(3, '0'));
      if (convertido != BigInt.parse(litros) ||
          (estado == 'COMPLETO' && (desde != inicio || hasta != fin))) {
        throw const FormatException();
      }
    } else if (litros != null || m3 != null || desde != null || hasta != null) {
      throw const FormatException();
    }
    return ConsumoMensual(
      periodo: periodo,
      estado: estado,
      metrosCubicos: conValor ? m3 as String : null,
      litros: conValor ? litros as String : null,
      desde: desde,
      hasta: hasta,
    );
  }

  final String periodo;
  final String estado;
  final String? metrosCubicos;
  final String? litros;
  final DateTime? desde;
  final DateTime? hasta;
}

class HistorialMensual {
  const HistorialMensual({required this.consultadoEn, required this.periodos});

  factory HistorialMensual.desdeJson(
    Object? valor,
    String suministro, {
    required int meses,
    String? hasta,
  }) {
    final json = objeto(valor);
    final consultadoEn = fecha(json, 'consultadoEn');
    final lima = consultadoEn.subtract(const Duration(hours: 5));
    final mesActual =
        '${lima.year.toString().padLeft(4, '0')}-'
        '${lima.month.toString().padLeft(2, '0')}';
    final ultimo = hasta ?? moverMes(mesActual, -1);
    final lista = json['periodos'];
    if (json['codigoSuministro'] != suministro ||
        json['zonaHoraria'] != 'America/Lima' ||
        lista is! List ||
        lista.length != meses ||
        ultimo.compareTo(mesActual) >= 0) {
      throw const FormatException();
    }
    final periodos = lista.map(ConsumoMensual.desdeJson).toList();
    for (var i = 0; i < periodos.length; i++) {
      if (periodos[i].periodo != moverMes(ultimo, -i)) {
        throw const FormatException();
      }
    }
    return HistorialMensual(
      consultadoEn: consultadoEn,
      periodos: List.unmodifiable(periodos),
    );
  }

  final DateTime consultadoEn;
  final List<ConsumoMensual> periodos;
}
