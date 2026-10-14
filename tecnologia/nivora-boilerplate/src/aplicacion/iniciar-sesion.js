import { validarAcceso } from '../dominio/acceso.js';
import { ErrorNivora } from '../dominio/errores.js';

export class IniciarSesion {
  constructor({ agua, identidad, repositorio, sesiones, reloj }) {
    Object.assign(this, { agua, identidad, repositorio, sesiones, reloj });
  }
  async ejecutar(codigo, dni) {
    validarAcceso(codigo, dni);
    const [suministro, persona] = await Promise.all([this.agua.consultarSuministro(codigo), this.identidad.consultarDni(dni)]);
    if (!suministro || !persona) throw new ErrorNivora('ACCESO_INVALIDO', 'El código de suministro o DNI no existe en las API de demostración.');
    const fecha = this.reloj();
    this.repositorio.modificar(estado => {
      estado.accesos.push({ suministro: codigo, dniUtilizado: dni, identidadConsultada: persona, fecha });
    });
    const token = this.sesiones.crear({ rol: 'consulta', codigo, dni, nombre: persona.nombre });
    return { token, usuario: { codigo, nombre: persona.nombre, rol: 'consulta', identidadSimulada: true } };
  }
  administrador(usuario, clave) {
    if (!this.sesiones.verificarAdministrador(usuario, clave)) throw new ErrorNivora('ACCESO_INVALIDO', 'Credenciales administrativas incorrectas.');
    return { token: this.sesiones.crear({ rol: 'admin', nombre: 'Administrador' }), usuario: { rol: 'admin', nombre: 'Administrador' } };
  }
}
