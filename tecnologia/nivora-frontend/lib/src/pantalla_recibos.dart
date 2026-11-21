import 'package:material_ui/material_ui.dart';

import 'api_nivora.dart';
import 'controlador_consulta.dart';
import 'formatos_consulta.dart';

const _azul = Color(0xFF123D59);
const _turquesa = Color(0xFF087F8C);

class PantallaRecibos extends StatelessWidget {
  const PantallaRecibos({super.key, required this.controlador});
  final ControladorConsulta controlador;

  @override
  Widget build(BuildContext context) {
    final datos = controlador.recibos;
    final corte = controlador.antesDeRecibos;
    final ocupado = controlador.consultandoRecibos || controlador.saliendo;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text(
          'Tus recibos',
          style: TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.w700,
            color: _azul,
          ),
        ),
        const SizedBox(height: 12),
        const Text(
          'Consulta los recibos emitidos por la empresa de agua para tu suministro. '
          'Cada recibo muestra su importe definitivo y fecha de vencimiento.',
          style: TextStyle(height: 1.5),
        ),
        const SizedBox(height: 20),
        Wrap(
          spacing: 12,
          runSpacing: 12,
          children: [
            OutlinedButton.icon(
              onPressed: ocupado ? null : () => controlador.cargarRecibos(),
              icon: const Icon(Icons.refresh),
              label: const Text('Actualizar recibos'),
            ),
            if (corte != null)
              TextButton(
                onPressed: ocupado
                    ? null
                    : () => controlador.cargarRecibos(recientes: true),
                child: const Text('Volver a los más recientes'),
              ),
          ],
        ),
        const SizedBox(height: 16),
        Text(
          corte == null
              ? 'Recibos más recientes · hasta 12 por página'
              : 'Recibos anteriores a ${nombreMes(corte)} · hasta 12 por página',
          style: const TextStyle(height: 1.5),
        ),
        if (ocupado) ...[
          const SizedBox(height: 16),
          const LinearProgressIndicator(),
          const SizedBox(height: 12),
          Text(
            controlador.saliendo
                ? 'Cerrando sesión…'
                : 'Consultando tus recibos…',
          ),
        ],
        if (controlador.errorRecibos != null) ...[
          const SizedBox(height: 16),
          Semantics(
            liveRegion: true,
            child: Text(
              controlador.errorRecibos!,
              style: const TextStyle(height: 1.5),
            ),
          ),
          if (datos != null)
            const Padding(
              padding: EdgeInsets.only(top: 12),
              child: Text(
                'Se muestra la última página de recibos consultada para este rango.',
              ),
            ),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              onPressed: ocupado ? null : () => controlador.cargarRecibos(),
              icon: const Icon(Icons.refresh),
              label: const Text('Reintentar recibos'),
            ),
          ),
        ],
        if (datos != null) ...[
          const SizedBox(height: 20),
          if (datos.recibos.isEmpty)
            Card(
              color: Colors.white,
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Text(
                  corte == null
                      ? 'Todavía no hay recibos guardados para tu suministro.'
                      : 'No hay recibos guardados anteriores a ${nombreMes(corte)}.',
                  style: const TextStyle(height: 1.5),
                ),
              ),
            )
          else ...[
            Text(
              '${datos.recibos.length} ${datos.recibos.length == 1 ? 'recibo' : 'recibos'} en esta página',
              style: const TextStyle(color: Color(0xFF536976)),
            ),
            const SizedBox(height: 12),
            for (final recibo in datos.recibos) ...[
              _TarjetaRecibo(
                key: ValueKey('recibo-${recibo.numero}'),
                recibo: recibo,
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
                    : () => controlador.cargarRecibos(
                        antesDe: datos.siguienteAntesDe,
                      ),
                icon: const Icon(Icons.history),
                label: const Text('Ver recibos anteriores'),
              ),
            )
          else if (datos.recibos.isNotEmpty)
            const Text('No hay más recibos anteriores guardados.'),
        ],
        const SizedBox(height: 20),
        const Text(
          'Para consultar el estado de pago, comunícate con la empresa de agua.',
          style: TextStyle(height: 1.5, color: Color(0xFF536976)),
        ),
      ],
    );
  }
}

