import {test} from 'node:test';
import assert from 'node:assert/strict';
import {GestionarNotificaciones} from '../src/aplicacion/notificaciones.js';
import {RepositorioMemoria} from '../src/infraestructura/repositorio-memoria.js';
test('un fallo de envío conserva el aviso y lo deja pendiente para un nuevo intento',async()=> {
  const r=new RepositorioMemoria(); let fecha='2026-10-11T17:00:00Z'; let fallar=true; let envios=0;
  const emisor={habilitado:true,validarSuscripcion:()=>{},enviar:async()=>{envios++;if(fallar)throw new Error('Fallo simulado');}};
  const n=new GestionarNotificaciones({repositorio:r,emisor,identificador:()=> 'dispositivo-1',reloj:()=>fecha});
  n.asociar('13100390','00000001',{endpoint:'https://proveedor.example/suscripcion'});
  r.modificar(e=>e.notificaciones.push({id:'aviso-1',suministro:'13100390',fecha,titulo:'Prueba',leida:false}));
  await n.entregar(); assert.equal(r.leer().notificaciones.length,1); assert.equal(r.leer().entregas[0].estado,'pendiente');
  await n.entregar(); assert.equal(envios,1);
  fecha='2026-10-11T18:00:00Z';fallar=false;
  await n.entregar(); assert.equal(r.leer().entregas[0].estado,'aceptada');
  await n.entregar();assert.equal(envios,2);
});
