package pe.nivora.backend.lecturas.dominio;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.tarifas.dominio.ProyeccionMensual;
import static org.junit.jupiter.api.Assertions.*;

class ConsumoIntervalosTests {
    private static final CodigoSuministro CODIGO = new CodigoSuministro("000101");
    private static final Instant INICIO = Instant.parse("2026-10-01T05:00:00Z");
    private static final Instant FIN = INICIO.plusSeconds(7200);

    @Test void sumaIntervalosContiguosSinRestarSusConsumos() {
        var consumo = calcular(intervalo("1", 0, 3600, 80), intervalo("2", 3600, 7200, 120));
        assertEquals(ConsumoPeriodo.Estado.COMPLETO, consumo.estado());
        assertEquals(200L, consumo.litros());
        assertEquals("0.200", consumo.metrosCubicos().toPlainString());
        assertEquals(INICIO, consumo.desde());
        assertEquals(FIN, consumo.hasta());
        assertEquals("1", consumo.idLecturaInicial());
        assertEquals("2", consumo.idLecturaFinal());
        assertEquals(Lectura.Tipo.INTERVALO, consumo.tipoMedicion());
    }

    @Test void unSoloIntervaloPuedeCubrirTodoElPeriodoYMedirCero() {
        var consumo = calcular(intervalo("1", 0, 7200, 0));
        assertEquals(ConsumoPeriodo.Estado.COMPLETO, consumo.estado());
        assertEquals(0L, consumo.litros());
        assertEquals(consumo.idLecturaInicial(), consumo.idLecturaFinal());
    }

    @Test void conservaElSubtotalCuandoFaltaUnExtremo() {
        var inicial = calcular(intervalo("1", 1, 7200, 100));
        var finalizadoAntes = calcular(intervalo("1", 0, 7199, 100));
        assertEquals(ConsumoPeriodo.Estado.PARCIAL, inicial.estado());
        assertEquals(ConsumoPeriodo.Motivo.FALTA_LECTURA_INICIAL, inicial.motivo());
        assertEquals(ConsumoPeriodo.Motivo.FALTA_LECTURA_FINAL, finalizadoAntes.motivo());
        assertEquals(100L, inicial.litros());
    }

    @Test void detectaHuecosAunqueLosExtremosCoincidanYNoProyectaElSubtotal() {
        var consumo = calcular(intervalo("1", 0, 1800, 80), intervalo("2", 3600, 7200, 120));
        assertEquals(200L, consumo.litros());
        assertEquals(ConsumoPeriodo.Estado.PARCIAL, consumo.estado());
        assertEquals(ConsumoPeriodo.Motivo.HUECOS_ENTRE_INTERVALOS, consumo.motivo());
        assertEquals(ProyeccionMensual.Estado.COBERTURA_INCOMPLETA,
            ProyeccionMensual.calcular(INICIO, INICIO.plusSeconds(86400 * 31L), consumo).estado());
    }

    @Test void superpuestosODuplicadosNoProducenUnaSuma() {
        for (var lecturas : List.of(
                List.of(intervalo("1", 0, 4000, 80), intervalo("2", 3600, 7200, 120)),
                List.of(intervalo("1", 0, 7200, 80), intervalo("1", 0, 7200, 80)))) {
            var consumo = ConsumoPeriodo.calcular(INICIO, FIN, lecturas);
            assertEquals(ConsumoPeriodo.Estado.INCOMPATIBLE, consumo.estado());
            assertEquals(ConsumoPeriodo.Motivo.INTERVALOS_SUPERPUESTOS, consumo.motivo());
            assertNull(consumo.litros());
        }
    }

    @Test void noProrrateaUnIntervaloQueCruzaUnLimite() {
        var soloCruzado = calcular(intervalo("1", -3600, 7200, 900));
        assertEquals(ConsumoPeriodo.Estado.SIN_DATOS, soloCruzado.estado());
        assertEquals(ConsumoPeriodo.Motivo.INTERVALO_CRUZA_LIMITE, soloCruzado.motivo());
        assertNull(soloCruzado.litros());
        var parcial = calcular(intervalo("1", -3600, 3600, 900), intervalo("2", 3600, 7200, 120));
        assertEquals(120L, parcial.litros());
        assertEquals(ConsumoPeriodo.Estado.PARCIAL, parcial.estado());
        assertEquals(ConsumoPeriodo.Motivo.INTERVALO_CRUZA_LIMITE, parcial.motivo());
        assertEquals(INICIO.plusSeconds(3600), parcial.desde());
    }

