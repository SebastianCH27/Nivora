import 'package:material_ui/material_ui.dart';

import 'api_nivora.dart';
import 'configuracion_notificaciones.dart';
import 'controlador_consulta.dart';
import 'formatos_consulta.dart';

const _azul = Color(0xFF123D59);
const _turquesa = Color(0xFF087F8C);

class PantallaNotificaciones extends StatelessWidget {
  const PantallaNotificaciones({super.key, required this.controlador});
  final ControladorConsulta controlador;

  @override
  Widget build(BuildContext context) {
    final control = controlador;
    final datos = control.notificaciones;
    final ocupado = control.ocupadaBandeja;
    final pendientes = control.soloPendientesNotificaciones;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text(
          'Tus notificaciones',
          style: TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.w700,
            color: _azul,
          ),
        ),
        const SizedBox(height: 12),
        const Text(
          'Revisa las alertas de consumo y los avisos de recibos de tu suministro. '
          'La marca de lectura se comparte entre quienes consultan el mismo suministro.',
          style: TextStyle(height: 1.5),
        ),
        const SizedBox(height: 20),
        ConfiguracionNotificaciones(controlador: control),
        const SizedBox(height: 20),
        Wrap(
          spacing: 12,
          runSpacing: 12,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 280),
              child: DropdownButton<bool>(
                isExpanded: true,
                itemHeight: null,
                value: pendientes,
                hint: const Text('Filtrar notificaciones'),
                items: const [
                  DropdownMenuItem(
                    value: false,
                    child: Text('Todos los avisos'),
                  ),
                  DropdownMenuItem(value: true, child: Text('Solo pendientes')),
                ],
                onChanged: ocupado
                    ? null
                    : (valor) {
                        if (valor != null && valor != pendientes) {
                          control.cargarNotificaciones(soloPendientes: valor);
                        }
                      },
              ),
            ),
            OutlinedButton.icon(
              onPressed: ocupado ? null : () => control.cargarNotificaciones(),
              icon: const Icon(Icons.refresh),
              label: const Text('Actualizar notificaciones'),
            ),
          ],
        ),
        const SizedBox(height: 16),
        const Text(
          'Del más reciente al más antiguo · hasta 20 por consulta',
          style: TextStyle(height: 1.5, color: Color(0xFF536976)),
        ),
        if (control.consultandoNotificaciones || control.saliendo) ...[
          const SizedBox(height: 16),
          const LinearProgressIndicator(),
          const SizedBox(height: 12),
          Text(
            control.saliendo
                ? 'Cerrando sesión…'
                : control.ampliandoNotificaciones
                ? 'Buscando más avisos…'
                : 'Consultando tus notificaciones…',
          ),
        ],
        if (control.errorNotificaciones != null) ...[
          const SizedBox(height: 16),
          Semantics(
            liveRegion: true,
            child: Text(
              control.errorNotificaciones!,
              style: const TextStyle(height: 1.5),
            ),
          ),
          if (datos != null) ...[
            const SizedBox(height: 8),
            Text(
              control.errorAlAmpliarNotificaciones
                  ? 'Se conservan los avisos cargados. No se pudieron consultar los siguientes.'
                  : 'Se muestran los últimos avisos consultados con este filtro.',
              style: const TextStyle(height: 1.5),
            ),
          ],
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              onPressed: ocupado
                  ? null
                  : () => control.cargarNotificaciones(
                      mas: control.errorAlAmpliarNotificaciones,
                    ),
              icon: const Icon(Icons.refresh),
              label: const Text('Reintentar notificaciones'),
            ),
          ),
        ],
        if (control.errorMarcarNotificacion != null) ...[
          const SizedBox(height: 16),
          Semantics(
            liveRegion: true,
            child: Text(
              control.errorMarcarNotificacion!,
              style: const TextStyle(height: 1.5),
            ),
          ),
        ],
        if (control.mensajeNotificaciones != null) ...[
          const SizedBox(height: 16),
          Semantics(
            liveRegion: true,
            child: Text(
              control.mensajeNotificaciones!,
              style: const TextStyle(color: _turquesa, height: 1.5),
            ),
          ),
        ],
        if (datos != null) ...[
          const SizedBox(height: 20),
          if (datos.avisos.isEmpty)
            Card(
              color: Colors.white,
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Text(
                  pendientes
                      ? datos.hayMas
                            ? 'No quedan pendientes entre los avisos cargados. Consulta más avisos para continuar.'
                            : 'No hay avisos pendientes para tu suministro.'
                      : 'Todavía no hay notificaciones guardadas para tu suministro.',
                  style: const TextStyle(height: 1.5),
                ),
              ),
            )
          else ...[
            Text(
              'Avisos mostrados: ${datos.avisos.length}',
              style: const TextStyle(color: Color(0xFF536976)),
            ),
            const SizedBox(height: 12),
            for (final aviso in datos.avisos) ...[
              _TarjetaNotificacion(
                key: ValueKey('aviso-${aviso.id}'),
                aviso: aviso,
                ocupado: ocupado,
                marcando: control.marcandoNotificacion == aviso.id,
                marcar: () => control.marcarNotificacionLeida(aviso.id),
              ),
              const SizedBox(height: 12),
            ],
          ],
          if (datos.hayMas)
            Align(
              alignment: Alignment.centerLeft,
              child: OutlinedButton.icon(
                onPressed: ocupado
                    ? null
                    : () => control.cargarNotificaciones(mas: true),
                icon: const Icon(Icons.history),
                label: const Text('Cargar más avisos'),
              ),
            )
          else if (datos.avisos.isNotEmpty)
            const Text('No hay más avisos para este filtro.'),
        ],
      ],
    );
  }
}

