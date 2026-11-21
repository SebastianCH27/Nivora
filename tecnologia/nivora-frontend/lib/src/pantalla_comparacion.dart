import 'package:material_ui/material_ui.dart';

import 'api_nivora.dart';
import 'controlador_consulta.dart';
import 'formatos_consulta.dart';
import 'pantalla_costos.dart';

const _azul = Color(0xFF123D59);
const _turquesa = Color(0xFF087F8C);

class PantallaComparacion extends StatelessWidget {
  const PantallaComparacion({super.key, required this.controlador});
  final ControladorConsulta controlador;

  @override
  Widget build(BuildContext context) {
    final datos = controlador.comparacion;
    final ocupado = controlador.consultandoComparacion || controlador.saliendo;
    final actual = controlador.actualComparacion;
    final opciones = actual == null
        ? <String>[]
        : [for (var i = 0; i <= 12; i++) moverMes(actual, -i)]
              .where(periodoComparacionValido)
              .toList();
    Widget selector(
      String etiqueta,
      String? seleccionado,
      void Function(String?) cambiar,
    ) => SizedBox(
      width: 260,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            etiqueta,
            style: const TextStyle(fontWeight: FontWeight.w700, color: _azul),
          ),
          DropdownButton<String>(
            isExpanded: true,
            value: opciones.contains(seleccionado) ? seleccionado : null,
            hint: const Text('Elige un mes'),
            items: [
              for (final mes in opciones)
                DropdownMenuItem(
                  value: mes,
                  child: Text(nombreMes(mes), overflow: TextOverflow.ellipsis),
                ),
            ],
            onChanged: ocupado || opciones.isEmpty ? null : cambiar,
          ),
        ],
      ),
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text(
          'Compara tus meses',
          style: TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.w700,
            color: _azul,
          ),
        ),
        const SizedBox(height: 12),
        const Text(
          'Elige un mes base y otro para comparar su consumo y costo estimado. '
          'Las diferencias se calculan como mes comparado menos mes base.',
          style: TextStyle(height: 1.5),
        ),
        const SizedBox(height: 20),
        Wrap(
          spacing: 24,
          runSpacing: 16,
          children: [
            selector(
              'Mes base',
              controlador.baseComparacion,
              (valor) => controlador.seleccionarComparacion(periodoBase: valor),
            ),
            selector(
              'Mes comparado',
              controlador.comparadoComparacion,
              (valor) =>
                  controlador.seleccionarComparacion(periodoComparado: valor),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 12,
          runSpacing: 12,
          children: [
            FilledButton.icon(
              onPressed:
                  ocupado ||
                      controlador.baseComparacion == null ||
                      controlador.comparadoComparacion == null
                  ? null
                  : () => controlador.cargarComparacion(),
              icon: const Icon(Icons.compare_arrows),
              label: const Text('Comparar meses'),
            ),
            TextButton(
              onPressed: ocupado
                  ? null
                  : () => controlador.cargarComparacion(predeterminada: true),
              child: const Text('Actual y anterior'),
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
                : 'Consultando tu comparación…',
          ),
        ],
        if (controlador.errorComparacion != null) ...[
          const SizedBox(height: 16),
          Semantics(
            liveRegion: true,
            child: _Bloque(children: [Text(controlador.errorComparacion!)]),
          ),
          if (datos != null)
            const Padding(
              padding: EdgeInsets.only(top: 12),
              child: Text(
                'Se muestra la última comparación consultada para estos meses.',
              ),
            ),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              onPressed: ocupado ? null : () => controlador.cargarComparacion(),
              icon: const Icon(Icons.refresh),
              label: const Text('Reintentar comparación'),
            ),
          ),
        ],
        if (datos == null &&
            !ocupado &&
            controlador.errorComparacion == null &&
            actual != null)
          const Padding(
            padding: EdgeInsets.only(top: 16),
            child: Text('Pulsa Comparar meses para consultar la selección.'),
          ),
        if (datos != null) ...[
          const SizedBox(height: 20),
          Text(
            'Consultado: ${fechaLima(datos.consultadoEn)}',
            style: const TextStyle(color: Color(0xFF536976)),
          ),
          const SizedBox(height: 16),
          _Bloque(
            children: [
              Text(
                switch (datos.estado) {
                  'COMPLETA' => 'Comparación completa',
                  'PARCIAL' => 'Comparación parcial',
                  _ =>
                    datos.diferenciaLitros == null
                        ? 'Comparación no disponible'
                        : 'Comparación de costo no disponible',
                },
                style: const TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w700,
                  color: _azul,
                ),
              ),
              const SizedBox(height: 12),
              Text(
                '${nombreMes(datos.comparado.estimacion.periodo)} menos ${nombreMes(datos.base.estimacion.periodo)}',
                style: const TextStyle(height: 1.5),
              ),
              const SizedBox(height: 16),
              Text(
                switch (datos.sentidoConsumo) {
                  1 => 'Mayor volumen medido',
                  -1 => 'Menor volumen medido',
                  0 => 'Mismo volumen medido',
                  _ => 'Diferencia de consumo no disponible',
                },
                style: const TextStyle(
                  color: _turquesa,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 8),
              if (datos.diferenciaLitros != null) ...[
                Text(
                  '${_cantidad(datos.diferenciaMetrosCubicos!, datos.sentidoConsumo!)} m³',
                  style: const TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.w700,
                    color: _azul,
                  ),
                ),
                Text(
                  '${_cantidad(datos.diferenciaLitros!, datos.sentidoConsumo!, decimal: false)} litros',
                ),
              ] else
                const Text(
                  'Faltan lecturas calculables en alguno de los meses.',
                ),
              const Divider(height: 32),
              const Text(
                'Diferencia de costo estimado',
                style: TextStyle(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 8),
              Text(
                datos.diferenciaCosto == null
                    ? 'No disponible'
                    : '${_signo(datos.sentidoCosto!)}${soles(_absoluto(datos.diferenciaCosto!))}',
                style: const TextStyle(
                  fontSize: 24,
                  fontWeight: FontWeight.w700,
                  color: _azul,
                ),
              ),
              if (datos.diferenciaCosto == null)
                const Padding(
                  padding: EdgeInsets.only(top: 8),
                  child: Text(
                    'Se necesitan consumos calculables y ambos totales estimados. '
                    'Los subtotales con conceptos faltantes se muestran en el detalle de cada mes.',
                    style: TextStyle(height: 1.5),
                  ),
                ),
              if (datos.base.estimacion.enCurso ||
                  datos.comparado.estimacion.enCurso)
                const Padding(
                  padding: EdgeInsets.only(top: 16),
                  child: Text(
                    'Uno de los meses sigue en curso. Su volumen corresponde a las lecturas disponibles '
                    'hasta el momento de la consulta.',
                    style: TextStyle(height: 1.5),
                  ),
                ),
              if (datos.mismaDuracion == false)
                const Padding(
                  padding: EdgeInsets.only(top: 12),
                  child: Text(
                    'Las lecturas abarcan tiempos distintos. Revisa las fechas antes de interpretar '
                    'el cambio como una variación del ritmo de consumo.',
                    style: TextStyle(height: 1.5),
                  ),
                ),
              if (datos.mismaTarifa == false)
                const Padding(
                  padding: EdgeInsets.only(top: 12),
                  child: Text(
                    'Se aplicaron tarifas distintas. El cambio de importe también puede deberse a la tarifa.',
                    style: TextStyle(height: 1.5),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 20),
          _Periodo(
            titulo: 'Detalle del mes base',
            periodo: datos.base,
            clave: 'base',
          ),
          const SizedBox(height: 20),
          _Periodo(
            titulo: 'Detalle del mes comparado',
            periodo: datos.comparado,
            clave: 'comparado',
          ),
          const SizedBox(height: 16),
          const Text(
            'La comparación usa los volúmenes medidos y los costos estimados disponibles. '
            'No ajusta los meses al mismo número de días. El monto a pagar corresponde al recibo emitido.',
            style: TextStyle(height: 1.5, color: Color(0xFF536976)),
          ),
        ],
      ],
    );
  }
}

