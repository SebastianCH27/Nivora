'use strict';

self.addEventListener('push', evento => {
    if (!evento.data) return;
    const aviso = evento.data.json();
    evento.waitUntil(self.registration.showNotification(aviso.titulo, {
        body: aviso.mensaje, tag: aviso.id
    }));
});

self.addEventListener('notificationclick', evento => {
    evento.notification.close();
    evento.waitUntil(clients.openWindow('/web-push.html'));
});
