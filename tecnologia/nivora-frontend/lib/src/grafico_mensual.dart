import 'package:material_ui/material_ui.dart';

import 'modelos_estadisticas.dart';
import 'formatos_consulta.dart';

const _azul = Color(0xFF123D59);
const _turquesa = Color(0xFF087F8C);
const _parcial = Color(0xFF986900);

// El formato elimina únicamente ceros finales; no redondea cantidades.
String numeroEstadistico(String valor, {int minimo = 0}) {
  final partes = valor.split('.');
  if (partes.length == 1) return valor;
  var fraccion = partes[1];
  while (fraccion.length > minimo && fraccion.endsWith('0')) {
    fraccion = fraccion.substring(0, fraccion.length - 1);
  }
  return fraccion.isEmpty ? partes.first : '${partes.first},$fraccion';
}

String _m3(BigInt litros) => numeroEstadistico(
  '${litros ~/ BigInt.from(1000)}.${(litros % BigInt.from(1000)).toString().padLeft(3, '0')}',
);

String _etiqueta(MesEstadistico mes) => switch (mes.consumo.estado) {
  'COMPLETO' => 'Mes completo',
  'PARCIAL' => 'Consumo parcial',
  'INCOMPATIBLE' => 'Lecturas en revisión',
  _ => 'Sin datos suficientes',
};

BigInt _paso(BigInt maximo) {
  if (maximo == BigInt.zero) return BigInt.from(250);
  final deseado = (maximo + BigInt.from(3)) ~/ BigInt.from(4);
  var potencia = BigInt.one;
  while (potencia * BigInt.from(10) < deseado) {
    potencia *= BigInt.from(10);
  }
  return [
    potencia,
    potencia * BigInt.two,
    if (potencia > BigInt.one) potencia * BigInt.from(5) ~/ BigInt.two,
    potencia * BigInt.from(5),
    potencia * BigInt.from(10),
  ].firstWhere((v) => v >= deseado);
}

class GraficoMensual extends StatefulWidget {
  const GraficoMensual({super.key, required this.periodos});
  final List<MesEstadistico> periodos;
  @override
  State<GraficoMensual> createState() => _GraficoMensualState();
}

class _GraficoMensualState extends State<GraficoMensual> {
  final _desplazamiento = ScrollController();
  late String _seleccionado;
  @override
  void initState() {
    super.initState();
    _seleccionado = widget.periodos.last.periodo;
    _mostrarRecientes();
  }

