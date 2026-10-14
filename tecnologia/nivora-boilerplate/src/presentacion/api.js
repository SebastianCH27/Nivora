import express from 'express';
import { fileURLToPath } from 'node:url';

export function crearApi(s, config) {
  const app = express();
  app.disable('x-powered-by');
  app.use((req,res,next) => {
    res.set({ 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'same-origin', 'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; worker-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'" });
    if (req.path.startsWith('/api/')) res.set('Cache-Control', 'no-store');
    if (['POST','PUT','DELETE','PATCH'].includes(req.method) && req.headers.origin) {
      const origen = config.origenPublico || `${req.protocol}://${req.get('host')}`;
      if (req.headers.origin !== origen) return res.status(403).json({ codigo: 'PROHIBIDO', error: 'Origen de solicitud no permitido.' });
    }
    next();
  });
  app.use(express.json({ limit: '16kb' }));
  const token = req => {
    const cookie = (req.headers.cookie || '').split(';').map(c => c.trim()).find(c => c.startsWith('nivora_sesion='));
    return cookie?.slice('nivora_sesion='.length) ?? '';
  };
  const cookie = (res, valor, maxAge = 8 * 3600000) => res.cookie('nivora_sesion', valor, { httpOnly: true, sameSite: 'strict', secure: config.cookieSegura, path: '/', maxAge });
  const proteger = rol => (req,res,next) => {
    req.sesion = s.sesiones.consultar(token(req));
    if (!req.sesion) return res.status(401).json({ codigo: 'SESION_REQUERIDA', error: 'Inicie sesión para continuar.' });
    if (rol && req.sesion.rol !== rol) return res.status(403).json({ codigo: 'PROHIBIDO', error: 'Su sesión no tiene permisos para esta operación.' });
    next();
  };
  const consulta = proteger('consulta');
  const admin = proteger('admin');
  app.get('/api/salud', (req,res) => res.json({ aplicacion: 'Nivora', version: '0.1.0', entorno: 'demostracion' }));
  app.post('/api/acceso', async (req,res) => {
    const resultado = await s.acceso.ejecutar(req.body?.codigo, req.body?.dni);
    s.sesiones.cerrar(token(req)); cookie(res, resultado.token);
    res.json(resultado.usuario);
  });
  app.post('/api/admin/acceso', (req,res) => {
    const resultado = s.acceso.administrador(req.body?.usuario, req.body?.clave);
    s.sesiones.cerrar(token(req)); cookie(res, resultado.token);
    res.json(resultado.usuario);
  });
  app.get('/api/sesion', proteger(), (req,res) => {
    const { nombre, codigo, rol } = req.sesion; res.json({ nombre, codigo, rol });
  });
  app.post('/api/salir', proteger(), (req,res) => { s.sesiones.cerrar(token(req)); cookie(res, '', 0); res.json({ cerrada: true }); });
  app.get('/api/panel', consulta, (req,res) => res.json(s.panel.ejecutar(req.sesion.codigo)));
  app.get('/api/consumo', consulta, (req,res) => res.json(req.query.periodo ? s.panel.resumen(req.sesion.codigo, req.query.periodo) : s.panel.ejecutar(req.sesion.codigo).actual));
  app.get('/api/costos', consulta, (req,res) => res.json(s.panel.ejecutar(req.sesion.codigo).actual.costo));
  app.get('/api/proyeccion', consulta, (req,res) => res.json(s.panel.ejecutar(req.sesion.codigo).actual.proyeccion));
  app.get('/api/historial', consulta, (req,res) => res.json(s.panel.ejecutar(req.sesion.codigo).historial));
  app.get('/api/recibos', consulta, (req,res) => res.json(s.panel.recibos(req.sesion.codigo)));
  app.get('/api/notificaciones', consulta, (req,res) => res.json(s.notificaciones.consultar(req.sesion.codigo)));
  app.post('/api/notificaciones/:id/leida', consulta, (req,res) => { s.notificaciones.leer(req.sesion.codigo, req.params.id); res.json({ leida: true }); });
  app.get('/api/push/configuracion', consulta, (req,res) => res.json({ habilitado: s.emisor.habilitado, publicKey: s.emisor.publicKey }));
  app.post('/api/dispositivos', consulta, (req,res) => res.json(s.notificaciones.asociar(req.sesion.codigo, req.sesion.dni, req.body)));
  app.delete('/api/dispositivos/:id', consulta, (req,res) => { s.notificaciones.desactivar(req.sesion.codigo, req.sesion.dni, req.params.id); res.json({ activo: false }); });
  app.get('/api/admin/estado', admin, (req,res) => res.json(s.administracion.consultarEstado()));
  app.post('/api/admin/actualizar', admin, async (req,res) => res.json(await s.actualizacion.ejecutar()));
  app.post('/api/admin/alertas', admin, (req,res) => res.json(s.administracion.configurarAlertas(req.body?.habilitadas)));
  app.post('/api/admin/escenario', admin, async (req,res) => {
    res.json(await s.administracion.escenario(req.body?.nombre));
  });
  const web = fileURLToPath(new URL('./web/', import.meta.url));
  app.use(express.static(web, { maxAge: 0 }));
  app.use((req,res) => res.status(404).json({ codigo: 'NO_ENCONTRADO', error: 'Recurso no encontrado.' }));
  app.use((err,req,res,next) => {
    const codigos = { DATOS_INVALIDOS:400, LECTURA_INVALIDA:400, ACCESO_INVALIDO:401, NO_ENCONTRADO:404, DATOS_NO_DISPONIBLES:503, SERVICIO_NO_DISPONIBLE:503 };
    const estado = codigos[err.codigo] || (err.type === 'entity.parse.failed' ? 400 : err.type === 'entity.too.large' ? 413 : 500);
    if (estado === 500) console.error('Error de API:', err.message);
    res.status(estado).json({ codigo: err.codigo || 'SOLICITUD_FALLIDA', error: estado === 500 ? 'No se pudo completar la operación.' : err.codigo ? err.message : 'Solicitud JSON inválida o demasiado grande.' });
  });
  return app;
}
