import { setTimeout as esperar } from 'node:timers/promises';
import { performance } from 'node:perf_hooks';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import os from 'node:os';

const argumentos = Object.fromEntries(process.argv.slice(2).map(a => { const i=a.indexOf('='); return i<0?[a.replace(/^--/,''),'true']:[a.slice(0,i).replace(/^--/,''),a.slice(i+1)]; }));
const usuarios=Number(argumentos.usuarios||5000);
const duracion=Number(argumentos.segundos||1800);
const rampa=Number(argumentos.rampa||300);
const pausaMin=Number(argumentos['pausa-min']||3000);
const pausaMax=Number(argumentos['pausa-max']||10000);
const base=argumentos.url||'http://127.0.0.1:3000';
if (![usuarios,duracion,rampa,pausaMin,pausaMax].every(Number.isFinite) || usuarios<1 || usuarios>10000 || duracion<1 || rampa<1 || pausaMin<1 || pausaMax<pausaMin) throw new Error('Argumentos de carga inválidos.');
const operaciones=['consumo','costos','proyeccion','historial','recibos','notificaciones'];
const metricas=Object.fromEntries(['acceso',...operaciones].map(op=>[op,{solicitudes:0,errores:0,incorrectas:0,histograma:new Uint32Array(120002)}]));
let activos=0,maxActivos=0,minActivosSostenidos=Infinity,ingresaron=0;
const inicio=performance.now();
const inicioSostenido=inicio+rampa*1000;
const fin=inicioSostenido+duracion*1000;
const monitoreo=setInterval(()=> {
  if (performance.now()>=inicioSostenido) minActivosSostenidos=Math.min(minActivosSostenidos,activos);
},200);
const progreso=setInterval(()=>console.log(`Usuarios activos: ${activos}/${usuarios}; transcurrido: ${Math.round((performance.now()-inicio)/1000)} s`),10000);
function registrar(op,tiempo,error,incorrecta) {
  const m=metricas[op];m.solicitudes++;m.errores+=error?1:0;m.incorrectas+=incorrecta?1:0;
  m.histograma[Math.min(120001,Math.ceil(tiempo))]++;
}
async function solicitud(op,cookie,codigo,body) {
  const t=performance.now();
  try {
    const r=await fetch(`${base}/api/${op==='acceso'?'acceso':op}`,{method:body?'POST':'GET',headers:{'content-type':'application/json',cookie},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(15000)});
    const dato=await r.json();
    let incorrecta=false;
    if (r.ok && op==='consumo') incorrecta=dato.consumo?.litros!==null && (!Number.isSafeInteger(dato.consumo?.litros)||dato.consumo.litros<0);
    if (r.ok && ['recibos','notificaciones'].includes(op)) incorrecta=!Array.isArray(dato)||dato.some(e=>e.suministro!==codigo);
    if (r.ok && op==='historial') incorrecta=!Array.isArray(dato);
    if (r.ok && ['costos','proyeccion'].includes(op) && dato!==null) incorrecta=op==='costos'?!Number.isSafeInteger(dato.totalCentimos):!Number.isSafeInteger(dato.litros);
    registrar(op,performance.now()-t,!r.ok,incorrecta);
    return r.ok?{cookie:r.headers.get('set-cookie')?.split(';')[0],dato}:null;
  } catch {registrar(op,performance.now()-t,true,false);return null;}
}
async function usuario(i) {
  const codigo=['13100390','13100391','13100392'][i%3];
  const dni=['00000001','00000002','00000003'][i%3];
  const ingreso=await solicitud('acceso','',codigo,{codigo,dni});
  if (!ingreso?.cookie) return;
  activos++;ingresaron++;maxActivos=Math.max(maxActivos,activos);
  // Cada usuario mantiene su propia cookie y ejecuta consultas con pausas.
  while(performance.now()<fin) {
    const op=operaciones[Math.floor(Math.random()*operaciones.length)];
    await solicitud(op,ingreso.cookie,codigo);
    await esperar(Math.min(pausaMin+Math.random()*(pausaMax-pausaMin),Math.max(0,fin-performance.now())));
  }
  activos--;
}
console.log(`Carga: ${usuarios} sesiones independientes, rampa ${rampa}s + fase sostenida ${duracion}s. Destino: ${base}`);
console.log('Las pausas forman parte del escenario. Esta prueba no equivale a 5000 solicitudes por segundo.');
try {
  const salud=await fetch(`${base}/api/salud`,{signal:AbortSignal.timeout(3000)});
  if(!salud.ok)throw new Error();
}catch {clearInterval(monitoreo);clearInterval(progreso);throw new Error('Inicie Nivora con npm start en otra terminal.');}
const promesas=[];
for(let i=0;i<usuarios;i++) {
  const instante=inicio+(i/usuarios)*rampa*1000;
  await esperar(Math.max(0,instante-performance.now()));
  promesas.push(usuario(i));
}
await Promise.all(promesas);clearInterval(monitoreo);clearInterval(progreso);
const resultados=Object.fromEntries(Object.entries(metricas).map(([op,m])=> {
  let acumulado=0,p95=null;
  for(let i=0;i<m.histograma.length;i++){acumulado+=m.histograma[i];if(acumulado>=Math.ceil(m.solicitudes*.95)&&m.solicitudes){p95=i;break;}}
  return [op,{solicitudes:m.solicitudes,errores:m.errores,porcentajeErrores:m.solicitudes?100*m.errores/m.solicitudes:null,verificacionesFallidas:m.incorrectas,p95ms:p95}];
}));
const informe={fecha:new Date().toISOString(),entornoGenerador:{node:process.version,plataforma:os.platform(),cpus:os.cpus().length,memoriaTotalBytes:os.totalmem(),memoriaProceso:process.memoryUsage()},escenario:{usuarios,duracionSostenidaSegundos:duracion,rampaSegundos:rampa,pausaMinMs:pausaMin,pausaMaxMs:pausaMax,base},ingresaron,maxActivos,minActivosSostenidos:Number.isFinite(minActivosSostenidos)?minActivosSostenidos:null,resultados,nota:'Mide desde el generador, incluyendo red y API. La infraestructura del servidor debe documentarse aparte. Las comprobaciones de respuesta son básicas; complementar con las pruebas de exactitud, permisos, deduplicación y recursos del servidor.'};
informe.metasObservadas=ingresaron===usuarios&&informe.minActivosSostenidos===usuarios&&Object.values(resultados).every(r=>r.solicitudes>0&&r.p95ms<=2000&&r.porcentajeErrores<1&&r.verificacionesFallidas===0);
informe.perfilRequerido=usuarios===5000&&duracion>=1800;
await mkdir(resolve('resultados'),{recursive:true});
const ruta=resolve('resultados',`carga-${Date.now()}.json`);await writeFile(ruta,JSON.stringify(informe,null,2)+'\n');
console.table(resultados);console.log(`Informe: ${ruta}`);
console.log(informe.perfilRequerido?'Se ejecutó el perfil de 5000 usuarios; evaluar el informe y complementar la evidencia.':'Perfil reducido: no demuestra cumplimiento del requisito de 5000 usuarios durante 30 minutos.');
if(!informe.metasObservadas)process.exitCode=1;