    @Test void unIntervaloEnElLimiteSeCuentaEnUnSoloPeriodo() {
        var lectura = intervalo("1", 0, 7200, 200);
        assertEquals(200L, calcular(lectura).litros());
        assertEquals(ConsumoPeriodo.Estado.SIN_DATOS,
            ConsumoPeriodo.calcular(FIN, FIN.plusSeconds(7200), List.of(lectura)).estado());
        assertEquals(ConsumoPeriodo.Estado.SIN_DATOS,
            ConsumoPeriodo.calcular(INICIO.minusSeconds(7200), INICIO, List.of(lectura)).estado());
    }

    @Test void noMezclaIntervalosConContadoresNiConOtroMedidor() {
        var contador = new LecturaAcumulada("empresa-agua", "3", CODIGO, "1", INICIO, 1000);
        assertEquals(ConsumoPeriodo.Motivo.TIPOS_DE_MEDICION_MEZCLADOS,
            calcular(contador, intervalo("1", 0, 7200, 200)).motivo());
        var otro = new LecturaIntervalo("empresa-agua", "2", CODIGO, "2", INICIO.plusSeconds(3600), FIN, 120);
        assertEquals(ConsumoPeriodo.Motivo.CAMBIO_DE_MEDIDOR,
            calcular(intervalo("1", 0, 3600, 80), otro).motivo());
    }

    @Test void intervalosDeOtroPeriodoNoVuelvenIncompatibleUnAcumulado() {
        var anterior=intervalo("1",-3600,0,100);
        var primera=new LecturaAcumulada("empresa-agua","2",CODIGO,"1",INICIO,1000);
        var ultima=new LecturaAcumulada("empresa-agua","3",CODIGO,"1",FIN,1100);
        var consumo=calcular(anterior,primera,ultima);
        assertEquals(ConsumoPeriodo.Estado.COMPLETO,consumo.estado());
        assertEquals(Lectura.Tipo.ACUMULADA,consumo.tipoMedicion());
        assertEquals(100L,consumo.litros());
    }

    @Test void elDesbordeSeInformaSinInventarUnConsumoNegativo() {
        var consumo = calcular(intervalo("1", 0, 3600, Long.MAX_VALUE), intervalo("2", 3600, 7200, 1));
        assertEquals(ConsumoPeriodo.Motivo.SUMA_FUERA_DE_RANGO, consumo.motivo());
        assertNull(consumo.litros());
    }

    @Test void validaElOrdenYLaDuracionSinExigirConsumosCrecientes() {
        assertThrows(IllegalArgumentException.class, () -> intervalo("1", 3600, 3600, 0));
        assertThrows(IllegalArgumentException.class, () -> intervalo("1", 7200, 3600, 10));
        assertThrows(IllegalArgumentException.class, () -> intervalo("1", 0, 3600, -1));
        assertEquals(200L, calcular(intervalo("2", 3600, 7200, 80), intervalo("1", 0, 3600, 120)).litros());
    }

    @Test void unIntervaloContinuoPermiteProyectarConElTiempoRealmenteCubierto() {
        var consumo = ConsumoPeriodo.calcular(INICIO, INICIO.plusSeconds(86400),
            List.of(intervalo("1", 0, 86400, 100)));
        var proyeccion = ProyeccionMensual.calcular(INICIO, INICIO.plusSeconds(86400 * 31L), consumo);
        assertEquals(ProyeccionMensual.Estado.PROYECTADO, proyeccion.estado());
        assertEquals("3100", proyeccion.datos().litros().toString());
    }

    @Test void elInventarioCompruebaTodoElTramoYElRetiroInclusivoEnLima() {
        var contexto = new ContextoLecturas("empresa-agua", CODIGO, "10", INICIO.plusSeconds(86400 * 2L),
            List.of(new ContextoLecturas.Medidor("1", LocalDate.of(2026,10,1), LocalDate.of(2026,10,1))));
        assertTrue(contexto.validar(intervalo("1", 0, 86400, 100)).isEmpty());
        assertEquals(MotivoRechazoLectura.FECHA_FUERA_VIGENCIA_MEDIDOR,
            contexto.validar(intervalo("2", -1, 7200, 100)).orElseThrow());
        assertEquals(MotivoRechazoLectura.FECHA_FUERA_VIGENCIA_MEDIDOR,
            contexto.validar(intervalo("3", 0, 86401, 100)).orElseThrow());
    }

    private static ConsumoPeriodo calcular(Lectura... lecturas) {
        return ConsumoPeriodo.calcular(INICIO, FIN, List.of(lecturas));
    }

    private static LecturaIntervalo intervalo(String id, long inicio, long fin, long litros) {
        return new LecturaIntervalo("empresa-agua", id, CODIGO, "1", INICIO.plusSeconds(inicio),
            INICIO.plusSeconds(fin), litros);
    }
}