class _TarjetaRecibo extends StatelessWidget {
  const _TarjetaRecibo({super.key, required this.recibo});
  final ReciboEmitido recibo;

  @override
  Widget build(BuildContext context) => Card(
    color: Colors.white,
    clipBehavior: Clip.antiAlias,
    child: ExpansionTile(
      title: Text(
        nombreMes(recibo.periodo),
        style: const TextStyle(fontWeight: FontWeight.w700, color: _azul),
      ),
      subtitle: Padding(
        padding: const EdgeInsets.only(top: 10, bottom: 14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Recibo ${recibo.numero}'),
            const SizedBox(height: 8),
            const Text(
              'Importe del recibo',
              style: TextStyle(color: _turquesa, fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 6),
            Text(
              soles(recibo.importeTotal),
              style: const TextStyle(
                fontSize: 26,
                fontWeight: FontWeight.w700,
                color: _azul,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Consumo facturado: ${recibo.metrosCubicos.replaceAll('.', ',')} m³ · '
              '${recibo.litrosParaMostrar.replaceAll('.', ',')} litros',
            ),
            const SizedBox(height: 8),
            Text('Vencimiento: ${fechaCalendario(recibo.vencimiento)}'),
          ],
        ),
      ),
      expandedCrossAxisAlignment: CrossAxisAlignment.stretch,
      childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 20),
      children: [
        const Divider(height: 24),
        const Text(
          'Cargos del recibo',
          style: TextStyle(fontWeight: FontWeight.w700),
        ),
        const SizedBox(height: 12),
        _Cargo(nombre: 'Cargo fijo', importe: recibo.cargoFijo),
        _Cargo(nombre: 'Importe por consumo', importe: recibo.importeConsumo),
        _Cargo(nombre: 'Importe total emitido', importe: recibo.importeTotal),
        const SizedBox(height: 8),
        Text('Disponible desde: ${fechaCalendario(recibo.disponibleDesde)}'),
        const SizedBox(height: 12),
        Text(
          'Tarifa del recibo: ${recibo.codigoTarifa} · versión ${recibo.versionTarifa}',
        ),
        const Divider(height: 32),
        const Text(
          'Periodo facturado',
          style: TextStyle(fontWeight: FontWeight.w700),
        ),
        const SizedBox(height: 8),
        Text(_rango(recibo.facturado), style: const TextStyle(height: 1.5)),
        if (!recibo.coincideConAnalisis) ...[
          const SizedBox(height: 16),
          const Text(
            'El periodo facturado difiere del periodo de análisis de Nivora. '
            'Revisa ambas fechas antes de comparar consumos.',
            style: TextStyle(height: 1.5),
          ),
          const SizedBox(height: 12),
          const Text(
            'Periodo de análisis de Nivora',
            style: TextStyle(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 8),
          Text(_rango(recibo.analisis), style: const TextStyle(height: 1.5)),
        ],
        const SizedBox(height: 16),
        Text(
          'Recibido en Nivora: ${fechaLima(recibo.recibidoEn)}',
          style: const TextStyle(height: 1.5, color: Color(0xFF536976)),
        ),
      ],
    ),
  );
}

String _rango(VentanaRecibo ventana) =>
    'Desde ${fechaCalendario(ventana.inicio.subtract(const Duration(hours: 5)))} '
    'hasta antes del ${fechaCalendario(ventana.fin.subtract(const Duration(hours: 5)))}.';

class _Cargo extends StatelessWidget {
  const _Cargo({required this.nombre, required this.importe});
  final String nombre;
  final String importe;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: Wrap(
      alignment: WrapAlignment.spaceBetween,
      spacing: 16,
      runSpacing: 4,
      children: [Text(nombre), Text(soles(importe))],
    ),
  );
}
