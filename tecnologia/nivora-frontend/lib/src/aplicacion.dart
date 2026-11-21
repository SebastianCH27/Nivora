import 'package:material_ui/material_ui.dart';

import 'api_nivora.dart';
import 'controlador_consulta.dart';
import 'formatos_consulta.dart';
import 'pantalla_costos.dart';

const _azul = Color(0xFF123D59);
const _turquesa = Color(0xFF087F8C);
const _fondo = Color(0xFFF3F7F8);

class NivoraApp extends StatefulWidget {
  const NivoraApp({super.key, required this.api});
  final ServicioNivora api;

  @override
  State<NivoraApp> createState() => _NivoraAppState();
}

class _NivoraAppState extends State<NivoraApp> with WidgetsBindingObserver {
  late final ControladorConsulta controlador;

  @override
  void initState() {
    super.initState();
    controlador = ControladorConsulta(widget.api);
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) controlador.verificarVencimiento();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    controlador.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'Nivora',
    debugShowCheckedModeBanner: false,
    theme: ThemeData(
      fontFamily: 'Roboto',
      colorScheme: ColorScheme.fromSeed(seedColor: _turquesa),
      scaffoldBackgroundColor: _fondo,
      inputDecorationTheme: const InputDecorationTheme(
        border: OutlineInputBorder(
          borderRadius: BorderRadius.all(Radius.circular(14)),
        ),
        filled: true,
        fillColor: Colors.white,
        contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 18),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: _turquesa,
          foregroundColor: Colors.white,
          minimumSize: const Size(0, 52),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
        ),
      ),
    ),
    home: ListenableBuilder(
      listenable: controlador,
      builder: (context, _) => controlador.sesion == null
          ? Acceso(controlador: controlador)
          : ResumenConsumo(controlador: controlador),
    ),
  );
}

class Marca extends StatelessWidget {
  const Marca({super.key, this.clara = false});
  final bool clara;
  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      Container(
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: clara ? Colors.white.withValues(alpha: .14) : _turquesa,
          borderRadius: BorderRadius.circular(14),
        ),
        child: const Icon(
          Icons.water_drop_outlined,
          color: Colors.white,
          size: 28,
        ),
      ),
      const SizedBox(width: 12),
      Text(
        'Nivora',
        style: TextStyle(
          fontSize: 28,
          fontWeight: FontWeight.w700,
          letterSpacing: -.8,
          color: clara ? Colors.white : _azul,
        ),
      ),
    ],
  );
}

class Acceso extends StatefulWidget {
  const Acceso({super.key, required this.controlador});
  final ControladorConsulta controlador;
  @override
  State<Acceso> createState() => _AccesoState();
}

class _AccesoState extends State<Acceso> {
  final formulario = GlobalKey<FormState>();
  final suministro = TextEditingController();
  final dni = TextEditingController();
  bool ocultarDni = true;

  @override
  void dispose() {
    suministro.dispose();
    dni.clear();
    dni.dispose();
    super.dispose();
  }

  void ingresar() {
    if (widget.controlador.ingresando ||
        widget.controlador.segundosEspera > 0) {
      return;
    }
    if (formulario.currentState!.validate()) {
      FocusScope.of(context).unfocus();
      widget.controlador.ingresar(suministro.text.trim(), dni.text.trim());
    }
  }

