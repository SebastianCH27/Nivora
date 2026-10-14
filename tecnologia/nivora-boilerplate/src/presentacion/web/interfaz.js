const $ = id => document.getElementById(id);
const escapar = valor => String(valor ?? '').replace(/[&<>"']/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[c]));
const numero = n => n === null || n === undefined ? 'Sin datos' : new Intl.NumberFormat('es-PE', { maximumFractionDigits: 3 }).format(n);
const soles = n => n === null || n === undefined ? 'Sin datos' : new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' }).format(n / 100);
const fecha = f => f ? new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Lima' }).format(new Date(f)) : 'Sin información';
const mes = p => new Intl.DateTimeFormat('es-PE', { month: 'long', year: 'numeric', timeZone: 'America/Lima' }).format(new Date(`${p}-15T12:00:00-05:00`));
let sesion = null;
let vista = 'resumen';
let datos = null;
let recibos = [];
let avisos = [];
let pushConfig = { habilitado: false };
let modoAdmin = false;
let refrescando = false;

async function api(ruta, opciones = {}) {
  const respuesta = await fetch(ruta, { ...opciones, headers: { 'Content-Type': 'application/json', ...opciones.headers }, credentials: 'same-origin', cache: 'no-store' });
  const cuerpo = await respuesta.json();
  if (!respuesta.ok) {
    const error = new Error(cuerpo.error || 'No se pudo completar la consulta.'); error.estado = respuesta.status; throw error;
  }
  return cuerpo;
}
function mensaje(texto, fallo = false) {
  $('mensaje-operacion').textContent = texto;
  $('mensaje-operacion').className = `mensaje${fallo ? ' fallo' : ''}`;
  $('mensaje-operacion').hidden = false;
}
function mostrarAcceso() { $('acceso').hidden = false; $('aplicacion').hidden = true; sesion = null; }
async function entrar(usuario) {
  sesion = usuario; vista = usuario.rol === 'admin' ? 'admin' : 'resumen';
  $('acceso').hidden = true; $('aplicacion').hidden = false;
  $('nombre-persona').textContent = usuario.nombre;
  $('codigo-activo').textContent = usuario.codigo ? `Suministro ${usuario.codigo}` : 'Administración';
  $('navegacion').hidden = usuario.rol === 'admin';
  if (usuario.rol === 'consulta') pushConfig = await api('/api/push/configuracion');
  await refrescar();
}
for (const [formulario, ruta] of [['form-consulta','/api/acceso'],['form-admin','/api/admin/acceso']]) {
  $(formulario).addEventListener('submit', async event => {
    event.preventDefault(); $('error-acceso').hidden = true;
    const boton = event.target.querySelector('button'); boton.disabled = true;
    try { await entrar(await api(ruta, { method: 'POST', body: JSON.stringify(Object.fromEntries(new FormData(event.target))) })); }
    catch(error) { $('error-acceso').textContent = error.message; $('error-acceso').hidden = false; }
    finally { boton.disabled = false; }
  });
}
$('cambiar-acceso').addEventListener('click', () => {
  modoAdmin = !modoAdmin;
  $('form-consulta').hidden = modoAdmin; $('form-admin').hidden = !modoAdmin;
  $('titulo-acceso').textContent = modoAdmin ? 'Administración de Nivora' : 'Consulta tu suministro';
  $('descripcion-acceso').textContent = modoAdmin ? 'Acceso separado para supervisar la integración y los escenarios de prueba.' : 'Ingresa el código de tu hogar y el DNI de la persona que consulta.';
  $('cambiar-acceso').textContent = modoAdmin ? 'Volver a consultar un suministro' : 'Acceso administrativo';
  $('datos-prueba').hidden = modoAdmin; $('error-acceso').hidden = true;
});
$('salir').addEventListener('click', async () => {
  try { await api('/api/salir', { method: 'POST', body: '{}' }); mostrarAcceso(); }
  catch(error) { mensaje(error.message, true); }
});
$('navegacion').addEventListener('click', event => {
  const boton = event.target.closest('[data-vista]'); if (!boton) return;
  vista = boton.dataset.vista; $('mensaje-operacion').hidden = true; renderizar();
});

function grafico(elementos) {
  const validos = elementos.filter(e => e.consumo.litros !== null);
  if (!validos.length) return '<p class="vacio">No hay datos suficientes para el gráfico.</p>';
  const max = Math.max(1, ...validos.map(e => e.consumo.litros));
  const ancho = 430 / elementos.length;
  return `<svg class="grafico" viewBox="0 0 500 205" role="img" aria-label="Consumo por mes en litros"><line x1="35" y1="163" x2="485" y2="163" stroke="#e5edef"/>${elementos.map((e,i) => {
    const altura = e.consumo.litros === null ? 0 : e.consumo.litros / max * 120;
    const x = 45 + i * ancho;
    return `<rect class="${i === elementos.length-1 ? 'barra-actual' : 'barra-anterior'}" x="${x+ancho*.2}" y="${163-altura}" width="${ancho*.55}" height="${altura}" rx="5"/><text x="${x+ancho*.47}" y="${151-altura}" text-anchor="middle">${escapar(e.consumo.litros === null ? 'Sin datos' : numero(e.consumo.litros))}</text><text x="${x+ancho*.47}" y="188" text-anchor="middle">${escapar(e.periodo)}</text>`;
  }).join('')}</svg>`;
}
function bloqueAvisos(lista) {
  return lista.length ? lista.map(n => `<div class="aviso ${n.leida ? 'leida' : ''}"><span class="punto" aria-hidden="true"></span><div><h3>${escapar(n.titulo)}</h3><p>${escapar(n.mensaje)}</p><small>${escapar(fecha(n.fecha))} · ${n.leida ? 'Leída' : 'Pendiente'}</small></div>${n.leida ? '' : `<button class="boton" data-leer="${escapar(n.id)}">Marcar leída</button>`}</div>`).join('') : '<p class="vacio">No tienes avisos disponibles.</p>';
}
function tablaHistorial(elementos) {
  return `<div class="tabla-scroll"><table><thead><tr><th>Periodo</th><th>Consumo</th><th>Costo estimado</th><th>Cobertura</th><th>Fechas utilizadas</th></tr></thead><tbody>${elementos.map(e => `<tr><td class="resaltado">${escapar(mes(e.periodo))}</td><td>${escapar(numero(e.consumo.litros))} L</td><td>${escapar(soles(e.costo?.totalCentimos))}</td><td>${escapar(e.consumo.estado)} · ${e.dias} días en el mes</td><td>${escapar(fecha(e.consumo.desde))}<br>${escapar(fecha(e.consumo.hasta))}</td></tr>`).join('')}</tbody></table></div>`;
}
function renderizar() {
  const nombres = { resumen: ['RESUMEN','Tu consumo de agua'], historial: ['HISTORIAL','Tu consumo, mes a mes'], recibos: ['RECIBOS','Recibos de tu suministro'], notificaciones: ['AVISOS','Tus notificaciones'], admin: ['ADMINISTRACIÓN','Supervisión de Nivora'] };
  $('nombre-seccion').textContent = nombres[vista][0]; $('titulo-vista').textContent = nombres[vista][1];
  for (const boton of document.querySelectorAll('[data-vista]')) boton.classList.toggle('nav-activo', boton.dataset.vista === vista);
  if (vista === 'admin') return renderAdmin();
  if (!datos) return;
  const a = datos.actual;
  const costo = a.costo;
  const notificacionesPendientes = avisos.filter(n => !n.leida).length;
  $('contador-avisos').textContent = notificacionesPendientes || '';
  const parcial = a.consumo.estado !== 'completo';
  const banner = `<div class="banner"><div><h2>${escapar(datos.suministro.descripcion)} · ${escapar(mes(a.periodo))}</h2><p>Última lectura: ${escapar(fecha(datos.ultimaLectura))} · Hora de Lima · Datos ficticios</p></div><span class="estado ${datos.integracion.estado === 'error' ? 'fallo' : ''}">${datos.integracion.estado === 'error' ? 'API sin conexión · datos conservados' : 'Fuente simulada'}</span></div>`;
  if (vista === 'resumen') $('contenido-vista').innerHTML = `${banner}${parcial ? `<p class="solo-lectura">Cobertura: ${escapar(a.consumo.estado)}. ${escapar(a.consumo.motivo)} Las estimaciones usan únicamente el consumo disponible.</p>` : ''}
    <div class="metricas"><article class="tarjeta metrica"><div class="nombre-metrica">Consumo acumulado<span class="icono-metrica">◒</span></div><div class="numero">${escapar(numero(a.consumo.litros))} <small>L</small></div><p>${escapar(numero(a.consumo.metrosCubicos))} m³ · Hasta la última lectura disponible</p></article>
    <article class="tarjeta metrica"><div class="nombre-metrica">Costo hasta el momento<span class="icono-metrica">S/</span></div><div class="numero">${escapar(soles(costo?.totalCentimos))}</div><p>Estimación con tarifa ficticia y cargo fijo mensual</p></article>
    <article class="tarjeta metrica destacada"><div class="nombre-metrica">Proyección a fin de mes<span class="icono-metrica">↗</span></div><div class="numero">${escapar(soles(a.proyeccion?.costo?.totalCentimos))}</div><p>${a.proyeccion ? `${escapar(numero(a.proyeccion.litros))} L si mantienes este ritmo` : 'Se necesitan datos completos desde el inicio del mes'}</p></article></div>
    <div class="fila-panel"><article class="tarjeta"><div class="titulo-tarjeta"><h2>Evolución del consumo</h2><p>Litros por mes</p></div>${grafico([...datos.historial,a])}<div class="leyenda"><span>Meses anteriores</span><span>Mes actual hasta la última lectura</span></div><p class="nota">${escapar(datos.comparacion?.nota || 'No hay datos suficientes para comparar los periodos.')}</p>${datos.comparacion ? `<p class="nota">Diferencia respecto al mes anterior: <b>${escapar(numero(datos.comparacion.diferenciaLitros))} L</b> · ${escapar(soles(datos.comparacion.diferenciaCostoCentimos))} en costo estimado.</p>` : ''}</article>
    <article class="tarjeta"><h2>¿Cómo se estima el costo?</h2><p>Tarifa de demostración · importes en soles</p>${costo ? Object.entries({ Agua:costo.conceptos.agua, Saneamiento:costo.conceptos.saneamiento, 'Cargo fijo mensual':costo.conceptos.cargoFijo, Impuesto:costo.conceptos.impuesto }).map(([k,v]) => `<div class="detalle"><span>${escapar(k)}</span><b>${escapar(soles(v))}</b></div>`).join('') + `<div class="detalle total"><span>Total estimado</span><b>${escapar(soles(costo.totalCentimos))}</b></div>` : '<p class="vacio">No hay consumo o tarifa compatible para calcular.</p>'}<p class="nota">${escapar(a.tarifa?.id)}. ${a.tarifa ? a.tarifa.tramos.map(t => `${escapar(soles(t.centimosPorM3))} por m³ ${t.hastaLitros === null ? 'en el tramo restante' : `hasta ${escapar(numero(t.hastaLitros))} L acumulados`}`).join('; ') + `. Saneamiento ${a.tarifa.saneamientoPorcentaje} %, cargo fijo ${escapar(soles(a.tarifa.fijoCentimos))} e impuesto ${a.tarifa.impuestoPorcentaje} %. Valores ficticios.` : 'Tarifa no disponible.'}</p></article></div>
    <article class="tarjeta"><div class="titulo-tarjeta"><h2>Avisos de tu suministro</h2><button class="enlace" data-cambiar="notificaciones">Ver todos →</button></div>${bloqueAvisos(avisos.slice(0,2))}</article>`;
  else if (vista === 'historial') $('contenido-vista').innerHTML = `${banner}<article class="tarjeta"><h2>Historial de consumo y estimaciones</h2>${grafico([...datos.historial,a])}${tablaHistorial([...datos.historial,a])}<p class="nota">Los intervalos sin lecturas se indican en la cobertura. La comparación del mes actual utiliza un periodo todavía en curso.</p></article>`;
  else if (vista === 'recibos') $('contenido-vista').innerHTML = `${banner}<article class="tarjeta"><h2>Recibos disponibles</h2><p>Proporcionados por la API simulada de la empresa. Los importes definitivos de estos recibos también son ficticios.</p>${recibos.length ? `<div class="tabla-scroll"><table><thead><tr><th>Mes</th><th>Importe definitivo</th><th>Disponible desde</th><th>Fecha límite</th></tr></thead><tbody>${recibos.map(r => `<tr><td class="resaltado">${escapar(mes(r.periodo))}</td><td>${escapar(soles(r.totalCentimos))}</td><td>${escapar(fecha(r.disponibleDesde))}</td><td>${escapar(fecha(r.fechaLimite))}</td></tr>`).join('')}</tbody></table></div>` : '<p class="vacio">Aún no hay recibos disponibles.</p>'}<p class="nota">Plazo de prueba: una semana desde la disponibilidad. El pago se realiza fuera de Nivora.</p></article>`;
  else $('contenido-vista').innerHTML = `${banner}<article class="tarjeta"><h2>Notificaciones al dispositivo</h2><p>${pushConfig.habilitado ? 'Activa los avisos de este suministro en este navegador. La entrega usa Web Push y puede funcionar con la aplicación cerrada.' : 'El adaptador Web Push está incluido. Falta configurar las claves VAPID del servidor para habilitarlo.'}</p><div class="acciones"><button class="boton primario" id="activar-push" ${!pushConfig.habilitado ? 'disabled' : ''}>Activar en este dispositivo</button><button class="boton" id="desactivar-push">Desactivar en este dispositivo</button></div><p class="nota">Requiere permiso y navegador compatible. Cerrar sesión conserva la asociación; desactívala aquí si no deseas recibir avisos. La entrega externa debe comprobarse en tu dispositivo.</p></article><article class="tarjeta"><h2>Avisos registrados</h2>${bloqueAvisos(avisos)}</article>`;
}
function renderAdmin() {
  const e = datos;
  $('contenido-vista').innerHTML = `<div class="banner"><div><h2>Estado de la integración</h2><p>${escapar(e.integracion.mensaje)} Última actualización: ${escapar(fecha(e.integracion.ultimaActualizacion))}</p></div><span class="estado ${e.integracion.estado === 'error' ? 'fallo' : ''}">${escapar(e.integracion.estado)}</span></div>
    <div class="metricas"><article class="tarjeta metrica"><div class="nombre-metrica">Suministros de prueba</div><div class="numero">${e.suministros.length}</div><p>${e.lecturas} lecturas aceptadas</p></article><article class="tarjeta metrica"><div class="nombre-metrica">Accesos registrados</div><div class="numero">${e.totalAccesos}</div><p>Se registra el DNI utilizado y la identidad devuelta</p></article><article class="tarjeta metrica destacada"><div class="nombre-metrica">Notificaciones almacenadas</div><div class="numero">${e.notificaciones}</div><p>${e.dispositivosActivos} dispositivos asociados activos</p></article></div>
    <div class="cuadricula-admin"><article class="tarjeta"><h2>Escenarios de la empresa simulada</h2><p>Los controles modifican la fuente externa local; después se intenta incorporar sus datos.</p><div class="acciones">${[['consumo-alto','Consumo elevado'],['duplicado','Lectura duplicada'],['lectura-invalida','Lectura inválida'],['recibo','Publicar recibo de septiembre'],['caida-agua','Interrumpir API de agua'],['restaurar-agua','Restaurar API de agua'],['caida-identidad','Interrumpir identidad'],['restaurar-identidad','Restaurar identidad']].map(([id,etiqueta]) => `<button class="boton" data-escenario="${id}">${etiqueta}</button>`).join('')}</div><p class="nota">13100390: normal; 13100391: consumo elevado; 13100392: lectura de inicio de octubre faltante.</p></article>
    <article class="tarjeta"><h2>Supervisión</h2><div class="acciones"><button class="boton primario" id="actualizar">Reintentar actualización</button><button class="boton" id="alternar-alertas">${e.alertasHabilitadas ? 'Desactivar' : 'Activar'} evaluación de alertas</button></div><h3 class="titulo-menor">Incidencias de integración</h3>${e.incidencias.length ? e.incidencias.slice(0,8).map(i => `<div class="aviso"><div><h3>${escapar(i.estado)} · ${escapar(i.suministro || 'General')}</h3><p>${escapar(i.motivo)}</p><small>${escapar(fecha(i.ultimaFecha))} · ${i.repeticiones} observaciones</small></div></div>`).join('') : '<p class="vacio">Sin incidencias registradas.</p>'}<p class="nota">Entregas aceptadas por el proveedor: ${e.entregas.filter(x=>x.estado==='aceptada').length}. Pendientes: ${e.entregas.filter(x=>x.estado==='pendiente').length}. No confundir aceptación con recepción verificada.</p></article>
    <article class="tarjeta"><h2>Últimos accesos de consulta</h2><p>Este registro identifica el documento utilizado; la consulta de DNI no demuestra quién lo escribió físicamente.</p><div class="tabla-scroll"><table><thead><tr><th>Fecha</th><th>Suministro</th><th>DNI utilizado</th><th>Identidad ficticia consultada</th></tr></thead><tbody>${e.accesos.slice(0,15).map(a => `<tr><td>${escapar(fecha(a.fecha))}</td><td>${escapar(a.suministro)}</td><td>${escapar(a.dniUtilizado)}</td><td>${escapar(a.identidadConsultada.nombre)}</td></tr>`).join('')}</tbody></table></div><p class="nota">El ejemplo muestra supervisión y simulación. La edición administrativa de periodos y versiones tarifarias queda pendiente para las siguientes etapas.</p></article></div>`;
}
async function refrescar() {
  if (!sesion || refrescando) return;
  refrescando = true;
  try {
    if (sesion.rol === 'admin') datos = await api('/api/admin/estado');
    else [datos,recibos,avisos] = await Promise.all([api('/api/panel'),api('/api/recibos'),api('/api/notificaciones')]);
    renderizar();
  } catch(error) { if (error.estado === 401) mostrarAcceso(); else mensaje(error.message, true); }
  finally { refrescando = false; }
}
function clavePublica(base64) {
  const datos = atob(base64.replace(/-/g,'+').replace(/_/g,'/').padEnd(Math.ceil(base64.length/4)*4,'='));
  return Uint8Array.from(datos, c=>c.charCodeAt(0));
}
async function activarPush() {
  if (!('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window) || !window.isSecureContext) return mensaje('Este navegador o conexión no permite Web Push. Use localhost o HTTPS y un navegador compatible.',true);
  if (!pushConfig.habilitado) return mensaje('Configure VAPID en el servidor antes de activar los avisos.',true);
  // Solicitar el permiso dentro del gesto de la persona.
  const permiso = await Notification.requestPermission();
  if (permiso !== 'granted') return mensaje('El dispositivo no concedió permiso de notificación.',true);
  try {
    await navigator.serviceWorker.register('/sw.js');
    const registro = await navigator.serviceWorker.ready;
    const suscripcion = await registro.pushManager.getSubscription() || await registro.pushManager.subscribe({ userVisibleOnly:true, applicationServerKey:clavePublica(pushConfig.publicKey) });
    const d = await api('/api/dispositivos',{ method:'POST', body:JSON.stringify(suscripcion.toJSON()) });
    localStorage.setItem(`nivora_dispositivo:${sesion.codigo}`, d.id);
    mensaje('Dispositivo asociado. Recibirá nuevos avisos de este suministro mediante Web Push.');
  } catch(error) { mensaje(`No se pudo activar: ${error.message}`,true); }
}
async function desactivarPush() {
  try {
    const id = localStorage.getItem(`nivora_dispositivo:${sesion.codigo}`);
    if (!id) return mensaje('No hay una asociación guardada para este suministro en este navegador.');
    await api(`/api/dispositivos/${encodeURIComponent(id)}`,{ method:'DELETE', body:'{}' });
    if ('serviceWorker' in navigator) {
      const registro = await navigator.serviceWorker.getRegistration();
      const suscripcion = await registro?.pushManager.getSubscription(); await suscripcion?.unsubscribe();
    }
    localStorage.removeItem(`nivora_dispositivo:${sesion.codigo}`); mensaje('Avisos desactivados en este dispositivo.');
  } catch(error) { mensaje(error.message,true); }
}
$('contenido-vista').addEventListener('click', async event => {
  const boton = event.target.closest('button'); if (!boton) return;
  if (boton.id === 'activar-push') return activarPush();
  if (boton.id === 'desactivar-push') return desactivarPush();
  if (boton.dataset.cambiar) { vista = boton.dataset.cambiar; return renderizar(); }
  boton.disabled = true;
  try {
    if (boton.dataset.leer) await api(`/api/notificaciones/${encodeURIComponent(boton.dataset.leer)}/leida`, { method:'POST',body:'{}' });
    else if (boton.dataset.escenario) { await api('/api/admin/escenario',{method:'POST',body:JSON.stringify({nombre:boton.dataset.escenario})}); mensaje('Escenario aplicado. Consulta el resultado o revisa la integración.'); }
    else if (boton.id === 'actualizar') { const r = await api('/api/admin/actualizar',{method:'POST',body:'{}'}); mensaje(`Actualización: ${r.estado}. Lecturas incorporadas: ${r.incorporadas}.`,r.estado==='error'); }
    else if (boton.id === 'alternar-alertas') await api('/api/admin/alertas',{method:'POST',body:JSON.stringify({habilitadas:!datos.alertasHabilitadas})});
    await refrescar();
  } catch(error) { mensaje(error.message,true); }
  finally { boton.disabled = false; }
});
setInterval(() => { if (!document.hidden) refrescar(); }, 10000);
api('/api/sesion').then(entrar).catch(() => mostrarAcceso());