  void _mostrarRecientes() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && _desplazamiento.hasClients) {
        _desplazamiento.jumpTo(_desplazamiento.position.maxScrollExtent);
      }
    });
  }

  @override
  void didUpdateWidget(GraficoMensual oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.periodos.first.periodo != widget.periodos.first.periodo ||
        oldWidget.periodos.last.periodo != widget.periodos.last.periodo) {
      _seleccionado = widget.periodos.last.periodo;
      _mostrarRecientes();
    }
  }

  @override
  void dispose() {
    _desplazamiento.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final calculables = widget.periodos
        .where((m) => m.consumo.litros != null)
        .toList();
    final maximo = calculables.fold(BigInt.zero, (a, m) {
      final valor = BigInt.parse(m.consumo.litros!);
      return valor > a ? valor : a;
    });
    final paso = _paso(maximo);
    final techo = paso * BigInt.from(4);
    final elegido = widget.periodos.firstWhere(
      (m) => m.periodo == _seleccionado,
    );
    final estilo = Theme.of(context).textTheme.bodySmall!
        .copyWith(color: _azul, fontSize: 12);
    final escalaTexto = MediaQuery.textScalerOf(context);
    double anchoTexto(String valor) {
      final p = TextPainter(
        text: TextSpan(text: valor, style: estilo),
        textDirection: Directionality.of(context),
        textScaler: escalaTexto,
      )..layout();
      final ancho = p.width;
      p.dispose();
      return ancho;
    }

    final etiquetasEje = [
      for (var i = 0; i <= 4; i++) _m3(paso * BigInt.from(i)),
    ];
    final ejeAncho =
        etiquetasEje.map(anchoTexto).reduce((a, b) => a > b ? a : b) + 14;
    var anchoColumna = escalaTexto.scale(30) + 10;
    for (final mes in calculables) {
      final ancho =
          anchoTexto(numeroEstadistico(mes.consumo.metrosCubicos!)) + 6;
      if (ancho > anchoColumna) anchoColumna = ancho;
    }
    const altoBarras = 220.0;
    final altoValor = escalaTexto.scale(16) + 6;
    final margenArriba = altoValor + 4;
    final altoMes = escalaTexto.scale(32) + 16;
    final base = margenArriba + altoBarras;
    final alto = base + altoMes;
    return Card(
      color: Colors.white,
      clipBehavior: Clip.antiAlias,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              'Consumo por mes',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w700,
                color: _azul,
              ),
            ),
            const SizedBox(height: 8),
            const Text('Consumo (m³)', style: TextStyle(color: _azul)),
            if (calculables.isEmpty)
              const Padding(
                padding: EdgeInsets.only(top: 8),
                child: Text('Sin consumos calculables para trazar columnas.'),
              )
            else if (maximo == BigInt.zero)
              const Padding(
                padding: EdgeInsets.only(top: 8),
                child: Text('Los consumos calculables son cero.'),
              ),
            const SizedBox(height: 8),
            LayoutBuilder(
              builder: (context, restricciones) {
                final disponible = (restricciones.maxWidth - ejeAncho).clamp(
                  1.0,
                  double.infinity,
                );
                final minimo = anchoColumna * widget.periodos.length;
                final anchoGrafico = disponible > minimo ? disponible : minimo;
                final celda = anchoGrafico / widget.periodos.length;
                return Column(
                  children: [
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        SizedBox(
                          width: ejeAncho,
                          height: alto,
                          child: Stack(
                            children: [
                              for (var i = 0; i <= 4; i++)
                                Positioned(
                                  left: 0,
                                  right: 8,
                                  top:
                                      margenArriba +
                                      altoBarras * (1 - i / 4) -
                                      escalaTexto.scale(8),
                                  child: Text(
                                    etiquetasEje[i],
                                    key: ValueKey('eje-$i'),
                                    textAlign: TextAlign.right,
                                    style: estilo,
                                    maxLines: 1,
                                  ),
                                ),
                            ],
                          ),
                        ),
                        Expanded(
                          child: Scrollbar(
                            controller: _desplazamiento,
                            thumbVisibility: anchoGrafico > disponible,
                            child: SingleChildScrollView(
                              key: const ValueKey('meses-grafico'),
                              controller: _desplazamiento,
                              scrollDirection: Axis.horizontal,
                              child: SizedBox(
                                width: anchoGrafico,
                                height: alto,
                                child: Stack(
                                  children: [
                                    for (var i = 0; i <= 4; i++)
                                      Positioned(
                                        left: 0,
                                        right: 0,
                                        top: margenArriba + altoBarras * i / 4,
                                        child: Container(
                                          height: i == 4 ? 1.5 : 1,
                                          color: i == 4
                                              ? _azul
                                              : const Color(0xFFE1E9EC),
                                        ),
                                      ),
                                    Positioned(
                                      left: 0,
                                      top: margenArriba,
                                      width: 1.5,
                                      height: altoBarras,
                                      child: Container(color: _azul),
                                    ),
                                    for (
                                      var i = 0;
                                      i < widget.periodos.length;
                                      i++
                                    )
                                      Positioned(
                                        left: i * celda,
                                        top: 0,
                                        width: celda,
                                        height: alto,
                                        child: _Columna(
                                          mes: widget.periodos[i],
                                          techo: techo,
                                          altoBarras: altoBarras,
                                          margenArriba: margenArriba,
                                          altoValor: altoValor,
                                          base: base,
                                          estilo: estilo,
                                          seleccionada:
                                              widget.periodos[i].periodo ==
                                              _seleccionado,
                                          alSeleccionar: () => setState(
                                            () => _seleccionado =
                                                widget.periodos[i].periodo,
                                          ),
                                        ),
                                      ),
                                  ],
                                ),
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    const Text(
                      'Meses de consumo',
                      style: TextStyle(color: _azul),
                    ),
                    if (anchoGrafico > disponible)
                      const Padding(
                        padding: EdgeInsets.only(top: 8),
                        child: Text(
                          'Desliza el gráfico a los lados para ver todos los meses.',
                        ),
                      ),
                  ],
                );
              },
            ),
            const SizedBox(height: 12),
            const Wrap(
              spacing: 16,
              runSpacing: 8,
              children: [
                _Leyenda(color: _turquesa, texto: 'Completo'),
                _Leyenda(color: _parcial, texto: 'Parcial'),
                Text('S/D: sin datos'),
                Text('Rev.: en revisión'),
              ],
            ),
            const SizedBox(height: 12),
            const Text(
              'Toca una columna o un mes para ver su detalle.',
              style: TextStyle(height: 1.5),
            ),
            const Divider(height: 24),
            _DetalleMensual(
              key: ValueKey('detalle-${elegido.periodo}'),
              mes: elegido,
            ),
          ],
        ),
      ),
    );
  }
}

