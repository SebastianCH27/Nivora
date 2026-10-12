import { randomUUID } from 'node:crypto';
import { IniciarSesion } from './aplicacion/iniciar-sesion.js';
import { ConsultarPanel } from './aplicacion/consultar-panel.js';
import { ActualizarDatos } from './aplicacion/actualizar-datos.js';
import { GestionarNotificaciones } from './aplicacion/notificaciones.js';
import { GestionarAdministracion } from './aplicacion/administracion.js';
import { FuenteAguaHttp, FuenteIdentidadHttp } from './infraestructura/fuentes-http.js';
import { SesionesMemoria } from './infraestructura/sesiones-memoria.js';
import { NotificadorWebPush } from './infraestructura/notificador-web-push.js';
import { crearApi } from './presentacion/api.js';

// RAÍZ DE COMPOSICIÓN. Las implementaciones concretas se eligen aquí.
export function componer({ repositorio, baseSimulacion, secreto, config, reloj = () => new Date().toISOString(), identificador = randomUUID }) {
  const agua = new FuenteAguaHttp(baseSimulacion, secreto);
  const identidad = new FuenteIdentidadHttp(baseSimulacion, secreto);
  const sesiones = new SesionesMemoria(config.adminUsuario, config.adminClave);
  const emisor = new NotificadorWebPush(config.vapid);
  const panel = new ConsultarPanel({ repositorio, reloj });
  const acceso = new IniciarSesion({ agua, identidad, repositorio, sesiones, reloj });
  const actualizacion = new ActualizarDatos({ agua, repositorio, panel, reloj, identificador });
  const notificaciones = new GestionarNotificaciones({ repositorio, emisor, reloj, identificador });
  const administracion = new GestionarAdministracion({ repositorio, agua, actualizacion });
  const servicios = { repositorio, agua, sesiones, emisor, panel, acceso, actualizacion, notificaciones, administracion };
  return { ...servicios, app: crearApi(servicios, config) };
}
