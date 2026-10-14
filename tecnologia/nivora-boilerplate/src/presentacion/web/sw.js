// No se almacenan respuestas de API ni datos personales en la caché del navegador.
self.addEventListener('install', event => event.waitUntil(self.skipWaiting()));
self.addEventListener('activate', event => event.waitUntil(self.clients.claim()));
self.addEventListener('push', event => {
  let aviso;
  try { aviso = event.data.json(); } catch { aviso = { titulo: 'Nivora', mensaje: 'Tienes un nuevo aviso disponible.' }; }
  event.waitUntil(self.registration.showNotification(aviso.titulo || 'Nivora', { body: aviso.mensaje, icon: '/icono.svg', tag: aviso.id, data: { url: '/' } }));
});
self.addEventListener('notificationclick', event => {
  event.notification.close();
  event.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(ventanas => {
    const ventana = ventanas.find(v => new URL(v.url).origin === self.location.origin);
    return ventana ? ventana.focus() : self.clients.openWindow('/');
  }));
});
