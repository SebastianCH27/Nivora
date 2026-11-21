import 'package:material_ui/material_ui.dart';

import 'api_nivora.dart';
import 'controlador_consulta.dart';
import 'formatos_consulta.dart';

const _azul = Color(0xFF123D59);
const _turquesa = Color(0xFF087F8C);

class PantallaProyeccion extends StatelessWidget {
  const PantallaProyeccion({super.key, required this.controlador});
  final ControladorConsulta controlador;

  @override
  Widget build(BuildContext context) {
    final datos = controlador.proyeccion;
    final ocupado = controlador.consultandoProyeccion || controlador.saliendo;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text(
          'Tu proyección mensual',
          style: TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.w700,
            color: _azul,
          ),
        ),
        const SizedBox(height: 12),
        const Text(
          'Conoce cuánto podrías consumir al finalizar el mes '
          'si mantienes el ritmo de tus lecturas.',
          style: TextStyle(height: 1.5),
        ),
        const SizedBox(height: 16),
        Align(
          alignment: Alignment.centerLeft,
          child: OutlinedButton.icon(
            onPressed: ocupado ? null : () => controlador.cargarProyeccion(),
            icon: const Icon(Icons.refresh),
            label: const Text('Actualizar proyección'),
          ),
        ),
        if (ocupado) ...[
          const SizedBox(height: 16),
          const LinearProgressIndicator(),
          const SizedBox(height: 12),
          Text(
            controlador.saliendo
                ? 'Cerrando sesión…'
                : 'Consultando tu proyección…',
          ),
        ],
        if (controlador.errorProyeccion != null) ...[
          const SizedBox(height: 16),
          Semantics(
            liveRegion: true,
            child: _Bloque(
              children: [
                Text(
                  controlador.errorProyeccion!,
                  style: const TextStyle(height: 1.5),
                ),
              ],
            ),
          ),
          if (datos != null)
            const Padding(
              padding: EdgeInsets.only(top: 12),
              child: Text('Se muestra la última proyección consultada.'),
            ),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              onPressed: ocupado ? null : () => controlador.cargarProyeccion(),
              icon: const Icon(Icons.refresh),
              label: const Text('Reintentar proyección'),
            ),
          ),
        ],
        if (datos != null) ...[
          const SizedBox(height: 20),
          Text(
            nombreMes(datos.periodo),
            style: const TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w700,
              color: _azul,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Calculado: ${fechaLima(datos.calculadoEn)}',
            style: const TextStyle(color: Color(0xFF536976)),
          ),
          const SizedBox(height: 20),
          if (datos.volumen != null) ...[
            _Bloque(
              children: [
                const Text(
                  'Consumo proyectado al cierre',
                  style: TextStyle(
                    color: _turquesa,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 12),
                Text(
                  '${datos.volumen!.metrosCubicos.replaceAll('.', ',')} m³',
                  style: const TextStyle(
                    fontSize: 30,
                    fontWeight: FontWeight.w700,
                    color: _azul,
                  ),
                ),
                Text('${datos.volumen!.litros} litros'),
                const SizedBox(height: 16),
                Text(
                  'Promedio observado: ${datos.volumen!.promedioLitrosPorDia.replaceAll('.', ',')} litros/día.',
                  style: const TextStyle(height: 1.5),
                ),
                const SizedBox(height: 12),
                const Text(
                  'Se mantiene el ritmo promedio observado hasta el final del mes. '
                  'La estimación puede variar con nuevas lecturas.',
                  style: TextStyle(height: 1.5),
                ),
              ],
            ),
            const SizedBox(height: 16),
            _CostoProyeccion(costo: datos.costo!),
            const SizedBox(height: 12),
            const Text(
              'El monto a pagar corresponde al recibo emitido por la empresa de agua.',
              style: TextStyle(height: 1.5, color: Color(0xFF536976)),
            ),
          ] else
            _Bloque(
              children: [
                Text(
                  switch (datos.estado) {
                    'COBERTURA_INCOMPLETA' => 'Falta cobertura para proyectar',
                    'TIEMPO_INSUFICIENTE' => 'Aún falta tiempo de observación',
                    'PERIODO_CERRADO' => 'El periodo ya terminó',
                    _ => 'Proyección no disponible',
                  },
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w700,
                    color: _azul,
                  ),
                ),
                const SizedBox(height: 12),
                Text(switch (datos.estado) {
                  'COBERTURA_INCOMPLETA' =>
                    'Se necesitan lecturas continuas desde el inicio del mes. '
                        'Puede faltar un extremo o haber huecos entre las mediciones.',
                  'TIEMPO_INSUFICIENTE' =>
                    'Se requiere al menos un día completo de lecturas '
                        'desde el inicio del mes para proyectar.',
                  'PERIODO_CERRADO' => 'La medición llegó al cierre del mes. Consulta su consumo en el historial.',
                  _ =>
                    datos.observado.estado == 'INCOMPATIBLE'
                        ? 'Las lecturas necesitan revisión antes de calcular una proyección confiable.'
                        : 'Se necesitan lecturas suficientes del suministro para estimar el cierre del mes.',
                }, style: const TextStyle(height: 1.5)),
              ],
            ),
          const SizedBox(height: 16),
          _Bloque(
            children: [
              const Text(
                'Consumo observado',
                style: TextStyle(fontWeight: FontWeight.w700, color: _azul),
              ),
              const SizedBox(height: 12),
              Text(
                datos.observado.metrosCubicos == null
                    ? 'Sin valor disponible'
                    : '${datos.observado.metrosCubicos!.replaceAll('.', ',')} m³ · ${datos.observado.litros} litros',
                style: const TextStyle(fontSize: 18, color: _azul),
              ),
              const SizedBox(height: 12),
              Text(switch (datos.observado.estado) {
                'COMPLETO' =>
                  'Cobertura desde el inicio del mes hasta la última medición.',
                'PARCIAL' => 'Lecturas parciales.',
                'INCOMPATIBLE' => 'Lecturas en revisión.',
                _ => 'Sin datos suficientes.',
              }, style: const TextStyle(height: 1.5)),
              if (datos.observado.desde != null &&
                  datos.observado.hasta != null) ...[
                const SizedBox(height: 12),
                Text(
                  'Tramo medido: ${fechaLima(datos.observado.desde!)} a ${fechaLima(datos.observado.hasta!)}',
                  style: const TextStyle(height: 1.5),
                ),
              ],
            ],
          ),
          const SizedBox(height: 16),
          Text(
            datos.ultimaLectura == null
                ? 'Última lectura: no disponible'
                : 'Última lectura: ${fechaLima(datos.ultimaLectura!)}',
          ),
          const SizedBox(height: 8),
          Text(switch (datos.actualizacion) {
            'CORTE_CONFIRMADO' =>
              'La fuente confirmó la actualización de las lecturas.',
            'CON_INCIDENCIAS' => 'La actualización de lecturas presenta incidencias. Puedes volver a consultar más tarde.',
            'SIN_LECTURAS' =>
              'Todavía no se recibieron lecturas del suministro.',
            _ => 'Hay lecturas pendientes de confirmación por la fuente.',
          }, style: const TextStyle(height: 1.5, color: Color(0xFF536976))),
        ],
      ],
    );
  }
}

