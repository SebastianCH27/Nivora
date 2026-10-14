import express from 'express';
import { crearDatosSimulados, PERSONAS } from './datos.js';

export function crearSimulador(secreto) {
  const app = express();
  app.disable('x-powered-by'); app.use(express.json({ limit: '16kb' }));
  const datos = crearDatosSimulados();
  let aguaDisponible = true;
  let identidadDisponible = true;
  let secuencia = 0;
  app.use((req, res, next) => req.headers.authorization === `Bearer ${secreto}` ? next() : res.status(401).json({ error: 'API local de simulación protegida.' }));
  app.use('/agua', (req, res, next) => aguaDisponible ? next() : res.status(503).json({ error: 'Caída simulada de la empresa.' }));
  app.get('/agua/datos', (req, res) => res.json(datos));
  app.get('/agua/suministros/:codigo', (req, res) => {
    const suministro = datos.suministros.find(s => s.codigo === req.params.codigo);
    res.status(suministro ? 200 : 404).json(suministro ?? { error: 'Suministro inexistente.' });
  });
  app.get('/identidad/:dni', (req, res) => {
    if (!identidadDisponible) return res.status(503).json({ error: 'Caída simulada de identidad.' });
    const persona = PERSONAS.find(p => p.dni === req.params.dni);
    res.status(persona ? 200 : 404).json(persona ? { ...persona, fuente: 'API de identidad ficticia' } : { error: 'DNI inexistente en la simulación.' });
  });
  const nuevaLectura = (incremento = 1, codigo = '13100390') => {
    const ultimo = datos.lecturas.filter(l => l.suministro === codigo && l.tipo === 'acumulada' && Number.isFinite(l.valor)).sort((a,b) => Date.parse(a.fecha) - Date.parse(b.fecha)).at(-1);
    const fecha = new Date(Math.min(Date.parse(datos.fechaFuente) + 60000, Date.parse('2026-10-31T23:59:00-05:00'))).toISOString();
    if (fecha === ultimo.fecha) return;
    datos.fechaFuente = fecha;
    datos.lecturas.push({ ...ultimo, id: `${codigo}-auto-${fecha}`, fecha, valor: ultimo.valor + incremento });
  };
  app.get('/control/estado', (req,res) => res.json({ aguaDisponible, identidadDisponible, fechaFuente: datos.fechaFuente, lecturasFuente: datos.lecturas.length }));
  app.post('/control/escenario', (req,res) => {
    const nombre = req.body?.nombre;
    if (nombre === 'consumo-alto') nuevaLectura(8000);
    else if (nombre === 'duplicado') datos.lecturas.push({ ...datos.lecturas.at(-1) });
    else if (nombre === 'lectura-invalida') datos.lecturas.push({ ...datos.lecturas[0], id: `invalida-${++secuencia}`, valor: -10 });
    else if (nombre === 'recibo') {
      const id = 'REC-DEMO-13100390-2026-09';
      if (!datos.recibos.some(r => r.id === id)) datos.recibos.push({ id, suministro: '13100390', periodo: '2026-09', totalCentimos: 3658, disponibleDesde: datos.fechaFuente, empresa: 'Empresa de agua simulada', simulado: true });
    }
    else if (nombre === 'caida-agua') aguaDisponible = false;
    else if (nombre === 'restaurar-agua') aguaDisponible = true;
    else if (nombre === 'caida-identidad') identidadDisponible = false;
    else if (nombre === 'restaurar-identidad') identidadDisponible = true;
    else return res.status(400).json({ error: 'Escenario desconocido.' });
    res.json({ escenario: nombre, aguaDisponible, identidadDisponible });
  });
  app.use((err,req,res,next) => res.status(400).json({ error: 'Solicitud inválida al simulador.' }));
  return { app, avanzar: nuevaLectura, datos };
}
