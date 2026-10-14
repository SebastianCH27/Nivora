import { ErrorNivora } from '../dominio/errores.js';

export class GestionarAdministracion {
  constructor({ repositorio, agua, actualizacion }) { Object.assign(this, { repositorio, agua, actualizacion }); }
  consultarEstado() {
    const e = this.repositorio.leer();
    return { integracion: e.integracion, suministros: e.suministros, lecturas: e.lecturas.length, accesos: e.accesos.slice(-100).reverse(), totalAccesos: e.accesos.length, incidencias: e.incidencias.slice(-100).reverse(), alertasHabilitadas: e.alertasHabilitadas, notificaciones: e.notificaciones.length, dispositivosActivos: e.dispositivos.filter(d => d.activo).length, entregas: e.entregas.slice(-100) };
  }
  configurarAlertas(habilitadas) {
    if (typeof habilitadas !== 'boolean') throw new ErrorNivora('DATOS_INVALIDOS', 'Indique habilitadas como booleano.');
    this.repositorio.modificar(e => { e.alertasHabilitadas = habilitadas; });
    return { habilitadas };
  }
  async escenario(nombre) {
    const nombres = ['consumo-alto','duplicado','lectura-invalida','recibo','caida-agua','restaurar-agua','caida-identidad','restaurar-identidad'];
    if (!nombres.includes(nombre)) throw new ErrorNivora('DATOS_INVALIDOS', 'Escenario desconocido.');
    const resultado = await this.agua.escenario(nombre);
    await this.actualizacion.ejecutar();
    return resultado;
  }
}
