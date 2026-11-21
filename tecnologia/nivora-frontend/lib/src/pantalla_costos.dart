import 'package:material_ui/material_ui.dart';

import 'api_nivora.dart';
import 'controlador_consulta.dart';
import 'formatos_consulta.dart';

const _azul = Color(0xFF123D59);
const _turquesa = Color(0xFF087F8C);

class PantallaCostos extends StatelessWidget {
  const PantallaCostos({super.key, required this.controlador});
  final ControladorConsulta controlador;

  @override
  Widget build(BuildContext context) {
    final datos = controlador.costos;
    final ocupado = controlador.consultandoCostos || controlador.saliendo;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text(
          'El costo de tu consumo',
          style: TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.w700,
            color: _azul,
          ),
        ),
        const SizedBox(height: 12),
        const Text(
          'Estimaciones en soles según las lecturas y la tarifa aplicable. '
          'El monto a pagar corresponde al recibo emitido por la empresa de agua.',
          style: TextStyle(height: 1.5, color: Color(0xFF536976)),
        ),
        const SizedBox(height: 16),
        Wrap(
          spacing: 16,
          runSpacing: 12,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            SizedBox(
              width: 240,
              child: DropdownButton<int>(
                isExpanded: true,
                value: controlador.mesesCostos,
                items: [
                  for (final meses in [3, 6, 12])
                    DropdownMenuItem(
                      value: meses,
                      child: Text(
                        '$meses meses anteriores',
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                ],
                onChanged: ocupado
                    ? null
                    : (meses) {
                        if (meses != null) {
                          controlador.cargarCostos(meses: meses);
                        }
                      },
              ),
            ),
            OutlinedButton.icon(
              onPressed: ocupado ? null : () => controlador.cargarCostos(),
              icon: const Icon(Icons.refresh),
              label: const Text('Actualizar costos'),
            ),
          ],
        ),
        if (ocupado) ...[
          const SizedBox(height: 16),
          const LinearProgressIndicator(),
          const SizedBox(height: 12),
          Text(
            controlador.saliendo
                ? 'Cerrando sesión…'
                : 'Consultando tus costos…',
          ),
        ],
        if (controlador.errorCostos != null) ...[
          const SizedBox(height: 16),
          Semantics(
            liveRegion: true,
            child: Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: const Color(0xFFF0F6F8),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(
                controlador.errorCostos!,
                style: const TextStyle(height: 1.5, color: _azul),
              ),
            ),
          ),
          if (datos != null)
            const Padding(
              padding: EdgeInsets.only(top: 12),
              child: Text(
                'Se muestran los últimos costos consultados para este rango.',
              ),
            ),
          TextButton.icon(
            onPressed: ocupado ? null : () => controlador.cargarCostos(),
            icon: const Icon(Icons.refresh),
            label: const Text('Reintentar costos'),
          ),
        ],
        if (datos != null) ...[
          const SizedBox(height: 20),
          Text(
            'Consultado: ${fechaLima(datos.consultadoEn)}',
            style: const TextStyle(color: Color(0xFF536976)),
          ),
          const SizedBox(height: 20),
          const Text(
            'Mes en curso',
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w700,
              color: _azul,
            ),
          ),
          const SizedBox(height: 12),
          _TarjetaCosto(
            key: ValueKey('costo-${datos.actual.periodo}'),
            costo: datos.actual,
          ),
          const SizedBox(height: 24),
          Text(
            'Últimos ${controlador.mesesCostos} meses terminados',
            style: const TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w700,
              color: _azul,
            ),
          ),
          const SizedBox(height: 12),
          for (final costo in datos.anteriores) ...[
            _TarjetaCosto(
              key: ValueKey('costo-${costo.periodo}'),
              costo: costo,
            ),
            const SizedBox(height: 12),
          ],
        ],
      ],
    );
  }
}

class _TarjetaCosto extends StatelessWidget {
  const _TarjetaCosto({super.key, required this.costo});
  final PeriodoCosto costo;