class _TarjetaNotificacion extends StatelessWidget {
  const _TarjetaNotificacion({
    super.key,
    required this.aviso,
    required this.ocupado,
    required this.marcando,
    required this.marcar,
  });
  final NotificacionConsulta aviso;
  final bool ocupado;
  final bool marcando;
  final VoidCallback marcar;

  @override
  Widget build(BuildContext context) {
    final consumo = aviso.consumo;
    final recibo = aviso.recibo;
    return Card(
      color: Colors.white,
      clipBehavior: Clip.antiAlias,
      child: ExpansionTile(
        title: Text(
          aviso.titulo,
          style: const TextStyle(fontWeight: FontWeight.w700, color: _azul),
        ),
        subtitle: Padding(
          padding: const EdgeInsets.only(top: 8, bottom: 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(nombreMes(aviso.periodo)),
              const SizedBox(height: 8),
              Text(
                aviso.leida ? 'Leída' : 'Pendiente',
                style: const TextStyle(
                  color: _turquesa,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 8),
              Text('Registrada: ${fechaLima(aviso.registradaEn)}'),
            ],
          ),
        ),
        expandedCrossAxisAlignment: CrossAxisAlignment.stretch,
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 20),
        children: [
          const Divider(height: 24),
          if (consumo != null) ...[
            const Text(
              'Consumo al detectarse la alerta',
              style: TextStyle(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 12),
            Text(
              '${nombreMes(aviso.periodo)}: '
              '${metrosCubicosNotificacion(consumo.actualLitros)} m³ · ${consumo.actualLitros} litros',
              style: const TextStyle(height: 1.5),
            ),
            const SizedBox(height: 8),
            Text(
              '${nombreMes(moverMes(aviso.periodo, -1))}: '
              '${metrosCubicosNotificacion(consumo.anteriorLitros)} m³ · ${consumo.anteriorLitros} litros',
              style: const TextStyle(height: 1.5),
            ),
            const SizedBox(height: 12),
            const Text(
              'Los valores corresponden al momento de la alerta. '
              'Consulta Consumo actual para revisar las lecturas más recientes.',
              style: TextStyle(height: 1.5),
            ),
          ],
          if (recibo != null) ...[
            Text(
              'Recibo ${recibo.numero}',
              style: const TextStyle(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 12),
            Text(
              'Disponible desde: ${fechaCalendario(recibo.disponibleDesde)}',
            ),
            const SizedBox(height: 8),
            Text('Vencimiento: ${fechaCalendario(recibo.vencimiento)}'),
            const SizedBox(height: 12),
            const Text(
              'Consulta Recibos para revisar el importe y los cargos emitidos. '
              'Marcar este aviso como leído no cambia el estado de pago del recibo.',
              style: TextStyle(height: 1.5),
            ),
          ],
          if (aviso.leidaEn != null) ...[
            const SizedBox(height: 16),
            Text(
              'Leída: ${fechaLima(aviso.leidaEn!)}',
              style: const TextStyle(height: 1.5),
            ),
          ],
          if (!aviso.leida) ...[
            const SizedBox(height: 16),
            Align(
              alignment: Alignment.centerLeft,
              child: OutlinedButton.icon(
                key: ValueKey('marcar-${aviso.id}'),
                onPressed: ocupado ? null : marcar,
                icon: const Icon(Icons.done),
                label: Text(
                  marcando ? 'Confirmando lectura…' : 'Marcar como leída',
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
