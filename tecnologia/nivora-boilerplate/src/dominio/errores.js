export class ErrorNivora extends Error {
  constructor(codigo, mensaje) {
    super(mensaje);
    this.name = 'ErrorNivora';
    this.codigo = codigo;
  }
}
