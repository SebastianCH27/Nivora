import 'package:flutter_test/flutter_test.dart';
import 'package:nivora_frontend/src/api_nivora.dart';

import 'datos_proyeccion_prueba.dart';

void main() {
  ProyeccionConsumo leer(Map<String, dynamic> json) =>
      ProyeccionConsumo.desdeJson(json, '0013100390');

  for (final estado in [
    'PROYECTADO',
    'SIN_CONSUMO_CALCULABLE',
    'COBERTURA_INCOMPLETA',
    'TIEMPO_INSUFICIENTE',
    'PERIODO_CERRADO',
  ]) {
    test('interpreta el estado de proyección $estado', () {
      final datos = leer(respuestaProyeccion(estado: estado));
      expect(datos.estado, estado);
      expect(datos.volumen != null, estado == 'PROYECTADO');
      expect(datos.costo != null, estado == 'PROYECTADO');
    });
  }

  for (final estado in [
    'ESTIMADO',
    'CONCEPTOS_INCOMPLETOS',
    'SIN_TARIFA_COMPATIBLE',
  ]) {
    test('conserva la proyección de volumen con costo $estado', () {
      final datos = leer(respuestaProyeccion(estadoCosto: estado));
      expect(datos.volumen!.litros, '12000');
      expect(datos.observado.litros, '4000');
      expect(datos.costo!.estado, estado);
      if (estado == 'ESTIMADO') {
        expect(datos.costo!.desglose!.totalEstimado, '27.14');
      }
      if (estado == 'CONCEPTOS_INCOMPLETOS') {
        expect(datos.costo!.desglose!.subtotalConocido, '23.00');
        expect(datos.costo!.desglose!.totalEstimado, isNull);
      }
      if (estado == 'SIN_TARIFA_COMPATIBLE') {
        expect(datos.costo!.desglose, isNull);
      }
    });
  }

  test('cero proyectado y observado se conservan como mediciones válidas', () {
    final json = respuestaProyeccion(estadoCosto: 'SIN_TARIFA_COMPATIBLE');
    json['observado']['litros'] = '0';
    json['observado']['metrosCubicos'] = '0.000';
    json['proyeccion']['litros'] = '0';
    json['proyeccion']['metrosCubicos'] = '0.000';
    json['proyeccion']['promedioLitrosPorDia'] = '0.000';
    expect(leer(json).volumen!.litros, '0');
  });

  test('duraciones con microsegundos no pierden precisión al validar', () {
    final json = respuestaProyeccion();
    json['observado']['hasta'] = '2026-11-11T05:00:00.123456Z';
    json['proyeccion']['segundosObservados'] = '864000.123456';
    expect(leer(json).volumen!.metrosCubicos, '12.000');
  });

  test(
    'volumen proyectado admite enteros grandes sin convertirlos a double',
    () {
      final json = respuestaProyeccion(estadoCosto: 'SIN_TARIFA_COMPATIBLE');
      json['proyeccion']['litros'] = '27670116110564327421';
      json['proyeccion']['metrosCubicos'] = '27670116110564327.421';
      json['observado']['litros'] = '9223372036854775807';
      json['observado']['metrosCubicos'] = '9223372036854775.807';
      json['proyeccion']['promedioLitrosPorDia'] = '922337203685477580.700';
      expect(leer(json).volumen!.litros, '27670116110564327421');
    },
  );

  test('un estado sin cálculo no acepta importes ni volúmenes arrastrados', () {
    final json = respuestaProyeccion(estado: 'COBERTURA_INCOMPLETA');
    json['proyeccion'] = respuestaProyeccion()['proyeccion'];
    expect(() => leer(json), throwsFormatException);
  });

  test('la hora UTC antes de las 05:00 conserva el mes de Lima', () {
    final json = respuestaProyeccion();
    json['calculadoEn'] = '2026-12-01T04:59:00Z';
    expect(leer(json).periodo, '2026-11');
  });

  final invalidos = <String, void Function(Map<String, dynamic>)>{
    'otro suministro': (j) => j['codigoSuministro'] = '9999',
    'otra moneda': (j) => j['moneda'] = 'USD',
    'otra zona': (j) => j['zonaHoraria'] = 'UTC',
    'otro método': (j) => j['supuestos']['metodo'] = 'OTRO',
    'mínimo distinto': (j) => j['supuestos']['minimoObservadoSegundos'] = 3600,
    'periodo inválido': (j) => j['periodo'] = '2026-13',
    'límite fuera de Lima': (j) => j['inicioMes'] = '2026-11-01T00:00:00Z',
    'estado desconocido': (j) => j['estado'] = 'OTRO',
    'litros numéricos': (j) => j['proyeccion']['litros'] = 12000,
    'litros negativos': (j) => j['proyeccion']['litros'] = '-12000',
    'unidades proyectadas inconsistentes': (j) =>
        j['proyeccion']['metrosCubicos'] = '13.000',
    'unidades observadas inconsistentes': (j) =>
        j['observado']['metrosCubicos'] = '4.001',
    'duración inconsistente': (j) =>
        j['proyeccion']['segundosObservados'] = '864001',
    'duración mensual inconsistente': (j) =>
        j['proyeccion']['segundosDelMes'] = '2678400',
    'proyección con cobertura parcial': (j) =>
        j['observado']['estado'] = 'PARCIAL',
    'lectura futura': (j) => j['observado']['hasta'] = '2026-11-21T05:00:00Z',
    'costo sin volumen': (j) => j['proyeccion'] = null,
    'sin tarifa con desglose': (j) =>
        j['costoProyectado']['estado'] = 'SIN_TARIFA_COMPATIBLE',
    'revisión incompatible': (j) =>
        j['costoProyectado']['revisionTarifaria'] = '2',
    'subtotal presentado como total': (j) =>
        j['costoProyectado']['desglose']['totalEstimado'] = '23.00',
    'suma monetaria inconsistente': (j) =>
        j['costoProyectado']['desglose']['subtotalConocido'] = '24.00',
    'actualización desconocida': (j) => j['actualizacion']['estado'] = 'OTRO',
  };
  for (final caso in invalidos.entries) {
    test('rechaza ${caso.key}', () {
      final json = respuestaProyeccion();
      caso.value(json);
      expect(() => leer(json), throwsFormatException);
    });
  }
}
