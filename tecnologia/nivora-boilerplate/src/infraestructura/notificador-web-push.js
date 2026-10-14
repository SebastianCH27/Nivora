import webpush from 'web-push';
import { EmisorNotificaciones } from '../dominio/contratos/puertos.js';
import { ErrorNivora } from '../dominio/errores.js';

export class NotificadorWebPush extends EmisorNotificaciones {
  constructor({ publicKey = '', privateKey = '', subject = '' } = {}) {
    super(); this.publicKey = publicKey;
    this.habilitado = Boolean(publicKey && privateKey && subject);
    this.opciones = this.habilitado ? { vapidDetails: { subject, publicKey, privateKey }, TTL: 86400, timeout: 5000 } : null;
  }
  validarSuscripcion(suscripcion) {
    try {
      const url = new URL(suscripcion.endpoint);
      const proveedores = ['fcm.googleapis.com', 'updates.push.services.mozilla.com', 'push.services.mozilla.com', 'web.push.apple.com'];
      const admitido = proveedores.some(host => url.hostname === host || url.hostname.endsWith(`.${host}`));
      if (url.protocol !== 'https:' || url.username || url.password || (url.port && url.port !== '443') || !admitido || Buffer.from(suscripcion.keys.p256dh, 'base64url').length !== 65 || Buffer.from(suscripcion.keys.auth, 'base64url').length !== 16) throw new Error();
    } catch { throw new ErrorNivora('DATOS_INVALIDOS', 'La suscripción Web Push no es válida o su proveedor no está admitido en esta base.'); }
  }
  enviar(dispositivo, aviso) {
    this.validarSuscripcion(dispositivo.suscripcion);
    // Un 2xx indica aceptación por el proveedor, no prueba recepción en pantalla.
    return webpush.sendNotification(dispositivo.suscripcion, JSON.stringify({ titulo: aviso.titulo, mensaje: aviso.mensaje, id: aviso.id, url: '/' }), this.opciones);
  }
}
