import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';
import 'package:nivora_frontend/src/dispositivo_notificaciones.dart';

import 'datos_dispositivo_prueba.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const dispositivo = DispositivoNotificacionesNativo();
  const canal = DispositivoNotificacionesNativo.canal;
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  tearDown(() => messenger.setMockMethodCallHandler(canal, null));
  test(
    'identidad canónica y llamadas de permiso y ajustes separadas',
    () async {
      final llamadas = <String>[];
      messenger.setMockMethodCallHandler(canal, (call) async {
        llamadas.add(call.method);
        return switch (call.method) {
          'identificar' => dispositivoId,
          'consultarPermiso' => 'PENDIENTE',
          'solicitarPermiso' => 'CONCEDIDO',
          _ => null,
        };
      });
      expect(await dispositivo.identificar(), dispositivoId);
      expect(await dispositivo.identificar(), dispositivoId);
      expect(
        await dispositivo.consultarPermiso(),
        PermisoNotificaciones.pendiente,
      );
      expect(llamadas, ['identificar', 'identificar', 'consultarPermiso']);
      expect(
        await dispositivo.solicitarPermiso(),
        PermisoNotificaciones.concedido,
      );
      await dispositivo.abrirAjustes();
      expect(llamadas.last, 'abrirAjustes');
    },
  );
  test('error nativo y plataforma sin adaptador son fallos visibles', () async {
    messenger.setMockMethodCallHandler(
      canal,
      (call) async => throw PlatformException(code: 'FALLO'),
    );
    await expectLater(dispositivo.identificar(), throwsA(isA<FalloApi>()));
    messenger.setMockMethodCallHandler(canal, null);
    await expectLater(dispositivo.identificar(), throwsA(isA<FalloApi>()));
  });
  test('rechaza UUID y permiso nativos inválidos', () async {
    messenger.setMockMethodCallHandler(
      canal,
      (call) async => call.method == 'identificar' ? 'ABC' : 'AUTORIZADO',
    );
    await expectLater(dispositivo.identificar(), throwsA(isA<FalloApi>()));
    await expectLater(dispositivo.consultarPermiso(), throwsA(isA<FalloApi>()));
  });
  test(
    'conserva referencia de Windows sin convertir el fallo en permiso',
    () async {
      messenger.setMockMethodCallHandler(
        canal,
        (call) async => throw PlatformException(
          code: 'WINDOWS_NOTIFICACIONES',
          details: {'etapa': 'CREAR_ACCESO', 'hresult': '0x800401F0'},
        ),
      );
      await expectLater(
        dispositivo.solicitarPermiso(),
        throwsA(
          isA<FalloApi>().having(
            (e) => e.mensaje,
            'referencia',
            contains('CREAR_ACCESO/0x800401F0'),
          ),
        ),
      );
      await expectLater(
        dispositivo.consultarPermiso(),
        throwsA(isA<FalloApi>()),
      );
    },
  );
  for (final detalles in <Object?>[
    null,
    'texto',
    {'etapa': 'CREAR_ACCESO', 'hresult': 'invalido'},
    {'etapa': 'DATOS PRIVADOS', 'hresult': '0x80004005'},
  ]) {
    test(
      'un diagnóstico inválido no se muestra como referencia: $detalles',
      () async {
        messenger.setMockMethodCallHandler(
          canal,
          (call) async => throw PlatformException(
            code: 'WINDOWS_NOTIFICACIONES',
            details: detalles,
          ),
        );
        await expectLater(
          dispositivo.solicitarPermiso(),
          throwsA(
            isA<FalloApi>().having(
              (e) => e.mensaje,
              'mensaje',
              isNot(contains('Referencia:')),
            ),
          ),
        );
      },
    );
  }
}