String _absoluto(String valor) =>
    valor.startsWith('-') ? valor.substring(1) : valor;
String _signo(int signo) => signo > 0
    ? '+'
    : signo < 0
    ? '−'
    : '';
String _cantidad(String valor, int signo, {bool decimal = true}) =>
    '${_signo(signo)}${decimal ? _absoluto(valor).replaceAll('.', ',') : _absoluto(valor)}';

class _Periodo extends StatelessWidget {
  const _Periodo({
    required this.titulo,
    required this.periodo,
    required this.clave,
  });
  final String titulo;
  final PeriodoComparacion periodo;
  final String clave;
  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Text(
        titulo,
        style: const TextStyle(
          fontSize: 18,
          fontWeight: FontWeight.w700,
          color: _azul,
        ),
      ),
      const SizedBox(height: 8),
      Text(
        'Periodo: desde ${fechaCalendario(periodo.inicio.subtract(const Duration(hours: 5)))} '
        'hasta antes del ${fechaCalendario(periodo.fin.subtract(const Duration(hours: 5)))}.',
        style: const TextStyle(height: 1.5),
      ),
      const SizedBox(height: 8),
      Text(
        periodo.duracionTramo == null
            ? 'Tramo medido: no disponible'
            : 'Tiempo entre mediciones: ${_duracion(periodo.duracionTramo!)}.',
      ),
      const SizedBox(height: 12),
      TarjetaCosto(
        key: ValueKey('comparacion-$clave'),
        costo: periodo.estimacion,
      ),
    ],
  );
}

String _duracion(Duration valor) =>
    '${valor.inDays} días, ${valor.inHours % 24} h y ${valor.inMinutes % 60} min';

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
