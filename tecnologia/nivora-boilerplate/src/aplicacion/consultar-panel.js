import { limitesPeriodo, periodoAnterior, periodoDe } from '../dominio/periodos.js';
import { calcularConsumo } from '../dominio/lecturas.js';
import { calcularCosto, proyectarConsumo } from '../dominio/calculos.js';
import { ErrorNivora } from '../dominio/errores.js';

export class ConsultarPanel {
  constructor({ repositorio, reloj }) { Object.assign(this, { repositorio, reloj }); }
  resumen(codigo, periodo, fechaCorte = null) {
    const estado = this.repositorio.leer();
    const { inicio, fin, dias } = limitesPeriodo(periodo);
    const lecturas = estado.lecturas.filter(l => l.suministro === codigo);
    const fechaFuente = estado.integracion.fechaFuente;
    const corte = fechaCorte ? Math.min(fin, Math.max(inicio, Date.parse(fechaCorte))) : fin;
    const consumo = calcularConsumo(lecturas, inicio, corte);
    consumo.metrosCubicos = consumo.litros === null ? null : consumo.litros / 1000;
    const tarifas = estado.tarifas.filter(t => t.suministro === codigo && Date.parse(t.vigenteDesde) <= inicio && Date.parse(t.vigenteHasta) >= corte);
    const tarifa = tarifas.at(-1) ?? null;
    const costo = consumo.litros !== null && tarifa ? calcularCosto(consumo.litros, tarifa) : null;
    const proyeccion = corte < fin ? proyectarConsumo(consumo, inicio, fin) : null;
    if (proyeccion && tarifa) proyeccion.costo = calcularCosto(proyeccion.litros, tarifa);
    return { periodo, dias, inicio: new Date(inicio).toISOString(), fin: new Date(fin).toISOString(), consumo, costo, tarifa, proyeccion, fechaFuente };
  }
  ejecutar(codigo) {
    const estado = this.repositorio.leer();
    const suministro = estado.suministros.find(s => s.codigo === codigo);
    if (!suministro) throw new ErrorNivora('DATOS_NO_DISPONIBLES', 'Este suministro aún no tiene datos almacenados.');
    const fecha = estado.integracion.fechaFuente || this.reloj();
    const periodo = periodoDe(fecha);
    const ultimaLectura = estado.lecturas.filter(l => l.suministro === codigo && Date.parse(l.fecha) <= Date.parse(fecha)).sort((a,b) => Date.parse(a.fecha)-Date.parse(b.fecha)).at(-1)?.fecha ?? null;
    const actual = this.resumen(codigo, periodo, ultimaLectura && periodoDe(ultimaLectura) === periodo ? ultimaLectura : fecha);
    const anterior = this.resumen(codigo, periodoAnterior(periodo));
    const meses = [...new Set(estado.lecturas.filter(l => l.suministro === codigo).map(l => periodoDe(l.fecha)))].filter(p => p < periodo).sort();
    const comparacion = actual.consumo.litros !== null && anterior.consumo.litros !== null ? {
      litrosActual: actual.consumo.litros, litrosAnterior: anterior.consumo.litros,
      diferenciaLitros: actual.consumo.litros - anterior.consumo.litros,
      diferenciaCostoCentimos: actual.costo && anterior.costo ? actual.costo.totalCentimos - anterior.costo.totalCentimos : null,
      nota: 'El mes actual cubre hasta la última lectura; el anterior cubre el mes completo cuando sus datos están completos.'
    } : null;
    return { suministro, actual, anterior, comparacion, historial: meses.map(p => this.resumen(codigo, p)), ultimaLectura, integracion: estado.integracion, relojDemostracion: true };
  }
  recibos(codigo) { return this.repositorio.leer().recibos.filter(r => r.suministro === codigo).sort((a,b) => b.periodo.localeCompare(a.periodo)); }
}
