import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validarAcceso } from '../src/dominio/acceso.js';
import { validarLectura, calcularConsumo } from '../src/dominio/lecturas.js';
import { calcularCosto, proyectarConsumo, superaMesAnterior } from '../src/dominio/calculos.js';
import { limitesPeriodo, periodoDe, periodoAnterior, fechaLimiteRecibo } from '../src/dominio/periodos.js';
import { crearDatosSimulados } from '../simulacion/datos.js';

const datos = crearDatosSimulados();
const suministro = datos.suministros[0];
const tarifa = datos.tarifas[0];
const lecturas = datos.lecturas.filter(l=>l.suministro===suministro.codigo).map(l=>validarLectura(l,suministro));
test('el acceso usa dos campos de ocho dígitos en la demostración', () => {
  assert.deepEqual(validarAcceso('13100390','00000002'),{codigo:'13100390',dni:'00000002'});
  for (const [codigo,dni] of [[null,'00000001'],['13100390',''],['13100390','abcdef12'],[13100390,'00000001']]) assert.throws(()=>validarAcceso(codigo,dni));
});
test('los meses respetan Lima y el cambio de año; febrero bisiesto tiene 29 días',()=> {
  assert.equal(periodoDe('2026-10-01T04:59:00Z'),'2026-09');
  assert.equal(periodoAnterior('2026-01'),'2025-12');
  assert.equal(limitesPeriodo('2024-02').dias,29);
  assert.throws(()=>limitesPeriodo('2026-13'));
});
test('validar lecturas rechaza valores, unidades, fechas y suministros inválidos',()=> {
  for (const cambio of [{valor:-1},{valor:'100'},{valor:null},{unidad:'galones'},{fecha:'2026-10-11'},{suministro:'99999999'},{medidor:'OTRO'}]) assert.throws(()=>validarLectura({...datos.lecturas[0],...cambio},suministro));
  assert.equal(validarLectura({...datos.lecturas[0],valor:1.125,unidad:'m3'},suministro).valorLitros,1125);
});
test('consumo acumulado del mes anterior: diferencia del contador, no suma de contadores',()=> {
  const {inicio,fin}=limitesPeriodo('2026-09');
  const c=calcularConsumo(lecturas,inicio,fin);
  assert.equal(c.litros,9000); assert.equal(c.estado,'completo');
});
test('una lectura inicial faltante impide declarar cobertura completa',()=> {
  const {inicio,fin}=limitesPeriodo('2026-09');
  assert.equal(calcularConsumo(lecturas.filter(l=>l.fecha!=='2026-09-01T05:00:00.000Z'),inicio,fin).estado,'sin_datos');
});
test('un contador que retrocede no produce un consumo negativo válido',()=> {
  const {inicio,fin}=limitesPeriodo('2026-09');
  const registros=structuredClone(lecturas); registros.find(l=>l.fecha==='2026-10-01T05:00:00.000Z').valorLitros=1;
  assert.equal(calcularConsumo(registros,inicio,fin).estado,'incompatible');
});
test('intervalos contiguos se suman y los superpuestos se rechazan',()=> {
  const a={id:'a',tipo:'intervalo',fecha:'2026-09-15T05:00:00Z',inicio:'2026-09-01T05:00:00Z',valorLitros:100,fuente:'demo',suministro:'13100390'};
  const b={...a,id:'b',inicio:a.fecha,fecha:'2026-10-01T05:00:00Z',valorLitros:200};
  const {inicio,fin}=limitesPeriodo('2026-09');
  assert.equal(calcularConsumo([a,b],inicio,fin).litros,300);
  assert.equal(calcularConsumo([a,{...b,inicio:a.inicio}],inicio,fin).estado,'incompatible');
  assert.equal(calcularConsumo([{...a,inicio:'2026-09-02T05:00:00Z'},b],inicio,fin).estado,'parcial');
});
test('el costo aplica tramos, conceptos y redondeo en céntimos',()=> {
  assert.equal(calcularCosto(3500,tarifa).totalCentimos,1711);
  assert.equal(calcularCosto(15000,tarifa).conceptos.agua,4250);
  assert.throws(()=>calcularCosto(2.5,tarifa));
  assert.throws(()=>calcularCosto(100,{...tarifa,tramos:[]}));
});
test('la proyección usa el tiempo hasta la última lectura y exige cobertura desde el inicio',()=> {
  const {inicio,fin}=limitesPeriodo('2026-10');
  const c=calcularConsumo(lecturas,inicio,Date.parse(datos.fechaFuente));
  const p=proyectarConsumo(c,inicio,fin);
  assert.equal(p.litros,10333);
  assert.equal(proyectarConsumo({...c,estado:'parcial'},inicio,fin),null);
});
test('la alerta compara el mes actual con el total del anterior y no alerta en igualdad',()=> {
  const completo=litros=>({litros,estado:'completo'});
  assert.equal(superaMesAnterior(completo(9000),completo(9000)),false);
  assert.equal(superaMesAnterior(completo(9001),completo(9000)),true);
  assert.equal(superaMesAnterior({litros:99999,estado:'parcial'},completo(9000)),false);
});
test('el plazo del recibo es exactamente una semana desde disponibilidad',()=> {
  assert.equal(fechaLimiteRecibo('2026-10-11T17:00:00Z'),'2026-10-18T17:00:00.000Z');
});
