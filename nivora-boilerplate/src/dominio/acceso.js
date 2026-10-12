import { ErrorNivora } from './errores.js';

export function validarAcceso(codigo, dni) {
  if (typeof codigo !== 'string' || !/^\d{8}$/.test(codigo)) {
    throw new ErrorNivora('DATOS_INVALIDOS', 'Ingrese un código de suministro de 8 dígitos para esta demostración.');
  }
  if (typeof dni !== 'string' || !/^\d{8}$/.test(dni)) {
    throw new ErrorNivora('DATOS_INVALIDOS', 'Ingrese un DNI de 8 dígitos.');
  }
  // No se exige que el DNI corresponda al titular del suministro.
  return { codigo, dni };
}