  @override
  Widget build(BuildContext context) {
    final control = widget.controlador;
    final ancho = MediaQuery.sizeOf(context).width >= 900;
    final bloqueado = control.ingresando || control.segundosEspera > 0;
    final campos = Padding(
      padding: EdgeInsets.all(ancho ? 44 : 24),
      child: Form(
        key: formulario,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            if (!ancho) ...[const Marca(), const SizedBox(height: 36)],
            Text(
              'Consulta tu suministro',
              style: Theme.of(context).textTheme.headlineMedium
                  ?.copyWith(color: _azul, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 12),
            const Text(
              'Ingresa tus datos para conocer tu consumo de agua.',
              style: TextStyle(
                fontSize: 16,
                height: 1.5,
                color: Color(0xFF536976),
              ),
            ),
            const SizedBox(height: 30),
            TextFormField(
              controller: suministro,
              enabled: !control.ingresando,
              keyboardType: TextInputType.number,
              textInputAction: TextInputAction.next,
              autocorrect: false,
              enableSuggestions: false,
              decoration: const InputDecoration(
                labelText: 'Código de suministro',
                helperText: 'Lo encuentras en tu recibo de agua.',
                prefixIcon: Icon(Icons.water_outlined),
              ),
              validator: (valor) =>
                  RegExp(r'^[0-9]{1,20}$').hasMatch((valor ?? '').trim())
                  ? null
                  : 'Ingresa entre 1 y 20 dígitos.',
            ),
            const SizedBox(height: 22),
            TextFormField(
              controller: dni,
              enabled: !control.ingresando,
              keyboardType: TextInputType.number,
              textInputAction: TextInputAction.done,
              autocorrect: false,
              enableSuggestions: false,
              obscureText: ocultarDni,
              onFieldSubmitted: (_) => ingresar(),
              decoration: InputDecoration(
                labelText: 'DNI de quien consulta',
                helperText: 'Debe contener 8 dígitos.',
                prefixIcon: const Icon(Icons.badge_outlined),
                suffixIcon: IconButton(
                  tooltip: ocultarDni ? 'Mostrar DNI' : 'Ocultar DNI',
                  onPressed: () => setState(() => ocultarDni = !ocultarDni),
                  icon: Icon(
                    ocultarDni
                        ? Icons.visibility_outlined
                        : Icons.visibility_off_outlined,
                  ),
                ),
              ),
              validator: (valor) =>
                  RegExp(r'^[0-9]{8}$').hasMatch((valor ?? '').trim())
                  ? null
                  : 'Ingresa un DNI de exactamente 8 dígitos.',
            ),
            const SizedBox(height: 24),
            if (control.aviso != null) ...[
              Aviso(texto: control.aviso!),
              const SizedBox(height: 20),
            ],
            FilledButton(
              onPressed: bloqueado ? null : ingresar,
              child: control.ingresando
                  ? const Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        ),
                        SizedBox(width: 12),
                        Text('Validando datos…'),
                      ],
                    )
                  : Text(
                      control.segundosEspera > 0
                          ? 'Intenta en ${control.segundosEspera} s'
                          : 'Ingresar',
                    ),
            ),
            const SizedBox(height: 22),
            const Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(Icons.lock_outline, size: 17, color: _turquesa),
                SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Usamos tu DNI para validar la consulta. '
                    'No necesitas crear una cuenta.',
                    style: TextStyle(
                      fontSize: 12,
                      height: 1.5,
                      color: Color(0xFF536976),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: EdgeInsets.all(ancho ? 40 : 16),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 1080),
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(28),
                  boxShadow: const [
                    BoxShadow(
                      color: Color(0x0C123D59),
                      blurRadius: 40,
                      offset: Offset(0, 12),
                    ),
                  ],
                ),
                child: ancho
                    ? IntrinsicHeight(
                        child: Row(
                          children: [
                            Expanded(
                              child: Container(
                                padding: const EdgeInsets.all(44),
                                decoration: const BoxDecoration(
                                  color: _azul,
                                  borderRadius: BorderRadius.horizontal(
                                    left: Radius.circular(28),
                                  ),
                                ),
                                child: const Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Marca(clara: true),
                                    SizedBox(height: 64),
                                    Icon(
                                      Icons.waves_rounded,
                                      size: 72,
                                      color: Color(0xFF72D7CF),
                                    ),
                                    SizedBox(height: 24),
                                    Text(
                                      'Conoce el agua\nque consumes.',
                                      style: TextStyle(
                                        fontSize: 38,
                                        height: 1.15,
                                        fontWeight: FontWeight.w700,
                                        color: Colors.white,
                                      ),
                                    ),
                                    SizedBox(height: 20),
                                    Text(
                                      'Información clara para cuidar tu consumo, día a día.',
                                      style: TextStyle(
                                        fontSize: 17,
                                        height: 1.6,
                                        color: Color(0xFFCBDFE8),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                            Expanded(child: campos),
                          ],
                        ),
                      )
                    : campos,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class Aviso extends StatelessWidget {
  const Aviso({super.key, required this.texto});
  final String texto;
  @override
  Widget build(BuildContext context) => Semantics(
    liveRegion: true,
    child: Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFFF0F6F8),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(texto, style: const TextStyle(color: _azul, height: 1.5)),
    ),
  );
}

enum _VistaConsulta { actual, historial, costos }

class ResumenConsumo extends StatefulWidget {
  const ResumenConsumo({super.key, required this.controlador});
  final ControladorConsulta controlador;

  @override
  State<ResumenConsumo> createState() => _ResumenConsumoState();
}

class _ResumenConsumoState extends State<ResumenConsumo> {
  _VistaConsulta vista = _VistaConsulta.actual;
  ControladorConsulta get controlador => widget.controlador;

  @override
  Widget build(BuildContext context) {
    final sesion = controlador.sesion!;
    final consumo = controlador.consumo;
    return Scaffold(
      appBar: AppBar(
        title: const Marca(),
        backgroundColor: _fondo,
        toolbarHeight: 80,
        actions: [
          IconButton(
            tooltip: 'Cerrar sesión',
            onPressed: controlador.saliendo ? null : controlador.salir,
            icon: const Icon(Icons.logout),
          ),
          const SizedBox(width: 16),
        ],
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 1000),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const SizedBox(height: 12),
                  Text(
                    'Hola, ${sesion.nombre}',
                    style: Theme.of(context).textTheme.headlineSmall
                        ?.copyWith(color: _azul, fontWeight: FontWeight.w700),
                  ),
                  const SizedBox(height: 10),
                  Text(
                    'Suministro ${sesion.suministro}',
                    style: const TextStyle(color: _turquesa, fontSize: 16),
                  ),
                  const SizedBox(height: 28),
                  Wrap(
                    spacing: 12,
                    runSpacing: 12,
                    children: [
                      ChoiceChip(
                        label: const Text('Consumo actual'),
                        selected: vista == _VistaConsulta.actual,
                        onSelected: controlador.saliendo
                            ? null
                            : (_) {
                                setState(() => vista = _VistaConsulta.actual);
                              },
                      ),
                      ChoiceChip(
                        label: const Text('Historial mensual'),
                        selected: vista == _VistaConsulta.historial,
                        onSelected: controlador.saliendo
                            ? null
                            : (_) {
                                setState(
                                  () => vista = _VistaConsulta.historial,
                                );
                                if (controlador.historial == null &&
                                    controlador.errorHistorial == null) {
                                  controlador.cargarHistorial();
                                }
                              },
                      ),
                      ChoiceChip(
                        label: const Text('Costos estimados'),
                        selected: vista == _VistaConsulta.costos,
                        onSelected: controlador.saliendo
                            ? null
                            : (_) {
                                setState(() => vista = _VistaConsulta.costos);
                                if (controlador.costos == null &&
                                    controlador.errorCostos == null) {
                                  controlador.cargarCostos();
                                }
                              },
                      ),
                    ],
                  ),
                  const SizedBox(height: 24),
                  if (vista == _VistaConsulta.costos)
                    PantallaCostos(controlador: controlador)
                  else if (vista == _VistaConsulta.historial)
                    _HistorialMensual(controlador: controlador)
                  else ...[
                    Wrap(
                      alignment: WrapAlignment.spaceBetween,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      spacing: 16,
                      runSpacing: 12,
                      children: [
                        const Text(
                          'Tu consumo actual',
                          style: TextStyle(
                            fontSize: 22,
                            fontWeight: FontWeight.w700,
                            color: _azul,
                          ),
                        ),
                        OutlinedButton.icon(
                          onPressed:
                              controlador.consultando || controlador.saliendo
                              ? null
                              : controlador.actualizar,
                          icon: const Icon(Icons.refresh),
                          label: const Text('Actualizar'),
                        ),
                      ],
                    ),
                    const SizedBox(height: 20),
                    if (controlador.saliendo) const LinearProgressIndicator(),
                    if (controlador.consultando) ...[
                      const LinearProgressIndicator(),
                      const SizedBox(height: 16),
                      const Text('Consultando tus lecturas…'),
                      const SizedBox(height: 16),
                    ],
                    if (controlador.errorConsumo != null) ...[
                      Aviso(texto: controlador.errorConsumo!),
                      const SizedBox(height: 16),
                      if (consumo != null)
                        const Padding(
                          padding: EdgeInsets.only(bottom: 16),
                          child: Text(
                            'Se muestran los últimos datos consultados.',
                          ),
                        ),
                    ],
                    if (consumo != null) _TarjetaConsumo(consumo: consumo),
                    const SizedBox(height: 24),
                    const Text(
                      'El consumo se calcula con las lecturas disponibles del suministro. '
                      'Puede actualizarse cuando se incorporen nuevas mediciones.',
                      style: TextStyle(color: Color(0xFF536976), height: 1.6),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _HistorialMensual extends StatelessWidget {
  const _HistorialMensual({required this.controlador});
  final ControladorConsulta controlador;

  @override
  Widget build(BuildContext context) {
    final datos = controlador.historial;
    final ocupado = controlador.consultandoHistorial || controlador.saliendo;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text(
          'Tus meses anteriores',
          style: TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.w700,
            color: _azul,
          ),
        ),
        const SizedBox(height: 10),
        const Text(
          'Consulta meses ya terminados. Cada mes indica si sus lecturas '
          'cubren todo el periodo o solo una parte.',
          style: TextStyle(height: 1.5, color: Color(0xFF536976)),
        ),
        const SizedBox(height: 16),
        Wrap(
          spacing: 16,
          runSpacing: 12,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            DropdownButton<int>(
              value: controlador.mesesHistorial,
              items: [
                for (final meses in [3, 6, 12])
                  DropdownMenuItem(value: meses, child: Text('$meses meses')),
              ],
              onChanged: ocupado
                  ? null
                  : (meses) {
                      if (meses != null) {
                        controlador.cargarHistorial(meses: meses);
                      }
                    },
            ),
            OutlinedButton.icon(
              onPressed: ocupado ? null : () => controlador.cargarHistorial(),
              icon: const Icon(Icons.refresh),
              label: const Text('Actualizar historial'),
            ),
            if (controlador.hastaHistorial != null)
              TextButton(
                onPressed: ocupado
                    ? null
                    : () => controlador.cargarHistorial(ultimos: true),
                child: const Text('Últimos meses'),
              ),
          ],
        ),
        const SizedBox(height: 12),
        Text(
          controlador.hastaHistorial == null
              ? 'Últimos ${controlador.mesesHistorial} meses terminados'
              : 'Hasta ${nombreMes(controlador.hastaHistorial!)}',
        ),
        if (ocupado) ...[
          const SizedBox(height: 16),
          const LinearProgressIndicator(),
          const SizedBox(height: 12),
          Text(
            controlador.saliendo
                ? 'Cerrando sesión…'
                : 'Consultando tu historial…',
          ),
        ],
        if (controlador.errorHistorial != null) ...[
          const SizedBox(height: 16),
          Aviso(texto: controlador.errorHistorial!),
          if (datos != null)
            const Padding(
              padding: EdgeInsets.only(top: 12),
              child: Text(
                'Se muestran los últimos datos consultados para este rango.',
              ),
            ),
          TextButton.icon(
            onPressed: ocupado ? null : () => controlador.cargarHistorial(),
            icon: const Icon(Icons.refresh),
            label: const Text('Reintentar historial'),
          ),
        ],
        if (datos != null) ...[
          const SizedBox(height: 16),
          Text(
            '${nombreMes(datos.periodos.last.periodo)} — ${nombreMes(datos.periodos.first.periodo)}',
            style: const TextStyle(fontWeight: FontWeight.w700, color: _azul),
          ),
          const SizedBox(height: 8),
          Text(
            'Consultado: ${fechaLima(datos.consultadoEn)}',
            style: const TextStyle(color: Color(0xFF536976)),
          ),
          const SizedBox(height: 16),
          for (final mes in datos.periodos) ...[
            _TarjetaMes(key: ValueKey(mes.periodo), mes: mes),
            const SizedBox(height: 12),
          ],
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: [
              OutlinedButton.icon(
                onPressed:
                    ocupado ||
                        moverMes(
                              datos.periodos.last.periodo,
                              -controlador.mesesHistorial,
                            ).compareTo('1000-01') <
                            0
                    ? null
                    : () {
                        controlador.cargarHistorial(
                          hasta: moverMes(datos.periodos.last.periodo, -1),
                        );
                      },
                icon: const Icon(Icons.chevron_left),
                label: const Text('Meses anteriores'),
              ),
              if (controlador.hastaHistorial != null)
                OutlinedButton.icon(
                  onPressed: ocupado
                      ? null
                      : () {
                          final siguiente = moverMes(
                            datos.periodos.first.periodo,
                            controlador.mesesHistorial,
                          );
                          final lima = datos.consultadoEn.subtract(
                            const Duration(hours: 5),
                          );
                          final actual =
                              '${lima.year.toString().padLeft(4, '0')}-'
                              '${lima.month.toString().padLeft(2, '0')}';
                          if (siguiente.compareTo(moverMes(actual, -1)) >= 0) {
                            controlador.cargarHistorial(ultimos: true);
                          } else {
                            controlador.cargarHistorial(hasta: siguiente);
                          }
                        },
                  icon: const Icon(Icons.chevron_right),
                  label: const Text('Más recientes'),
                ),
            ],
          ),
        ],
      ],
    );
  }
}

class _TarjetaMes extends StatelessWidget {
  const _TarjetaMes({super.key, required this.mes});
  final ConsumoMensual mes;

  @override
  Widget build(BuildContext context) {
    final etiqueta = switch (mes.estado) {
      'COMPLETO' => 'Mes completo',
      'PARCIAL' => 'Consumo parcial',
      'INCOMPATIBLE' => 'Lecturas en revisión',
      _ => 'Sin datos suficientes',
    };
    final explicacion = switch (mes.estado) {
      'COMPLETO' => 'Las lecturas cubren todo el mes.',
      'PARCIAL' =>
        'El valor corresponde al tramo con lecturas disponibles. '
            'Puede faltar parte del consumo del mes.',
      'INCOMPATIBLE' =>
        'Las lecturas presentan incompatibilidades. '
            'No se puede calcular un consumo confiable para este mes.',
      _ =>
        'No hay lecturas suficientes para calcular este mes. '
            'Esto no significa que el consumo haya sido cero.',
    };
    return Card(
      color: Colors.white,
      clipBehavior: Clip.antiAlias,
      child: ExpansionTile(
        title: Text(
          nombreMes(mes.periodo),
          style: const TextStyle(fontWeight: FontWeight.w700, color: _azul),
        ),
        subtitle: Padding(
          padding: const EdgeInsets.only(top: 8, bottom: 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                mes.metrosCubicos == null
                    ? 'Sin valor disponible'
                    : '${mes.metrosCubicos!.replaceAll('.', ',')} m³',
                style: const TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.w700,
                  color: _azul,
                ),
              ),
              if (mes.litros != null) Text('${mes.litros} litros'),
              const SizedBox(height: 8),
              Text(etiqueta, style: const TextStyle(color: _turquesa)),
            ],
          ),
        ),
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 20),
        expandedCrossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(explicacion, style: const TextStyle(height: 1.5)),
          if (mes.desde != null && mes.hasta != null) ...[
            const SizedBox(height: 10),
            Text(
              'Tramo medido: ${fechaLima(mes.desde!)} a ${fechaLima(mes.hasta!)}',
              style: const TextStyle(height: 1.5),
            ),
          ],
        ],
      ),
    );
  }
}

