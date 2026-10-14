import { readdir, readFile } from 'node:fs/promises';
import { dirname, resolve, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
const raiz = fileURLToPath(new URL('../src/',import.meta.url));
async function archivos(ruta) {
  const resultados = [];
  for (const entrada of await readdir(ruta,{withFileTypes:true})) {
    const destino = resolve(ruta,entrada.name);
    if (entrada.isDirectory()) resultados.push(...await archivos(destino));
    else if (destino.endsWith('.js')) resultados.push(destino);
  }
  return resultados;
}
const errores = [];
let comprobados = 0;
for (const capa of ['dominio','aplicacion']) {
  for (const archivo of await archivos(resolve(raiz,capa))) {
    comprobados++;
    const contenido = await readFile(archivo,'utf8');
    for (const coincidencia of contenido.matchAll(/(?:\bfrom\s*|\bimport\s*\(\s*|\brequire\s*\(\s*|\bimport\s*)['"]([^'"]+)['"]/g)) {
      const ruta = coincidencia[1];
      const destino = relative(raiz,resolve(dirname(archivo),ruta)).replaceAll('\\','/');
      if (!ruta.startsWith('.') || !(destino.startsWith('dominio/') || (capa === 'aplicacion' && destino.startsWith('aplicacion/')))) errores.push(`${relative(raiz,archivo)} importa ${ruta}`);
    }
  }
}
if (errores.length) { console.error(errores.join('\n')); process.exitCode = 1; }
else console.log(`OK: ${comprobados} archivos del núcleo solo importan las capas permitidas. Comprobación de importaciones estáticas, no garantía completa de arquitectura.`);
