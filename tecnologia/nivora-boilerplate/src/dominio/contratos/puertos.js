// Contratos del núcleo. No importan Express, HTTP, archivos ni Web Push.
// Las implementaciones se eligen en la raíz de composición (app.js).
export class FuenteAgua {
  async consultarSuministro(codigo) { throw new Error('Implementar consultarSuministro'); }
  async obtenerDatos() { throw new Error('Implementar obtenerDatos'); }
  async escenario(nombre) { throw new Error('Escenarios solo disponibles en el entorno simulado.'); }
}
export class FuenteIdentidad {
  async consultarDni(dni) { throw new Error('Implementar consultarDni'); }
}
export class RepositorioNivora {
  leer() { throw new Error('Implementar leer'); }
  modificar(operacion) { throw new Error('Implementar modificar'); }
}
export class EmisorNotificaciones {
  constructor() { this.habilitado = false; this.publicKey = ''; }
  validarSuscripcion(suscripcion) { throw new Error('Implementar validarSuscripcion'); }
  async enviar(dispositivo, aviso) { throw new Error('Implementar enviar'); }
}
export class GestorSesiones {
  crear(datos) { throw new Error('Implementar crear'); }
  consultar(token) { throw new Error('Implementar consultar'); }
  cerrar(token) { throw new Error('Implementar cerrar'); }
  verificarAdministrador(usuario, clave) { throw new Error('Implementar verificarAdministrador'); }
}
