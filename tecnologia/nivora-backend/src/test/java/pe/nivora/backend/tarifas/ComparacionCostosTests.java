package pe.nivora.backend.tarifas;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.nivora.backend.lecturas.dominio.ConsumoPeriodo;
import pe.nivora.backend.tarifas.dominio.ComparacionCostos;
import static org.junit.jupiter.api.Assertions.*;

class ComparacionCostosTests {
    @Test void diferenciaPositivaNegativaOCeroConservaCentimosYSigno() {
        var base=consumo(10000L);
        var comparado=consumo(13200L);
        var positiva=ComparacionCostos.calcular(base,new BigDecimal("30.00"),true,
            comparado,new BigDecimal("38.00"),true);
        assertEquals(ComparacionCostos.Estado.COMPLETA,positiva.estado());
        assertEquals(3200L,positiva.diferenciaLitros());
        assertEquals("3.200",positiva.diferenciaMetrosCubicos().toPlainString());
        assertEquals("8.00",positiva.diferenciaCostoEstimado().toPlainString());
        var negativa=ComparacionCostos.calcular(comparado,new BigDecimal("38.00"),true,
            base,new BigDecimal("30.00"),true);
        assertEquals(-3200L,negativa.diferenciaLitros());
        assertEquals("-8.00",negativa.diferenciaCostoEstimado().toPlainString());
        var cero=ComparacionCostos.calcular(consumo(0L),new BigDecimal("0.00"),true,
            consumo(0L),new BigDecimal("0.00"),true);
        assertEquals("0.00",cero.diferenciaCostoEstimado().toPlainString());
    }

    @ParameterizedTest
    @CsvSource({"true,false","false,true","false,false"})
    void unPeriodoNoCerradoOIncompletoConservaSoloUnaComparacionParcial(boolean base,boolean comparado) {
        var resultado=ComparacionCostos.calcular(consumo(1000L),new BigDecimal("7.50"),base,
            consumo(2000L),new BigDecimal("10.00"),comparado);
        assertEquals(ComparacionCostos.Estado.PARCIAL,resultado.estado());
        assertEquals("2.50",resultado.diferenciaCostoEstimado().toPlainString());
    }

    @Test void unSubtotalConConceptosAusentesNoSeConvierteEnUnaDiferenciaMonetaria() {
        for(boolean faltaBase:new boolean[]{true,false}) {
            var resultado=ComparacionCostos.calcular(consumo(1000L),faltaBase?null:new BigDecimal("7.50"),true,
                consumo(2000L),faltaBase?new BigDecimal("10.00"):null,true);
            assertEquals(ComparacionCostos.Estado.NO_CALCULABLE,resultado.estado());
            assertEquals(1000L,resultado.diferenciaLitros());
            assertNull(resultado.diferenciaCostoEstimado());
        }
    }

    @Test void lasLecturasIncalculablesDejanAmbasDiferenciasIndefinidas() {
        var sinDatos=new ConsumoPeriodo(ConsumoPeriodo.Estado.SIN_DATOS,
            ConsumoPeriodo.Motivo.LECTURAS_INSUFICIENTES,null,null,null,null,null,null);
        var resultado=ComparacionCostos.calcular(sinDatos,null,false,
            consumo(0L),new BigDecimal("5.00"),true);
        assertEquals(ComparacionCostos.Estado.NO_CALCULABLE,resultado.estado());
        assertNull(resultado.diferenciaLitros());
        assertNull(resultado.diferenciaMetrosCubicos());
        assertNull(resultado.diferenciaCostoEstimado());
    }

    @Test void laCoberturaParcialImpideDeclararLaComparacionCompleta() {
        var parcial=new ConsumoPeriodo(ConsumoPeriodo.Estado.PARCIAL,
            ConsumoPeriodo.Motivo.HUECOS_ENTRE_INTERVALOS,2000L,
            Instant.parse("2026-10-01T05:00:00Z"),Instant.parse("2026-11-01T05:00:00Z"),"1","1","2");
        assertEquals(ComparacionCostos.Estado.PARCIAL,
            ComparacionCostos.calcular(parcial,new BigDecimal("10.00"),true,
                consumo(2000L),new BigDecimal("10.00"),true).estado());
    }

    @Test void losExtremosDeLongNoDesbordanLaDiferenciaDeVolumen() {
        var resultado=ComparacionCostos.calcular(consumo(Long.MAX_VALUE),BigDecimal.ZERO,true,
            consumo(0L),BigDecimal.ZERO,true);
        assertEquals(-Long.MAX_VALUE,resultado.diferenciaLitros());
        assertEquals("-9223372036854775.807",resultado.diferenciaMetrosCubicos().toPlainString());
    }

    private static ConsumoPeriodo consumo(Long litros) {
        return new ConsumoPeriodo(ConsumoPeriodo.Estado.COMPLETO,null,litros,
            Instant.parse("2026-10-01T05:00:00Z"),Instant.parse("2026-11-01T05:00:00Z"),"1","1","2");
    }
}
