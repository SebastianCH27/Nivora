import { ErrorNivora } from './errores.js';

export function validarLectura(dato, suministro) {
  const falla = mensaje => { throw new ErrorNivora('LECTURA_INVALIDA', mensaje); };
  if (!dato || typeof dato.id !== 'string' || !dato.id || !dato.fuente || !dato.medidor) falla('Falta identificar la lectura, su fuente o su medidor.');
  if (!suministro || dato.suministro !== suministro.codigo || dato.medidor !== suministro.medidor) falla('La lectura no corresponde al suministro o medidor.');
  if (!['L', 'm3'].includes(dato.unidad)) falla('Unidad de lectura no admitida.');
  if (typeof dato.valor !== 'number' || !Number.isFinite(dato.valor)) falla('El valor de lectura debe ser numérico.');
  const valorLitros = dato.valor * (dato.unidad === 'm3' ? 1000 : 1);
  if (!Number.isSafeInteger(Math.round(valorLitros)) || valorLitros < 0 || Math.abs(valorLitros - Math.round(valorLitros)) > 0.00001) falla('El valor debe representar una cantidad no negativa de litros enteros.');
  const fecha = Date.parse(dato.fecha);
  if (!Number.isFinite(fecha) || !/(Z|[+-]\d{2}:\d{2})$/.test(dato.fecha)) falla('Fecha de lectura inválida o sin zona horaria.');
  if (!['acumulada', 'intervalo'].includes(dato.tipo)) falla('Tipo de lectura no admitido.');
  if (dato.tipo === 'intervalo' && (!Number.isFinite(Date.parse(dato.inicio)) || Date.parse(dato.inicio) >= fecha || !/(Z|[+-]\d{2}:\d{2})$/.test(dato.inicio))) falla('El intervalo de la lectura es inválido.');
  return { ...dato, valorLitros: Math.round(valorLitros) };
}

export function claveLectura(lectura) {
  return `${lectura.fuente}:${lectura.suministro}:${lectura.id}`;
}

export function calcularConsumo(lecturas, inicio, fin) {
  const ordenadas = [...lecturas].sort((a, b) => Date.parse(a.fecha) - Date.parse(b.fecha));
  const acumuladas = ordenadas.filter(l => l.tipo === 'acumulada' && Date.parse(l.fecha) >= inicio && Date.parse(l.fecha) <= fin);
  const intervalos = ordenadas.filter(l => l.tipo === 'intervalo' && Date.parse(l.inicio) >= inicio && Date.parse(l.fecha) <= fin);
  const vacio = { litros: null, estado: 'sin_datos', desde: null, hasta: null, referencias: [], motivo: 'No hay lecturas suficientes para el periodo.' };
  if (acumuladas.length && intervalos.length) return { ...vacio, estado: 'incompatible', motivo: 'No se mezclan mediciones acumuladas y de intervalos.' };
  if (intervalos.length) {
    let cursor = inicio;
    let incompleto = false;
    let litros = 0;
    for (const l of intervalos) {
      if (Date.parse(l.inicio) < cursor) return { ...vacio, estado: 'incompatible', motivo: 'Existen intervalos superpuestos.' };
      if (Date.parse(l.inicio) > cursor) incompleto = true;
      cursor = Date.parse(l.fecha);
      litros += l.valorLitros;
      if (!Number.isSafeInteger(litros)) return { ...vacio, estado: 'incompatible', motivo: 'El consumo excede la precisión admitida.' };
    }
    return { litros, estado: incompleto || cursor < fin ? 'parcial' : 'completo', desde: intervalos[0].inicio, hasta: intervalos.at(-1).fecha, referencias: intervalos.map(claveLectura), motivo: incompleto ? 'Hay intervalos sin información.' : null };
  }
  if (acumuladas.length < 2) return vacio;
  const primera = acumuladas[0];
  const ultima = acumuladas.at(-1);
  for (let i = 1; i < acumuladas.length; i++) {
    if (acumuladas[i].medidor !== primera.medidor || acumuladas[i].valorLitros < acumuladas[i - 1].valorLitros) return { ...vacio, estado: 'incompatible', motivo: 'Cambio de medidor o retroceso del contador: se requiere revisar las lecturas.' };
  }
  return { litros: ultima.valorLitros - primera.valorLitros, estado: Date.parse(primera.fecha) === inicio && Date.parse(ultima.fecha) === fin ? 'completo' : 'parcial', desde: primera.fecha, hasta: ultima.fecha, referencias: acumuladas.map(claveLectura), motivo: Date.parse(primera.fecha) > inicio ? 'Falta la lectura del inicio del periodo.' : Date.parse(ultima.fecha) < fin ? 'La última lectura aún no cubre todo el periodo solicitado.' : null };
}
