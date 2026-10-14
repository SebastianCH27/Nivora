import { ErrorNivora } from './errores.js';

// Importes en céntimos; los números de la tarifa de demostración son ficticios.
export function calcularCosto(litros, tarifa) {
  if (!Number.isSafeInteger(litros) || litros < 0) throw new ErrorNivora('DATOS_INVALIDOS', 'Consumo inválido para calcular el costo.');
  if (!tarifa || !Number.isSafeInteger(tarifa.fijoCentimos) || tarifa.fijoCentimos < 0 || !Number.isSafeInteger(tarifa.saneamientoPorcentaje) || tarifa.saneamientoPorcentaje < 0 || !Number.isSafeInteger(tarifa.impuestoPorcentaje) || tarifa.impuestoPorcentaje < 0 || !Array.isArray(tarifa.tramos) || !tarifa.tramos.length) throw new ErrorNivora('DATOS_INVALIDOS', 'La configuración tarifaria es inválida.');
  let anterior = 0;
  let aguaMilicentimos = 0;
  for (const [indice, tramo] of tarifa.tramos.entries()) {
    if (!Number.isSafeInteger(tramo.centimosPorM3) || tramo.centimosPorM3 < 0 || (tramo.hastaLitros === null ? indice !== tarifa.tramos.length - 1 : !Number.isSafeInteger(tramo.hastaLitros) || tramo.hastaLitros <= anterior)) throw new ErrorNivora('DATOS_INVALIDOS', 'Los tramos tarifarios son inválidos.');
    const limite = tramo.hastaLitros ?? Infinity;
    const usados = Math.max(0, Math.min(litros, limite) - anterior);
    aguaMilicentimos += usados * tramo.centimosPorM3;
    anterior = limite;
  }
  if (tarifa.tramos.at(-1).hastaLitros !== null || !Number.isSafeInteger(aguaMilicentimos)) throw new ErrorNivora('DATOS_INVALIDOS', 'La tarifa no cubre el consumo o el cálculo excede la precisión admitida.');
  const agua = Math.round(aguaMilicentimos / 1000);
  const saneamiento = Math.round(agua * tarifa.saneamientoPorcentaje / 100);
  const subtotal = agua + saneamiento + tarifa.fijoCentimos;
  const impuesto = Math.round(subtotal * tarifa.impuestoPorcentaje / 100);
  return { totalCentimos: subtotal + impuesto, conceptos: { agua, saneamiento, cargoFijo: tarifa.fijoCentimos, impuesto }, tarifaId: tarifa.id, moneda: 'PEN', nota: 'Estimación con tarifa ficticia; incluye el cargo fijo mensual completo.' };
}

export function proyectarConsumo(consumo, inicio, fin) {
  const transcurrido = Date.parse(consumo.hasta) - inicio;
  if (consumo.litros === null || consumo.estado !== 'completo' || Date.parse(consumo.desde) !== inicio || transcurrido < 86400000) return null;
  return { litros: Math.round(consumo.litros * (fin - inicio) / transcurrido), promedioLitrosDia: consumo.litros * 86400000 / transcurrido, hastaLectura: consumo.hasta, supuesto: 'Se mantiene el ritmo promedio observado desde el inicio del mes hasta la última lectura disponible.' };
}

export function superaMesAnterior(actual, anterior) {
  return actual.estado === 'completo' && anterior.estado === 'completo' && actual.litros !== null && anterior.litros !== null && actual.litros > anterior.litros;
}
