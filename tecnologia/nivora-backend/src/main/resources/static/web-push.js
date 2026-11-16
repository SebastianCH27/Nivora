'use strict';

const base = '/api/v1/acceso/consulta';
const formulario = document.querySelector('#notificaciones');
const estado = document.querySelector('#estado');
const confirmacion = document.querySelector('#confirmacion');
const confirmar = document.querySelector('#confirmar');
const cancelar = document.querySelector('#cancelar');
let preparada;
const claveDispositivo = 'nivora.push.dispositivo';
const claveSuministro = 'nivora.push.suministro';

async function solicitar(ruta, metodo, token, datos) {
    const headers = {};
    if (token) headers.Authorization = 'Bearer ' + token;
    if (datos) headers['Content-Type'] = 'application/json';
    const respuesta = await fetch(base + ruta, {
        method: metodo, headers, body: datos ? JSON.stringify(datos) : undefined,
        cache: 'no-store', signal: AbortSignal.timeout(30000)
    });
    if (!respuesta.ok) {
        const error = await respuesta.json().catch(() => ({}));
        throw new Error(error.detail || 'La solicitud devolvió HTTP ' + respuesta.status + '.');
    }
    return respuesta.status === 204 ? null : respuesta.json();
}

function claveBytes(clave) {
    const base64 = clave.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(clave.length / 4) * 4, '=');
    return Uint8Array.from(atob(base64), caracter => caracter.charCodeAt(0));
}

async function preparar(token, ruta, codigo, permiso) {
    if (permiso !== 'granted') {
        await solicitar(ruta, 'PUT', token, {
            permiso: permiso === 'denied' ? 'DENEGADO' : 'PENDIENTE', habilitadas: false
        });
        throw new Error('Concede el permiso de notificaciones en el navegador para activar los avisos.');
    }
    const { clavePublica } = await solicitar('/notificaciones/web-push/clave-publica', 'GET', token);
    await navigator.serviceWorker.register('/web-push-sw.js');
    const registro = await navigator.serviceWorker.ready;
    let suscripcion = await registro.pushManager.getSubscription();
    const clave = claveBytes(clavePublica);
    if (suscripcion) {
        const anterior = new Uint8Array(suscripcion.options.applicationServerKey);
        if (anterior.length !== clave.length || !anterior.every((byte, indice) => byte === clave[indice])) {
            await suscripcion.unsubscribe();
            suscripcion = null;
        }
    }
    preparada = { token, ruta, codigo, suscripcion, registro, clave };
    formulario.hidden = true;
    confirmacion.hidden = false;
    estado.textContent = 'Confirma la activación en este navegador.';
    confirmar.focus();
}

function cerrarSesion(token) {
    return solicitar('/sesion', 'DELETE', token).catch(() => {});
}

async function cerrarConfirmacion() {
    await cerrarSesion(preparada.token);
    preparada = null;
    confirmacion.hidden = true;
    formulario.hidden = false;
    confirmar.disabled = false;
    cancelar.disabled = false;
    formulario.elements.codigo.focus();
}

confirmar.addEventListener('click', async () => {
    confirmar.disabled = true;
    cancelar.disabled = true;
    const { registro, clave, token, ruta, codigo } = preparada;
    try {
        const suscripcion = preparada.suscripcion || await registro.pushManager.subscribe({
            userVisibleOnly: true, applicationServerKey: clave
        });
        const { endpoint, keys } = suscripcion.toJSON();
        await solicitar(ruta, 'PUT', token, { permiso: 'CONCEDIDO', habilitadas: true });
        await solicitar(ruta + '/suscripcion', 'PUT', token, { endpoint, keys });
        localStorage.setItem(claveSuministro, codigo);
        estado.textContent = 'Avisos activados para este navegador.';
    } catch (error) {
        estado.textContent = error.message;
    } finally {
        await cerrarConfirmacion();
    }
});

cancelar.addEventListener('click', async () => {
    confirmar.disabled = true;
    cancelar.disabled = true;
    estado.textContent = 'Activación cancelada.';
    await cerrarConfirmacion();
});

async function desactivar(token, ruta, codigo) {
    const permiso = { granted: 'CONCEDIDO', denied: 'DENEGADO', default: 'PENDIENTE' }[Notification.permission];
    await solicitar(ruta, 'PUT', token, { permiso, habilitadas: false });
    await solicitar(ruta + '/suscripcion', 'DELETE', token);
    if (localStorage.getItem(claveSuministro) === codigo) {
        const registro = await navigator.serviceWorker.getRegistration();
        const suscripcion = await registro?.pushManager.getSubscription();
        if (suscripcion) await suscripcion.unsubscribe();
        localStorage.removeItem(claveSuministro);
    }
    estado.textContent = 'Avisos desactivados para este suministro.';
}

formulario.addEventListener('submit', async evento => {
    evento.preventDefault();
    const activarAvisos = evento.submitter?.value !== 'desactivar';
    const codigo = formulario.elements.codigo.value;
    const dni = formulario.elements.dni.value;
    const botones = formulario.querySelectorAll('button');
    botones.forEach(boton => { boton.disabled = true; });
    estado.textContent = 'Guardando la configuración…';
    let token;
    try {
        if (!window.isSecureContext || !('serviceWorker' in navigator) || !('PushManager' in window)
                || !('Notification' in window)) {
            throw new Error('Abre esta página en un navegador compatible mediante localhost o HTTPS.');
        }
        const asociado = localStorage.getItem(claveSuministro);
        if (activarAvisos && asociado && asociado !== codigo) {
            throw new Error('Desactiva primero los avisos del suministro asociado a este navegador.');
        }
        let permiso = Notification.permission;
        if (activarAvisos && permiso === 'default') permiso = await Notification.requestPermission();
        let id = localStorage.getItem(claveDispositivo);
        if (!id) {
            id = crypto.randomUUID();
            localStorage.setItem(claveDispositivo, id);
        }
        ({ token } = await solicitar('/sesiones', 'POST', null, { codigoSuministro: codigo, dni }));
        const ruta = '/notificaciones/dispositivos/' + id;
        if (activarAvisos) await preparar(token, ruta, codigo, permiso);
        else await desactivar(token, ruta, codigo);
    } catch (error) {
        estado.textContent = error.message;
    } finally {
        formulario.elements.dni.value = '';
        if (token && !preparada) await cerrarSesion(token);
        botones.forEach(boton => { boton.disabled = false; });
    }
});
