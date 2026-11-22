import 'package:flutter/foundation.dart'
    show defaultTargetPlatform, TargetPlatform;
import 'package:material_ui/material_ui.dart';

import 'api_nivora.dart';
import 'controlador_consulta.dart';
import 'formatos_consulta.dart';

class ConfiguracionNotificaciones extends StatelessWidget {
  const ConfiguracionNotificaciones({super.key, required this.controlador});
  final ControladorConsulta controlador;

  @override
  Widget build(BuildContext context) {
    final c = controlador;
    final ocupada = c.configurandoNotificaciones || c.saliendo;
    final preferencia = c.preferenciaNotificaciones;
    final concedido = c.permisoDispositivo == PermisoNotificaciones.concedido;
    final etiquetaEstado = c.configuracionConfirmada
        ? 'Estado guardado'
        : 'Último estado confirmado';
    return Card(
      child: ExpansionTile(
        title: const Text('Configurar notificaciones'),
        onExpansionChanged: (abierta) {
          if (abierta) c.configurarNotificaciones();
        },
        expandedCrossAxisAlignment: CrossAxisAlignment.stretch,
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 20),
        children: [
          Text('Suministro: ${c.sesion?.suministro ?? ""}'),
          const SizedBox(height: 12),
          const Text(
            'Esta preferencia corresponde a esta instalación y a este suministro. '
            'Los otros dispositivos conservan su propia configuración.',
          ),
          const SizedBox(height: 12),
          Text(
            'Permiso del sistema: ${c.permisoDispositivo?.etiqueta ?? "Sin comprobar"}',
          ),
          if (ocupada) ...[
            const SizedBox(height: 12),
            const LinearProgressIndicator(),
            const Text('Comprobando configuración…'),
          ],
          if (c.errorConfiguracion != null) ...[
            const SizedBox(height: 12),
            Semantics(liveRegion: true, child: Text(c.errorConfiguracion!)),
            const Text(
              'El estado guardado puede haber cambiado. Actualiza antes de volver a activarlo.',
            ),
          ],
          if (preferencia != null) ...[
            const SizedBox(height: 12),
            Text(
              '$etiquetaEstado: ${preferencia.habilitadas ? 'activadas' : 'desactivadas'}',
            ),
            Text('Último cambio: ${fechaLima(preferencia.actualizadaEn)}'),
          ],
          const SizedBox(height: 12),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Activar para este suministro'),
            subtitle: Text(
              concedido
                  ? 'Guarda la activación en este dispositivo.'
                  : 'Primero concede el permiso del sistema.',
            ),
            // Comprobar bloquea la edición, pero conserva el estado conocido.
            // Una revocación del permiso sí apaga el control inmediatamente.
            value: concedido && preferencia?.habilitadas == true,
            onChanged: ocupada || !c.configuracionConfirmada || !concedido
                ? null
                : (valor) => c.configurarNotificaciones(habilitar: valor),
          ),
          if (defaultTargetPlatform == TargetPlatform.windows) ...[
            const Text(
              'En Windows no aparece un diálogo de permiso. '
              'Solicitar permiso registra Nivora como emisor. Si Windows aún no conoce su estado, puede mostrar un aviso de prueba. Después pulsa Actualizar configuración.',
            ),
            const SizedBox(height: 12),
          ],
          Wrap(
            spacing: 12,
            runSpacing: 8,
            children: [
              if (!concedido)
                OutlinedButton(
                  onPressed: ocupada
                      ? null
                      : () => c.configurarNotificaciones(solicitar: true),
                  child: const Text('Solicitar permiso'),
                ),
              OutlinedButton(
                onPressed: ocupada ? null : c.abrirAjustesNotificaciones,
                child: const Text('Abrir ajustes del sistema'),
              ),
              TextButton(
                onPressed: ocupada ? null : () => c.configurarNotificaciones(),
                child: const Text('Actualizar configuración'),
              ),
            ],
          ),
          if (c.mensajeConfiguracion != null)
            Semantics(liveRegion: true, child: Text(c.mensajeConfiguracion!)),
          const SizedBox(height: 12),
          const Text(
            'La bandeja sigue disponible aunque desactives esta preferencia. '
            'La recepción de avisos con la aplicación cerrada se incorporará en un bloque posterior.',
          ),
        ],
      ),
    );
  }
}
