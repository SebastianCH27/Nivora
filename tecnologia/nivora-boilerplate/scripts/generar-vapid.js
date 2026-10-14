import webpush from 'web-push';
const claves = webpush.generateVAPIDKeys();
console.log('Copiar las siguientes líneas en .env; conservarlas y no subirlas a Git:');
console.log(`VAPID_PUBLIC_KEY=${claves.publicKey}`);
console.log(`VAPID_PRIVATE_KEY=${claves.privateKey}`);
console.log('VAPID_SUBJECT=mailto:tu-correo@example.com');
