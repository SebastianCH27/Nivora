import { readFile, mkdir, writeFile, rename } from 'node:fs/promises';
import { dirname } from 'node:path';
import { RepositorioMemoria, estadoVacio } from './repositorio-memoria.js';

// Adaptador educativo para una sola instancia. No sustituye una BD transaccional.
export class RepositorioArchivo extends RepositorioMemoria {
  static async abrir(ruta) {
    let estado;
    try { estado = JSON.parse(await readFile(ruta, 'utf8')); }
    catch (error) { if (error.code !== 'ENOENT') throw error; estado = estadoVacio(); }
    if (estado.version !== 1) throw new Error('Versión de almacenamiento no compatible.');
    return new RepositorioArchivo(ruta, estado);
  }
  constructor(ruta, estado) {
    super(estado); this.ruta = ruta; this.cola = Promise.resolve(); this.temporizador = null;
  }
  modificar(operacion) {
    const resultado = super.modificar(operacion);
    if (!this.temporizador) this.temporizador = setTimeout(() => {
      this.temporizador = null;
      this.persistir().catch(error => { console.error('No se pudo persistir el estado:', error.message); });
    }, 250);
    return resultado;
  }
  persistir() {
    const contenido = JSON.stringify(this.estado);
    this.cola = this.cola.catch(() => {}).then(async () => {
      await mkdir(dirname(this.ruta), { recursive: true });
      await writeFile(`${this.ruta}.tmp`, contenido, { mode: 0o600 });
      await rename(`${this.ruta}.tmp`, this.ruta);
    });
    return this.cola;
  }
  async cerrar() { clearTimeout(this.temporizador); this.temporizador = null; await this.persistir(); }
}
