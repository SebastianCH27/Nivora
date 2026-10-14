import { ErrorNivora } from './errores.js';

// Los periodos del ejemplo se delimitan en America/Lima (UTC-05:00).
// Las fechas recibidas y almacenadas siempre incluyen zona horaria.
export function limitesPeriodo(periodo) {
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(periodo)) {
    throw new ErrorNivora('DATOS_INVALIDOS', 'El periodo debe tener formato AAAA-MM.');
  }
  const [anio, mes] = periodo.split('-').map(Number);
  const inicio = Date.UTC(anio, mes - 1, 1, 5);
  const fin = Date.UTC(anio, mes, 1, 5);
  return { inicio, fin, dias: (fin - inicio) / 86400000 };
}

export function periodoDe(fecha) {
  return new Date(new Date(fecha).getTime() - 5 * 3600000).toISOString().slice(0, 7);
}

export function periodoAnterior(periodo) {
  const { inicio } = limitesPeriodo(periodo);
  return periodoDe(new Date(inicio - 1).toISOString());
}

export function fechaLimiteRecibo(disponibleDesde) {
  return new Date(new Date(disponibleDesde).getTime() + 7 * 86400000).toISOString();
}
