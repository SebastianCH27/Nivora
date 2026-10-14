import { FuenteAgua, FuenteIdentidad } from '../dominio/contratos/puertos.js';
import { ErrorNivora } from '../dominio/errores.js';

async function consultar(base, ruta, secreto, opciones = {}) {
  try {
    const respuesta = await fetch(new URL(ruta, base), { ...opciones, headers: { 'authorization': `Bearer ${secreto}`, 'content-type': 'application/json' }, signal: AbortSignal.timeout(3000) });
    if (respuesta.status === 404) return null;
    if (!respuesta.ok) throw new Error('Respuesta no válida.');
    return await respuesta.json();
  } catch {
    throw new ErrorNivora('SERVICIO_NO_DISPONIBLE', 'No se pudo validar o actualizar la información con la API externa simulada. Intente nuevamente.');
  }
}
export class FuenteAguaHttp extends FuenteAgua {
  constructor(base, secreto) { super(); this.base = base; this.secreto = secreto; }
  consultarSuministro(codigo) { return consultar(this.base, `/agua/suministros/${codigo}`, this.secreto); }
  obtenerDatos() { return consultar(this.base, '/agua/datos', this.secreto); }
  escenario(nombre) { return consultar(this.base, '/control/escenario', this.secreto, { method: 'POST', body: JSON.stringify({ nombre }) }); }
  estadoSimulacion() { return consultar(this.base, '/control/estado', this.secreto); }
}
export class FuenteIdentidadHttp extends FuenteIdentidad {
  constructor(base, secreto) { super(); this.base = base; this.secreto = secreto; }
  consultarDni(dni) { return consultar(this.base, `/identidad/${dni}`, this.secreto); }
}