class _CostoProyeccion extends StatelessWidget {
  const _CostoProyeccion({required this.costo});
  final CostoProyectado costo;

  @override
  Widget build(BuildContext context) {
    final desglose = costo.desglose;
    final tarifa = costo.tarifa;
    if (desglose == null) {
      return const _Bloque(
        children: [
          Text(
            'Costo proyectado no disponible',
            style: TextStyle(fontWeight: FontWeight.w700, color: _azul),
          ),
          SizedBox(height: 12),
          Text(
            'Se proyectó el volumen, pero falta una tarifa compatible que cubra todo el mes.',
            style: TextStyle(height: 1.5),
          ),
        ],
      );
    }
    return Card(
      color: Colors.white,
      clipBehavior: Clip.antiAlias,
      child: ExpansionTile(
        title: Text(
          desglose.totalEstimado == null
              ? 'Subtotal proyectado conocido'
              : 'Total proyectado estimado',
          style: const TextStyle(fontWeight: FontWeight.w700, color: _turquesa),
        ),
        subtitle: Padding(
          padding: const EdgeInsets.symmetric(vertical: 12),
          child: Text(
            soles(desglose.totalEstimado ?? desglose.subtotalConocido),
            style: const TextStyle(
              fontSize: 28,
              fontWeight: FontWeight.w700,
              color: _azul,
            ),
          ),
        ),
        expandedCrossAxisAlignment: CrossAxisAlignment.stretch,
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 20),
        children: [
          if (desglose.totalEstimado == null)
            const Padding(
              padding: EdgeInsets.only(bottom: 16),
              child: Text(
                'Faltan conceptos para estimar el total proyectado. '
                'El subtotal incluye únicamente los importes disponibles.',
                style: TextStyle(height: 1.5),
              ),
            ),
          _Importe(nombre: 'Agua', importe: desglose.agua),
          _Importe(nombre: 'Alcantarillado', importe: desglose.alcantarillado),
          _Importe(nombre: 'Cargo fijo mensual', importe: desglose.cargoFijo),
          _Importe(
            nombre: 'Subtotal conocido',
            importe: desglose.subtotalConocido,
          ),
          _Importe(nombre: 'IGV', importe: desglose.igv),
          _Importe(nombre: 'Total estimado', importe: desglose.totalEstimado),
          const Divider(height: 32),
          const Text(
            'Tarifa aplicada',
            style: TextStyle(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 8),
          Text(
            '${tarifa!.nombre} · ${tarifa.codigo} · versión ${tarifa.version}',
          ),
          const SizedBox(height: 8),
          Text(
            'Desde ${fechaCalendario(tarifa.desde)}'
            '${tarifa.hasta == null ? '' : ' hasta antes del ${fechaCalendario(tarifa.hasta!)}'}',
          ),
        ],
      ),
    );
  }
}

class _Bloque extends StatelessWidget {
  const _Bloque({required this.children});
  final List<Widget> children;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(20),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(20),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: children,
    ),
  );
}

class _Importe extends StatelessWidget {
  const _Importe({required this.nombre, required this.importe});
  final String nombre;
  final String? importe;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: Wrap(
      alignment: WrapAlignment.spaceBetween,
      spacing: 16,
      runSpacing: 4,
      children: [
        Text(nombre),
        Text(importe == null ? 'No disponible' : soles(importe!)),
      ],
    ),
  );
}
