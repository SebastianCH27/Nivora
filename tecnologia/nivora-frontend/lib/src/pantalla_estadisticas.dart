import 'package:material_ui/material_ui.dart';

import 'api_nivora.dart';
import 'controlador_consulta.dart';
import 'formatos_consulta.dart';
import 'grafico_mensual.dart';

const _azul = Color(0xFF123D59);
const _turquesa = Color(0xFF087F8C);

class PantallaEstadisticas extends StatelessWidget {
  const PantallaEstadisticas({super.key, required this.controlador});
  final ControladorConsulta controlador;

  @override
  Widget build(BuildContext context) {
    final datos = controlador.estadisticas;
    final ocupado = controlador.consultandoEstadisticas || controlador.saliendo;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text(
          'Tu consumo en cifras',
          style: TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.w700,
            color: _azul,
          ),
        ),
        const SizedBox(height: 12),
        const Text(
          'Revisa los meses anteriores y sus lecturas. El resumen utiliza únicamente '
          'meses con cobertura completa.',
          style: TextStyle(height: 1.5),
        ),
        const SizedBox(height: 20),
        Wrap(
          spacing: 16,
          runSpacing: 12,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            DropdownButton<int>(
              value: controlador.mesesEstadisticas,
              items: [
                for (final meses in [3, 6, 12])
                  DropdownMenuItem(value: meses, child: Text('$meses meses')),
              ],
              onChanged: ocupado
                  ? null
                  : (meses) {
                      if (meses != null) {
                        controlador.cargarEstadisticas(meses: meses);
                      }
                    },
            ),
            OutlinedButton.icon(
              onPressed: ocupado
                  ? null
                  : () => controlador.cargarEstadisticas(),
              icon: const Icon(Icons.refresh),
              label: const Text('Actualizar estadísticas'),
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
                : 'Consultando tus estadísticas…',
          ),
        ],
        if (controlador.errorEstadisticas != null) ...[
          const SizedBox(height: 16),
          Semantics(
            liveRegion: true,
            child: Text(
              controlador.errorEstadisticas!,
              style: const TextStyle(height: 1.5),
            ),
          ),
          if (datos != null)
            const Padding(
              padding: EdgeInsets.only(top: 12),
              child: Text(
                'Se muestran las últimas estadísticas consultadas para este rango.',
              ),
            ),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              onPressed: ocupado
                  ? null
                  : () => controlador.cargarEstadisticas(),
              icon: const Icon(Icons.refresh),
              label: const Text('Reintentar estadísticas'),
            ),
          ),
        ],
        if (datos != null) ...[
          const SizedBox(height: 20),
          Text(
            '${nombreMes(datos.serie.first.periodo)} — ${nombreMes(datos.serie.last.periodo)}',
            style: const TextStyle(fontWeight: FontWeight.w700, color: _azul),
          ),
          const SizedBox(height: 8),
          Text(
            'Consultado: ${fechaLima(datos.consultadoEn)}',
            style: const TextStyle(color: Color(0xFF536976)),
          ),
          const SizedBox(height: 8),
          Text(
            'Mes en curso: ${nombreMes(datos.periodoActual)}. El gráfico y el resumen muestran los meses anteriores.',
            style: const TextStyle(height: 1.5),
          ),
          const SizedBox(height: 20),
          GraficoMensual(periodos: datos.serie),
          const SizedBox(height: 20),
          const Text(
            'Resumen de meses completos',
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w700,
              color: _azul,
            ),
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 16,
            runSpacing: 8,
            children: [
              Text('Completos: ${datos.conteos['COMPLETO']}'),
              Text('Parciales: ${datos.conteos['PARCIAL']}'),
              Text('Sin datos: ${datos.conteos['SIN_DATOS']}'),
              Text('En revisión: ${datos.conteos['INCOMPATIBLE']}'),
            ],
          ),
          const SizedBox(height: 12),
          if (datos.total == null)
            const Card(
              color: Colors.white,
              child: Padding(
                padding: EdgeInsets.all(20),
                child: Text(
                  'Aún no hay meses con cobertura completa para calcular el resumen.',
                  style: TextStyle(height: 1.5),
                ),
              ),
            )
          else
            Wrap(
              spacing: 12,
              runSpacing: 12,
              children: [
                _Cifra(
                  titulo: 'Total de meses completos',
                  cantidad: datos.total!,
                ),
                _Cifra(
                  titulo: 'Promedio por mes completo',
                  cantidad: datos.promedio!,
                ),
                _Cifra(titulo: 'Mínimo mensual', cantidad: datos.minimo!),
                _Cifra(titulo: 'Máximo mensual', cantidad: datos.maximo!),
              ],
            ),
          const SizedBox(height: 12),
          const Text(
            'Los meses parciales conservan su volumen disponible en el gráfico. '
            'Los meses sin datos o con lecturas en revisión se identifican por separado.',
            style: TextStyle(height: 1.5),
          ),
        ],
      ],
    );
  }
}

class _Cifra extends StatelessWidget {
  const _Cifra({required this.titulo, required this.cantidad});
  final String titulo;
  final CantidadEstadistica cantidad;
  @override
  Widget build(BuildContext context) => SizedBox(
    width: 232,
    child: Card(
      color: Colors.white,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              titulo,
              style: const TextStyle(fontWeight: FontWeight.w700, color: _azul),
            ),
            const SizedBox(height: 10),
            Text(
              '${numeroEstadistico(cantidad.metrosCubicos, minimo: 3)} m³',
              style: const TextStyle(
                fontSize: 22,
                fontWeight: FontWeight.w700,
                color: _turquesa,
              ),
            ),
            const SizedBox(height: 6),
            Text('${numeroEstadistico(cantidad.litros)} litros'),
          ],
        ),
      ),
    ),
  );
}
