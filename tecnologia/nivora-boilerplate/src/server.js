import { randomBytes } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { RepositorioArchivo } from './infraestructura/repositorio-archivo.js';
import { crearSimulador } from '../simulacion/servidor.js';
import { componer } from './app.js';

const config = {
  adminUsuario: process.env.ADMIN_USUARIO || 'admin',
  adminClave: process.env.ADMIN_CLAVE || 'NivoraDemo2026!',
  cookieSegura: process.env.COOKIE_SEGURA === 'true',
  origenPublico: process.env.ORIGEN_PUBLICO || '',
  vapid: { publicKey: process.env.VAPID_PUBLIC_KEY, privateKey: process.env.VAPID_PRIVATE_KEY, subject: process.env.VAPID_SUBJECT }
};
const puerto = Number(process.env.PORT || 3000);
const puertoSim = Number(process.env.SIM_PORT || 3001);
const host = process.env.HOST || '127.0.0.1';
const frecuencia = Number(process.env.ACTUALIZACION_MS || 10000);
if (![puerto, puertoSim].every(n => Number.isInteger(n) && n > 0 && n < 65536) || !Number.isFinite(frecuencia) || frecuencia < 1000 || puerto === puertoSim) throw new Error('Revise los puertos y ACTUALIZACION_MS.');
const escuchar = (app, puerto, host) => new Promise((resolve,reject) => {
  const server = app.listen(puerto, host, () => resolve(server)); server.once('error', reject);
});
const secreto = randomBytes(32).toString('base64url');
const simulacion = crearSimulador(secreto);
const servidorSim = await escuchar(simulacion.app, puertoSim, '127.0.0.1');
const repositorio = await RepositorioArchivo.abrir(fileURLToPath(new URL('../datos/nivora.json', import.meta.url)));
// El simulador retoma las lecturas aceptadas después de un reinicio local.
if (repositorio.leer().integracion.fechaFuente) {
  simulacion.datos.lecturas = structuredClone(repositorio.leer().lecturas).map(({ recibidaEn, valorLitros, ...l }) => l);
  simulacion.datos.fechaFuente = repositorio.leer().integracion.fechaFuente;
  simulacion.datos.recibos = structuredClone(repositorio.leer().recibos);
}
const servicios = componer({ repositorio, baseSimulacion: `http://127.0.0.1:${puertoSim}`, secreto, config });
await servicios.actualizacion.ejecutar();
const servidor = await escuchar(servicios.app, puerto, host);
let ciclo = false;
const temporizador = setInterval(async () => {
  if (ciclo) return;
  ciclo = true;
  try { simulacion.avanzar(); await servicios.actualizacion.ejecutar(); servicios.sesiones.depurar(); }
  catch (error) { console.error('Error de actualización:', error.message); }
  finally { ciclo = false; }
}, frecuencia);
const entrega = setInterval(() => { servicios.notificaciones.entregar().catch(e => console.error('Error de entrega:', e.message)); }, 5000);
console.log(`Nivora: http://localhost:${puerto}`);
console.log('Entorno académico: datos, DNI y tarifas ficticios. Reloj de datos: octubre de 2026.');
console.log('Consulta de prueba: código 13100390 / DNI 00000001 (también 00000002 y 00000003).');
if (!process.env.ADMIN_CLAVE) console.log('Administrador de demostración: admin / NivoraDemo2026!');
console.log(`Notificaciones al dispositivo: ${servicios.emisor.habilitado ? 'Web Push configurado; verificar en un dispositivo' : 'pendientes de configurar claves VAPID (.env)'}.`);
let cerrando = false;
async function cerrar() {
  if (cerrando) return; cerrando = true;
  clearInterval(temporizador); clearInterval(entrega);
  await Promise.all([new Promise(resolve => servidor.close(resolve)), new Promise(resolve => servidorSim.close(resolve))]);
  if (servicios.actualizacion.enCurso) await servicios.actualizacion.enCurso;
  if (servicios.notificaciones.enCurso) await servicios.notificaciones.enCurso;
  await repositorio.cerrar(); process.exit(0);
}
process.once('SIGINT', cerrar); process.once('SIGTERM', cerrar);
