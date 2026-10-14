import { ErrorNivora } from '../dominio/errores.js';

export class GestionarNotificaciones {
  constructor({ repositorio, emisor, identificador, reloj }) { Object.assign(this, { repositorio, emisor, identificador, reloj }); this.enCurso = null; }
  consultar(codigo) { return this.repositorio.leer().notificaciones.filter(n => n.suministro === codigo).map(n => ({ ...n })).reverse(); }
  leer(codigo, id) {
    this.repositorio.modificar(e => {
      const aviso = e.notificaciones.find(n => n.id === id && n.suministro === codigo);
      if (!aviso) throw new ErrorNivora('NO_ENCONTRADO', 'Notificación no encontrada.');
      aviso.leida = true;
    });
  }
  asociar(codigo, dni, suscripcion) {
    if (!this.emisor.habilitado) throw new ErrorNivora('DATOS_NO_DISPONIBLES', 'Configure las claves VAPID antes de activar notificaciones al dispositivo.');
    this.emisor.validarSuscripcion(suscripcion);
    let dispositivo;
    this.repositorio.modificar(e => {
      dispositivo = e.dispositivos.find(d => d.endpoint === suscripcion.endpoint);
      if (!dispositivo) {
        dispositivo = { id: this.identificador(), endpoint: suscripcion.endpoint };
        e.dispositivos.push(dispositivo);
      }
      // La asociación de un mismo navegador cambia al suministro elegido.
      Object.assign(dispositivo, { suministro: codigo, dni, suscripcion, activo: true, fecha: this.reloj() });
    });
    return { id: dispositivo.id, activo: true };
  }
  desactivar(codigo, dni, id) {
    this.repositorio.modificar(e => {
      const dispositivo = e.dispositivos.find(d => d.id === id && d.suministro === codigo && d.dni === dni);
      if (!dispositivo) throw new ErrorNivora('NO_ENCONTRADO', 'Dispositivo no encontrado para esta consulta.');
      dispositivo.activo = false;
    });
  }
  entregar() {
    if (!this.enCurso) this.enCurso = this.procesar().finally(() => { this.enCurso = null; });
    return this.enCurso;
  }
  async procesar() {
    if (!this.emisor.habilitado) return;
    const estado = this.repositorio.leer();
    for (const d of estado.dispositivos.filter(d => d.activo)) {
      for (const n of estado.notificaciones.filter(n => n.suministro === d.suministro && Date.parse(n.fecha) >= Date.parse(d.fecha))) {
        const clave = `${d.id}:${n.id}`;
        const entrega = estado.entregas.find(e => e.clave === clave);
        if (entrega?.estado === 'aceptada' || entrega?.estado === 'expirada' || (entrega?.proximoIntento && Date.parse(entrega.proximoIntento) > Date.parse(this.reloj()))) continue;
        try {
          await this.emisor.enviar(d, n);
          this.repositorio.modificar(e => {
            const anterior = e.entregas.find(x => x.clave === clave);
            const resultado = { clave, estado: 'aceptada', fecha: this.reloj(), intentos: (anterior?.intentos ?? 0) + 1 };
            if (anterior) Object.assign(anterior, resultado); else e.entregas.push(resultado);
          });
        } catch (error) {
          this.repositorio.modificar(e => {
            const anterior = e.entregas.find(x => x.clave === clave);
            const intentos = (anterior?.intentos ?? 0) + 1;
            const expirada = [404,410].includes(error.statusCode);
            if (expirada) d.activo = false;
            const resultado = { clave, estado: expirada ? 'expirada' : 'pendiente', fecha: this.reloj(), intentos, proximoIntento: new Date(Date.parse(this.reloj()) + Math.min(3600000, 30000 * 2 ** Math.min(intentos, 7))).toISOString(), motivo: 'Fallo en la entrega al servicio de notificaciones.' };
            if (anterior) Object.assign(anterior, resultado); else e.entregas.push(resultado);
          });
        }
      }
    }
  }
}
