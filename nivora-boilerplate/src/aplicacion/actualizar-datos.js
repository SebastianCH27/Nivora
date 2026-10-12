import { validarLectura, claveLectura } from '../dominio/lecturas.js';
import { calcularCosto, superaMesAnterior } from '../dominio/calculos.js';
import { fechaLimiteRecibo } from '../dominio/periodos.js';

export class ActualizarDatos {
  constructor({ agua, repositorio, panel, reloj, identificador }) {
    Object.assign(this, { agua, repositorio, panel, reloj, identificador });
    this.enCurso = null;
  }
  ejecutar() {
    // Un único ciclo de incorporación activo; la consulta no espera a este ciclo.
    if (!this.enCurso) this.enCurso = this.actualizar().finally(() => { this.enCurso = null; });
    return this.enCurso;
  }
  incidencia(estado, motivo, suministro = null) {
    const existente = estado.incidencias.find(i => i.motivo === motivo && i.suministro === suministro && i.estado === 'pendiente');
    if (existente) { existente.repeticiones++; existente.ultimaFecha = this.reloj(); }
    else estado.incidencias.push({ id: this.identificador(), fecha: this.reloj(), ultimaFecha: this.reloj(), suministro, motivo, estado: 'pendiente', repeticiones: 1 });
  }
  aviso(estado, clave, suministro, titulo, mensaje, tipo) {
    if (!estado.notificaciones.some(n => n.clave === clave)) estado.notificaciones.push({ id: this.identificador(), clave, suministro, titulo, mensaje, tipo, fecha: this.reloj(), leida: false });
  }
  async actualizar() {
    let datos;
    try {
      datos = await this.agua.obtenerDatos();
      if (!Array.isArray(datos.lecturas) || !Array.isArray(datos.suministros) || !Array.isArray(datos.tarifas) || !Array.isArray(datos.recibos) || !Number.isFinite(Date.parse(datos.fechaFuente))) throw new Error('Respuesta de integración incompleta.');
    } catch (error) {
      this.repositorio.modificar(estado => {
        estado.integracion.estado = 'error';
        estado.integracion.ultimoIntento = this.reloj();
        estado.integracion.mensaje = 'La API de la empresa no responde. Se conservan los datos almacenados.';
        this.incidencia(estado, 'Fallo de comunicación con la empresa de agua.');
      });
      return { estado: 'error', incorporadas: 0 };
    }
    let incorporadas = 0;
    this.repositorio.modificar(estado => {
      for (const suministro of datos.suministros) {
        if (!/^\d{8}$/.test(suministro.codigo) || !suministro.medidor) { this.incidencia(estado, 'Suministro inválido recibido.'); continue; }
        if (!estado.suministros.some(s => s.codigo === suministro.codigo)) estado.suministros.push(suministro);
      }
      for (const tarifa of datos.tarifas) {
        try {
          calcularCosto(0, tarifa);
          if (!tarifa.id || !estado.suministros.some(s => s.codigo === tarifa.suministro) || !(Date.parse(tarifa.vigenteHasta) > Date.parse(tarifa.vigenteDesde))) throw new Error('Tarifa inválida.');
          const anterior = estado.tarifas.find(t => t.id === tarifa.id);
          if (anterior && JSON.stringify(anterior) !== JSON.stringify(tarifa)) throw new Error('Una versión tarifaria existente no puede cambiar sus datos.');
          if (!anterior) estado.tarifas.push(tarifa);
        } catch { this.incidencia(estado, 'Tarifa inválida o incompleta.', tarifa.suministro ?? null); }
      }
      for (const dato of datos.lecturas) {
        try {
          const lectura = validarLectura(dato, estado.suministros.find(s => s.codigo === dato?.suministro));
          if (Date.parse(lectura.fecha) > Date.parse(datos.fechaFuente)) throw new Error('La lectura es posterior a la fecha de corte de la fuente.');
          const existente = estado.lecturas.find(l => claveLectura(l) === claveLectura(lectura));
          const mismosCampos = l => ['suministro','medidor','fecha','inicio','tipo','valorLitros'].every(c => l[c] === lectura[c]);
          if (existente) {
            if (!mismosCampos(existente)) throw new Error('El identificador ya existe con otros valores.');
            continue;
          }
          const mismaMedicion = estado.lecturas.find(l => l.suministro === lectura.suministro && l.medidor === lectura.medidor && l.fecha === lectura.fecha && l.tipo === lectura.tipo && l.inicio === lectura.inicio);
          if (mismaMedicion) {
            if (!mismosCampos(mismaMedicion)) throw new Error('Hay valores incompatibles para la misma medición.');
            continue;
          }
          estado.lecturas.push({ ...lectura, recibidaEn: this.reloj() });
          incorporadas++;
        } catch (error) { this.incidencia(estado, `Lectura rechazada: ${error.message}`, dato?.suministro ?? null); }
      }
      for (const recibo of datos.recibos) {
        if (!recibo.id || !/^\d{4}-(0[1-9]|1[0-2])$/.test(recibo.periodo) || !estado.suministros.some(s => s.codigo === recibo.suministro) || !Number.isSafeInteger(recibo.totalCentimos) || recibo.totalCentimos < 0 || !Number.isFinite(Date.parse(recibo.disponibleDesde))) {
          this.incidencia(estado, 'Recibo inválido recibido.', recibo.suministro ?? null); continue;
        }
        if (estado.recibos.some(r => r.id === recibo.id)) continue;
        const guardado = { ...recibo, fechaLimite: fechaLimiteRecibo(recibo.disponibleDesde), procedencia: 'API simulada de la empresa' };
        estado.recibos.push(guardado);
        this.aviso(estado, `recibo:${recibo.id}`, recibo.suministro, 'Tu recibo está disponible', `El recibo de ${recibo.periodo} está disponible. El plazo es de una semana desde su disponibilidad. Fecha límite: ${guardado.fechaLimite.slice(0,10)}.`, 'recibo');
      }
      estado.integracion = { estado: 'actualizada', fechaFuente: datos.fechaFuente, ultimaActualizacion: this.reloj(), ultimoIntento: this.reloj(), mensaje: 'Datos de la API simulada incorporados. Reloj de demostración.' };
      for (const i of estado.incidencias) if (i.motivo === 'Fallo de comunicación con la empresa de agua.' && i.estado === 'pendiente') i.estado = 'recuperada';
    });
    this.repositorio.modificar(estado => {
      if (!estado.alertasHabilitadas) return;
      for (const suministro of estado.suministros) {
        const panel = this.panel.ejecutar(suministro.codigo);
        if (superaMesAnterior(panel.actual.consumo, panel.anterior.consumo)) this.aviso(estado, `consumo:${suministro.codigo}:${panel.actual.periodo}`, suministro.codigo, 'Has superado el consumo anterior', `El consumo de ${panel.actual.periodo} superó el total de ${panel.anterior.periodo}. Revisa tu consumo de agua.`, 'consumo');
      }
    });
    return { estado: 'actualizada', incorporadas };
  }
}
