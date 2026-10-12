import { randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';
import { GestorSesiones } from '../dominio/contratos/puertos.js';

export class SesionesMemoria extends GestorSesiones {
  constructor(usuario, clave) {
    super(); this.sesiones = new Map(); this.usuario = usuario;
    this.sal = randomBytes(16); this.hash = scryptSync(clave, this.sal, 32);
  }
  crear(datos) {
    const token = randomBytes(32).toString('base64url');
    this.sesiones.set(token, { ...datos, expira: Date.now() + 8 * 3600000 });
    return token;
  }
  consultar(token) {
    const datos = this.sesiones.get(token);
    if (datos && datos.expira > Date.now()) return datos;
    this.sesiones.delete(token); return null;
  }
  cerrar(token) { this.sesiones.delete(token); }
  depurar() { for (const [token, datos] of this.sesiones) if (datos.expira <= Date.now()) this.sesiones.delete(token); }
  verificarAdministrador(usuario, clave) {
    if (typeof clave !== 'string' || clave.length > 128 || usuario !== this.usuario) return false;
    return timingSafeEqual(this.hash, scryptSync(clave, this.sal, 32));
  }
}
