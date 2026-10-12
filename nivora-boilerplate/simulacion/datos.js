export const FECHA_INICIAL = '2026-10-11T17:00:00.000Z'; // 12:00 en Lima.
export const PERSONAS = [
  { dni: '00000001', nombre: 'Persona de prueba Uno' },
  { dni: '00000002', nombre: 'Persona de prueba Dos' },
  { dni: '00000003', nombre: 'Persona de prueba Tres' }
];

export function crearDatosSimulados() {
  const suministros = ['13100390', '13100391', '13100392'].map((codigo, i) => ({ codigo, medidor: `MED-DEMO-${i+1}`, descripcion: ['Hogar de demostración', 'Consumo elevado', 'Lectura de inicio faltante'][i], simulado: true }));
  const lecturas = [];
  for (const [i, s] of suministros.entries()) {
    const fechas = ['2026-08-01T05:00:00.000Z','2026-09-01T05:00:00.000Z','2026-10-01T05:00:00.000Z',FECHA_INICIAL];
    const valores = [100000, 108000, 117000, i === 1 ? 129000 : 120500];
    fechas.forEach((fecha, j) => {
      if (i === 2 && j === 2) return;
      lecturas.push({ id: `${s.codigo}-base-${j}`, suministro: s.codigo, medidor: s.medidor, fecha, valor: valores[j], unidad: 'L', tipo: 'acumulada', fuente: 'empresa-demo' });
    });
  }
  const tarifas = suministros.map(s => ({ id: `TARIFA-DEMO-${s.codigo}-v1`, suministro: s.codigo, vigenteDesde: '2026-08-01T05:00:00.000Z', vigenteHasta: '2027-01-01T05:00:00.000Z', fijoCentimos: 400, saneamientoPorcentaje: 20, impuestoPorcentaje: 18, tramos: [{ hastaLitros: 10000, centimosPorM3: 250 }, { hastaLitros: null, centimosPorM3: 350 }], ficticia: true }));
  const recibos = suministros.map(s => ({ id: `REC-DEMO-${s.codigo}-2026-08`, suministro: s.codigo, periodo: '2026-08', totalCentimos: 3350, disponibleDesde: '2026-09-01T05:00:00.000Z', empresa: 'Empresa de agua simulada', simulado: true }));
  return { suministros, lecturas, tarifas, recibos, fechaFuente: FECHA_INICIAL };
}