class _Columna extends StatelessWidget {
  const _Columna({
    required this.mes,
    required this.techo,
    required this.altoBarras,
    required this.margenArriba,
    required this.altoValor,
    required this.base,
    required this.estilo,
    required this.seleccionada,
    required this.alSeleccionar,
  });
  final MesEstadistico mes;
  final BigInt techo;
  final double altoBarras, margenArriba, altoValor, base;
  final TextStyle estilo;
  final bool seleccionada;
  final VoidCallback alSeleccionar;
  @override
  Widget build(BuildContext context) {
    final consumo = mes.consumo;
    final litros = consumo.litros == null
        ? null
        : BigInt.parse(consumo.litros!);
    final ratio = litros == null
        ? 0.0
        : (litros * BigInt.from(1000000) ~/ techo).toDouble() / 1000000;
    final altura = litros == null || litros == BigInt.zero
        ? 0.0
        : (ratio < 1 / altoBarras ? 1 / altoBarras : ratio);
    final color = consumo.estado == 'COMPLETO' ? _turquesa : _parcial;
    final texto = consumo.metrosCubicos == null
        ? (consumo.estado == 'INCOMPATIBLE' ? 'Rev.' : 'S/D')
        : numeroEstadistico(consumo.metrosCubicos!);
    const meses = [
      'Ene',
      'Feb',
      'Mar',
      'Abr',
      'May',
      'Jun',
      'Jul',
      'Ago',
      'Sep',
      'Oct',
      'Nov',
      'Dic',
    ];
    final nombre =
        '${meses[int.parse(mes.periodo.substring(5)) - 1]}\n${mes.periodo.substring(2, 4)}';
    return Semantics(
      button: true,
      onTap: alSeleccionar,
      selected: seleccionada,
      label:
          '${nombreMes(mes.periodo)}. ${_etiqueta(mes)}. ${litros == null ? 'Consumo no disponible' : '${numeroEstadistico(consumo.metrosCubicos!)} metros cúbicos, ${consumo.litros} litros'}.',
      child: ExcludeSemantics(
        child: InkWell(
          key: ValueKey('columna-${mes.periodo}'),
          onTap: alSeleccionar,
          child: Stack(
            children: [
              if (seleccionada)
                Positioned.fill(
                  child: ColoredBox(color: _azul.withValues(alpha: .04)),
                ),
              Positioned(
                left: 3,
                right: 3,
                top: margenArriba,
                height: altoBarras,
                child: Align(
                  alignment: Alignment.bottomCenter,
                  child: litros == null
                      ? const SizedBox.shrink()
                      : litros == BigInt.zero
                      ? Container(
                          key: ValueKey('cero-${mes.periodo}'),
                          width: 24,
                          height: 3,
                          color: color,
                        )
                      : FractionallySizedBox(
                          key: ValueKey('barra-${mes.periodo}'),
                          heightFactor: altura,
                          child: Container(
                            width: 28,
                            decoration: BoxDecoration(
                              color: color,
                              borderRadius: const BorderRadius.vertical(
                                top: Radius.circular(3),
                              ),
                            ),
                          ),
                        ),
                ),
              ),
              Positioned(
                left: 0,
                right: 0,
                top: base - altoBarras * altura - altoValor,
                child: Text(
                  texto,
                  textAlign: TextAlign.center,
                  style: estilo,
                  maxLines: 1,
                ),
              ),
              Positioned(
                left: 0,
                right: 0,
                top: base + 8,
                child: Text(
                  nombre,
                  textAlign: TextAlign.center,
                  style: estilo.copyWith(
                    fontWeight: seleccionada
                        ? FontWeight.w700
                        : FontWeight.w400,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Leyenda extends StatelessWidget {
  const _Leyenda({required this.color, required this.texto});
  final Color color;
  final String texto;
  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      Container(width: 12, height: 12, color: color),
      const SizedBox(width: 6),
      Text(texto),
    ],
  );
}

class _DetalleMensual extends StatelessWidget {
  const _DetalleMensual({super.key, required this.mes});
  final MesEstadistico mes;
  @override
  Widget build(BuildContext context) {
    final c = mes.consumo;
    return ExpansionTile(
      title: Text(
        nombreMes(mes.periodo),
        style: const TextStyle(fontWeight: FontWeight.w700, color: _azul),
      ),
      subtitle: Text(
        '${_etiqueta(mes)}\n${c.litros == null ? 'Consumo no disponible' : '${numeroEstadistico(c.metrosCubicos!, minimo: 3)} m³ · ${c.litros} litros'}',
        style: const TextStyle(height: 1.5),
      ),
      expandedCrossAxisAlignment: CrossAxisAlignment.stretch,
      childrenPadding: const EdgeInsets.only(top: 12, bottom: 12),
      children: [
        Text(
          'Periodo de análisis: desde ${fechaCalendario(mes.inicio.subtract(const Duration(hours: 5)))} '
          'hasta antes del ${fechaCalendario(mes.fin.subtract(const Duration(hours: 5)))}.',
          style: const TextStyle(height: 1.5),
        ),
        if (c.desde != null) ...[
          const SizedBox(height: 12),
          Text(
            'Tramo medido: ${fechaLima(c.desde!)} a ${fechaLima(c.hasta!)}.',
            style: const TextStyle(height: 1.5),
          ),
        ],
        if (c.estado != 'COMPLETO') ...[
          const SizedBox(height: 12),
          Text(switch (c.estado) {
            'PARCIAL' => 'El volumen corresponde a las lecturas disponibles. Falta cobertura de parte del periodo.',
            'INCOMPATIBLE' => 'Las lecturas requieren revisión y no permiten calcular un volumen confiable.',
            _ => 'Se necesitan lecturas suficientes para calcular el consumo de este mes.',
          }, style: const TextStyle(height: 1.5)),
        ],
        if (c.motivo == 'HUECOS_ENTRE_INTERVALOS') ...[
          const SizedBox(height: 8),
          const Text(
            'Hay huecos entre intervalos de medición; el tramo no representa cobertura continua.',
            style: TextStyle(height: 1.5),
          ),
        ],
      ],
    );
  }
}