class _TarjetaConsumo extends StatelessWidget {
  const _TarjetaConsumo({required this.consumo});
  final ConsumoActual consumo;

  @override
  Widget build(BuildContext context) {
    final etiqueta = switch (consumo.estado) {
      'COMPLETO' => 'Cobertura completa hasta la última lectura',
      'PARCIAL' => 'Lecturas parciales',
      'INCOMPATIBLE' => 'Lecturas pendientes de revisión',
      _ => 'Aún no hay suficientes lecturas',
    };
    final explicacion = switch (consumo.estado) {
      'PARCIAL' =>
        'Este valor corresponde únicamente al tramo con lecturas disponibles.',
      'INCOMPATIBLE' =>
        'No podemos calcular un consumo confiable con las lecturas actuales.',
      'SIN_DATOS' =>
        'El consumo aparecerá cuando existan lecturas suficientes.',
      _ => 'Consumo registrado en el mes hasta la última medición disponible.',
    };
    return Container(
      padding: const EdgeInsets.all(28),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(24),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            nombreMes(consumo.periodo),
            style: const TextStyle(
              color: _turquesa,
              fontSize: 16,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 22),
          const Icon(Icons.water_drop_outlined, color: _turquesa, size: 38),
          const SizedBox(height: 14),
          Text(
            consumo.metrosCubicos == null
                ? 'Sin valor disponible'
                : '${consumo.metrosCubicos!.replaceAll('.', ',')} m³',
            style: const TextStyle(
              fontSize: 34,
              color: _azul,
              fontWeight: FontWeight.w700,
            ),
          ),
          if (consumo.litros != null)
            Text(
              '${consumo.litros} litros',
              style: const TextStyle(fontSize: 16, color: Color(0xFF536976)),
            ),
          const SizedBox(height: 22),
          Text(
            etiqueta,
            style: const TextStyle(fontWeight: FontWeight.w700, color: _azul),
          ),
          const SizedBox(height: 8),
          Text(explicacion, style: const TextStyle(height: 1.5)),
          if (consumo.desde != null && consumo.hasta != null) ...[
            const SizedBox(height: 16),
            Text(
              'Tramo medido: ${fechaLima(consumo.desde!)} a ${fechaLima(consumo.hasta!)}',
            ),
          ],
          const Divider(height: 40),
          Text(
            consumo.ultimaLectura == null
                ? 'Última lectura: no disponible'
                : 'Última lectura: ${fechaLima(consumo.ultimaLectura!)}',
          ),
          const SizedBox(height: 10),
          Text(switch (consumo.actualizacion) {
            'CORTE_CONFIRMADO' =>
              'La fuente confirmó la actualización de las lecturas.',
            'CON_INCIDENCIAS' => 'La actualización presenta incidencias. Puedes volver a consultar más tarde.',
            'SIN_LECTURAS' =>
              'Todavía no se recibieron lecturas del suministro.',
            _ => 'Hay lecturas pendientes de confirmación por la fuente.',
          }, style: const TextStyle(color: Color(0xFF536976), height: 1.5)),
        ],
      ),
    );
  }
}
