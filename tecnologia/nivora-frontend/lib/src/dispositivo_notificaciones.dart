import 'dart:async';

import 'package:flutter/services.dart';

import 'api_nivora.dart';

abstract class DispositivoNotificaciones {
  Future<String> identificar();
  Future<PermisoNotificaciones> consultarPermiso();
  Future<PermisoNotificaciones> solicitarPermiso();
  Future<void> abrirAjustes();
}

class DispositivoNotificacionesNativo implements DispositivoNotificaciones {
  const DispositivoNotificacionesNativo();
  static const canal = MethodChannel('pe.nivora/notificaciones');

  Future<T?> _invocar<T>(String metodo) async {
    try {
      return await canal
          .invokeMethod<T>(metodo)
          .timeout(const Duration(minutes: 2));
    } on PlatformException catch (error) {
      final detalles = error.details;
      if (error.code == 'WINDOWS_NOTIFICACIONES' && detalles is Map) {
        final etapa = detalles['etapa'];
        final codigo = detalles['hresult'];
        if (etapa is String &&
            codigo is String &&
            RegExp(r'^[A-Z_]{1,40}$').hasMatch(etapa) &&
            RegExp(r'^0x[0-9A-F]{8}$').hasMatch(codigo)) {
          throw FalloApi(
            'Windows no pudo completar la configuración. '
            'Referencia: $etapa/$codigo.',
          );
        }
      }
      throw const FalloApi(
        'No pudimos consultar la configuración del dispositivo. Vuelve a intentarlo.',
      );
    } on MissingPluginException {
      throw const FalloApi(
        'La configuración del dispositivo requiere volver a compilar la aplicación.',
      );
    } on TimeoutException {
      throw const FalloApi(
        'La consulta del dispositivo tardó demasiado. Vuelve a intentarlo.',
      );
    }
  }

  @override
  Future<String> identificar() async {
    final id = await _invocar<String>('identificar');
    if (id == null || !idDispositivoValido(id)) {
      throw const FalloApi(
        'No pudimos identificar esta instalación. Vuelve a intentarlo.',
      );
    }
    return id;
  }

  Future<PermisoNotificaciones> _permiso(String metodo) async {
    try {
      return PermisoNotificaciones.leer(await _invocar<String>(metodo));
    } on FormatException {
      throw const FalloApi(
        'No pudimos comprobar el permiso de notificaciones.',
      );
    }
  }

  @override
  Future<PermisoNotificaciones> consultarPermiso() =>
      _permiso('consultarPermiso');
  @override
  Future<PermisoNotificaciones> solicitarPermiso() =>
      _permiso('solicitarPermiso');
  @override
  Future<void> abrirAjustes() async {
    await _invocar<void>('abrirAjustes');
  }
}