  @override
  Widget build(BuildContext context) {
    final desglose = costo.desglose;
    final tarifa = costo.tarifa;
    final importe = desglose?.totalEstimado ?? desglose?.subtotalConocido;
    final etiqueta = switch (costo.estado) {
      'ESTIMADO' => 'Total estimado',
      'CONCEPTOS_INCOMPLETOS' => 'Subtotal conocido',
      'SIN_TARIFA_COMPATIBLE' => 'Sin tarifa compatible',
      _ => 'Sin consumo calculable',
    };
    final explicacion = switch (costo.estado) {
      'ESTIMADO' => 'Incluye los conceptos informados por la tarifa aplicable.',
      'CONCEPTOS_INCOMPLETOS' =>
        'Faltan conceptos para estimar el total. '
            'El subtotal incluye únicamente los importes disponibles.',
      'SIN_TARIFA_COMPATIBLE' =>
        'Hay consumo medido, pero no una tarifa que cubra todo el mes.',
      _ =>
        costo.cobertura == 'INCOMPATIBLE'
            ? 'Las lecturas están en revisión y no permiten calcular un consumo confiable.'
            : 'Se necesitan lecturas suficientes para calcular el consumo y su costo.',
    };
    final cobertura = switch (costo.cobertura) {
      'COMPLETO' =>
        costo.enCurso ? 'Cobertura hasta la última lectura' : 'Mes completo',
      'PARCIAL' => 'Consumo parcial',
      'INCOMPATIBLE' => 'Lecturas en revisión',
      _ => 'Sin datos suficientes',
    };
    return Card(
      color: Colors.white,
      clipBehavior: Clip.antiAlias,
      child: ExpansionTile(
        title: Text(
          nombreMes(costo.periodo),
          style: const TextStyle(fontWeight: FontWeight.w700, color: _azul),
        ),
        subtitle: Padding(
          padding: const EdgeInsets.only(top: 10, bottom: 14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                etiqueta,
                style: const TextStyle(
                  color: _turquesa,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                importe == null ? 'Importe no disponible' : soles(importe),
                style: const TextStyle(
                  fontSize: 26,
                  fontWeight: FontWeight.w700,
                  color: _azul,
                ),
              ),
              if (costo.metrosCubicos != null) ...[
                const SizedBox(height: 8),
                Text(
                  '${costo.metrosCubicos!.replaceAll('.', ',')} m³ · ${costo.litros} litros',
                ),
              ],
              const SizedBox(height: 8),
              Text(cobertura),
              if (costo.cobertura == 'PARCIAL')
                const Padding(
                  padding: EdgeInsets.only(top: 8),
                  child: Text(
                    'Importe sobre el consumo conocido. Puede faltar parte del mes.',
                    style: TextStyle(height: 1.5),
                  ),
                ),
            ],
          ),
        ),
        expandedCrossAxisAlignment: CrossAxisAlignment.stretch,
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 20),
        children: [
          Text(explicacion, style: const TextStyle(height: 1.5)),
          if (desglose != null) ...[
            const Divider(height: 32),
            _Concepto(nombre: 'Agua', importe: desglose.agua),
            _Concepto(
              nombre: 'Alcantarillado',
              importe: desglose.alcantarillado,
            ),
            _Concepto(
              nombre: 'Cargo fijo mensual',
              importe: desglose.cargoFijo,
            ),
            _Concepto(
              nombre: 'Subtotal conocido',
              importe: desglose.subtotalConocido,
            ),
            _Concepto(nombre: 'IGV', importe: desglose.igv),
            _Concepto(
              nombre: 'Total estimado',
              importe: desglose.totalEstimado,
            ),
            if (desglose.alcantarillado == null)
              const Text(
                'Alcantarillado: la tarifa no informa el importe.',
                style: TextStyle(height: 1.5),
              ),
            if (desglose.igv == null)
              Text(
                tarifa!.igvPorcentaje == null
                    ? 'IGV: la tarifa no informa la tasa.'
                    : 'IGV: pendiente de completar los conceptos del subtotal.',
                style: const TextStyle(height: 1.5),
              ),
            const SizedBox(height: 8),
            const Text(
              'El cargo fijo corresponde al mes completo.',
              style: TextStyle(height: 1.5, color: Color(0xFF536976)),
            ),
          ],
          if (tarifa != null) ...[
            const Divider(height: 32),
            const Text(
              'Tarifa aplicada',
              style: TextStyle(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 8),
            Text(
              '${tarifa.nombre} · ${tarifa.codigo} · versión ${tarifa.version}',
            ),
            const SizedBox(height: 8),
            Text(
              'Origen: ${tarifa.fuente == 'empresa-agua' ? 'Empresa de Agua' : 'Nivora'}',
            ),
            const SizedBox(height: 8),
            Text(
              'Desde ${fechaCalendario(tarifa.desde)}'
              '${tarifa.hasta == null ? '' : ' hasta antes del ${fechaCalendario(tarifa.hasta!)}'}',
            ),
          ],
          if (costo.desde != null && costo.hasta != null) ...[
            const SizedBox(height: 16),
            Text(
              'Tramo medido: ${fechaLima(costo.desde!)} a ${fechaLima(costo.hasta!)}',
              style: const TextStyle(height: 1.5),
            ),
          ],
        ],
      ),
    );
  }
}

class _Concepto extends StatelessWidget {
  const _Concepto({required this.nombre, required this.importe});
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
