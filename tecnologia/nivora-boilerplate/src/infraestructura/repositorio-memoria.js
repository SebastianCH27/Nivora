import { RepositorioNivora } from '../dominio/contratos/puertos.js';

export function estadoVacio() {
  return { version: 1, suministros: [], lecturas: [], tarifas: [], recibos: [], notificaciones: [], dispositivos: [], entregas: [], accesos: [], incidencias: [], alertasHabilitadas: true, integracion: { estado: 'sin_datos', fechaFuente: null, ultimaActualizacion: null } };
}

export class RepositorioMemoria extends RepositorioNivora {
  constructor(estado = estadoVacio()) { super(); this.estado = estado; }
  leer() { return this.estado; }
  modificar(operacion) {
    // Las modificaciones son sincrónicas: no ejecutar await dentro del callback.
    const resultado = operacion(this.estado);
    if (resultado?.then) throw new Error('La operación del repositorio debe ser sincrónica.');
    return resultado;
  }
}
