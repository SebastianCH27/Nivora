import { before, after, test } from 'node:test';
import assert from 'node:assert/strict';
import { componer } from '../src/app.js';
import { RepositorioMemoria } from '../src/infraestructura/repositorio-memoria.js';
import { crearSimulador } from '../simulacion/servidor.js';
let servidor, servidorSim, base, servicios, codigoCookie, adminCookie;
const secreto='secreto-exclusivo-de-pruebas';
const escuchar=app=>new Promise(resolve=>{const s=app.listen(0,'127.0.0.1',()=>resolve(s));});
async function llamar(ruta,{cookie='',method='GET',body}={}) {
  const r=await fetch(new URL(ruta,base),{method,headers:{'content-type':'application/json',cookie},body:body===undefined?undefined:JSON.stringify(body)});
  return {status:r.status,body:await r.json(),cookie:r.headers.get('set-cookie')?.split(';')[0]};
}
before(async()=> {
  servidorSim=await escuchar(crearSimulador(secreto).app);
  servicios=componer({repositorio:new RepositorioMemoria(),baseSimulacion:`http://127.0.0.1:${servidorSim.address().port}`,secreto,config:{adminUsuario:'admin',adminClave:'prueba-admin',cookieSegura:false,vapid:{}},reloj:()=>new Date().toISOString()});
  await servicios.actualizacion.ejecutar();
  servidor=await escuchar(servicios.app); base=`http://127.0.0.1:${servidor.address().port}`;
});
after(async()=> {await Promise.all([new Promise(r=>servidor.close(r)),new Promise(r=>servidorSim.close(r))]);});
test('las consultas protegidas requieren una sesión',async()=>assert.equal((await llamar('/api/panel')).status,401));
test('un suministro existente acepta distintos DNI válidos, sin exigir titularidad',async()=> {
  for (const dni of ['00000001','00000002']) {
    const r=await llamar('/api/acceso',{method:'POST',body:{codigo:'13100390',dni}});
    assert.equal(r.status,200); codigoCookie=r.cookie;
  }
  const e=servicios.repositorio.leer(); assert.equal(e.accesos.length,2); assert.equal(e.accesos[1].dniUtilizado,'00000002');
});
test('datos mal formados o inexistentes no crean sesiones',async()=> {
  const prev=servicios.sesiones.sesiones.size;
  assert.equal((await llamar('/api/acceso',{method:'POST',body:{codigo:'13100390',dni:'123'}})).status,400);
  assert.equal((await llamar('/api/acceso',{method:'POST',body:{codigo:'13100390',dni:'99999999'}})).status,401);
  assert.equal((await llamar('/api/acceso',{method:'POST',body:{codigo:'99999999',dni:'00000001'}})).status,401);
  assert.equal(servicios.sesiones.sesiones.size,prev);
});
test('consumo, costo y proyección comparten las reglas del backend',async()=> {
  const r=await llamar('/api/panel',{cookie:codigoCookie}); assert.equal(r.status,200);
  assert.equal(r.body.actual.consumo.litros,3500); assert.equal(r.body.actual.costo.totalCentimos,1711); assert.equal(r.body.actual.proyeccion.litros,10333);
  const otro=await llamar('/api/panel?codigo=13100391',{cookie:codigoCookie}); assert.equal(otro.body.suministro.codigo,'13100390');
});
test('una sesión de consulta no puede leer registros administrativos ni ejecutar escenarios',async()=> {
  assert.equal((await llamar('/api/admin/estado',{cookie:codigoCookie})).status,403);
  assert.equal((await llamar('/api/admin/escenario',{cookie:codigoCookie,method:'POST',body:{nombre:'consumo-alto'}})).status,403);
});
test('el administrador utiliza credenciales y sesión diferentes',async()=> {
  assert.equal((await llamar('/api/admin/acceso',{method:'POST',body:{usuario:'admin',clave:'incorrecta'}})).status,401);
  const r=await llamar('/api/admin/acceso',{method:'POST',body:{usuario:'admin',clave:'prueba-admin'}});
  assert.equal(r.status,200); adminCookie=r.cookie;
  assert.equal((await llamar('/api/admin/estado',{cookie:adminCookie})).status,200);
  assert.equal((await llamar('/api/panel',{cookie:adminCookie})).status,403);
});
test('duplicados no cambian el consumo ni la cantidad de lecturas almacenadas',async()=> {
  const antes=servicios.repositorio.leer().lecturas.length;
  await llamar('/api/admin/escenario',{cookie:adminCookie,method:'POST',body:{nombre:'duplicado'}});
  assert.equal(servicios.repositorio.leer().lecturas.length,antes);
  assert.equal((await llamar('/api/panel',{cookie:codigoCookie})).body.actual.consumo.litros,3500);
});
test('una lectura inválida queda como incidencia y no entra al cálculo',async()=> {
  const antes=servicios.repositorio.leer().lecturas.length;
  await llamar('/api/admin/escenario',{cookie:adminCookie,method:'POST',body:{nombre:'lectura-invalida'}});
  assert.equal(servicios.repositorio.leer().lecturas.length,antes);
  assert.ok(servicios.repositorio.leer().incidencias.some(i=>i.motivo.includes('Lectura rechazada')));
});
test('el consumo elevado genera un solo aviso por suministro y mes',async()=> {
  await llamar('/api/admin/escenario',{cookie:adminCookie,method:'POST',body:{nombre:'consumo-alto'}});
  await servicios.actualizacion.ejecutar(); await servicios.actualizacion.ejecutar();
  const avisos=(await llamar('/api/notificaciones',{cookie:codigoCookie})).body.filter(n=>n.tipo==='consumo');
  assert.equal(avisos.length,1); assert.equal((await llamar('/api/panel',{cookie:codigoCookie})).body.actual.consumo.litros,11500);
});
test('una caída de la empresa conserva las consultas y su fecha de actualización',async()=> {
  const antes=(await llamar('/api/panel',{cookie:codigoCookie})).body;
  await llamar('/api/admin/escenario',{cookie:adminCookie,method:'POST',body:{nombre:'caida-agua'}});
  const despues=await llamar('/api/panel',{cookie:codigoCookie});
  assert.equal(despues.status,200); assert.equal(despues.body.actual.consumo.litros,antes.actual.consumo.litros);
  assert.equal(despues.body.integracion.estado,'error'); assert.equal(despues.body.ultimaLectura,antes.ultimaLectura);
  assert.equal((await llamar('/api/acceso',{method:'POST',body:{codigo:'13100390',dni:'00000001'}})).status,503);
  await llamar('/api/admin/escenario',{cookie:adminCookie,method:'POST',body:{nombre:'restaurar-agua'}});
  assert.equal((await llamar('/api/panel',{cookie:codigoCookie})).body.integracion.estado,'actualizada');
});
test('una caída de identidad se distingue de un DNI inexistente',async()=> {
  await llamar('/api/admin/escenario',{cookie:adminCookie,method:'POST',body:{nombre:'caida-identidad'}});
  assert.equal((await llamar('/api/acceso',{method:'POST',body:{codigo:'13100390',dni:'00000001'}})).status,503);
  await llamar('/api/admin/escenario',{cookie:adminCookie,method:'POST',body:{nombre:'restaurar-identidad'}});
});
test('el recibo viene de la empresa y su aviso no se duplica al reintentar',async()=> {
  for(let i=0;i<2;i++) await llamar('/api/admin/escenario',{cookie:adminCookie,method:'POST',body:{nombre:'recibo'}});
  const recibos=(await llamar('/api/recibos',{cookie:codigoCookie})).body;
  const r=recibos.find(r=>r.periodo==='2026-09'); assert.equal(r.totalCentimos,3658);
  assert.equal(Date.parse(r.fechaLimite)-Date.parse(r.disponibleDesde),7*86400000);
  const avisos=(await llamar('/api/notificaciones',{cookie:codigoCookie})).body;
  assert.equal(avisos.filter(n=>n.clave===`recibo:${r.id}`).length,1);
});
test('se pueden marcar avisos propios como leídos, no los de otro suministro',async()=> {
  const propios=(await llamar('/api/notificaciones',{cookie:codigoCookie})).body;
  assert.equal((await llamar(`/api/notificaciones/${propios[0].id}/leida`,{cookie:codigoCookie,method:'POST',body:{}})).status,200);
  const ajeno=servicios.repositorio.leer().notificaciones.find(n=>n.suministro==='13100391');
  assert.equal((await llamar(`/api/notificaciones/${ajeno.id}/leida`,{cookie:codigoCookie,method:'POST',body:{}})).status,404);
});
test('cerrar sesión invalida el token para nuevas consultas',async()=> {
  assert.equal((await llamar('/api/salir',{cookie:codigoCookie,method:'POST',body:{}})).status,200);
  assert.equal((await llamar('/api/panel',{cookie:codigoCookie})).status,401);
});
