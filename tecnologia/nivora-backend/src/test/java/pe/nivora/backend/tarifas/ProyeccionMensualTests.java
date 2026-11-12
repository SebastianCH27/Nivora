package pe.nivora.backend.tarifas;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import pe.nivora.backend.lecturas.dominio.ConsumoPeriodo;
import pe.nivora.backend.lecturas.dominio.MesConsumo;
import pe.nivora.backend.tarifas.dominio.ProyeccionMensual;
import pe.nivora.backend.tarifas.dominio.ProyeccionMensual.Estado;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProyeccionMensualTests {
    private static final Instant INICIO = Instant.parse("2026-11-01T05:00:00Z");
    private static final Instant FIN = Instant.parse("2026-12-01T05:00:00Z");

    @Test
    void calculaConLaDuracionExactaAunqueElPromedioVisibleSeaRedondeado() {
        var corte = INICIO.plus(Duration.ofDays(12)).plusNanos(1000);
        var resultado = ProyeccionMensual.calcular(INICIO, FIN, observado(INICIO, corte, 1001));

        assertEquals(Estado.PROYECTADO, resultado.estado());
        assertEquals(new BigInteger("2502"), resultado.datos().litros());
        assertEquals(new BigDecimal("2.502"), resultado.datos().metrosCubicos());
        assertEquals(new BigDecimal("83.417"), resultado.datos().promedioLitrosPorDia());
        assertEquals(Duration.ofDays(12).plusNanos(1000), resultado.datos().duracionObservada());
    }

    @Test
    void requiereUnDiaCompletoYAdmiteConsumoCero() {
        var unDia = INICIO.plus(Duration.ofDays(1));
        var corto = ProyeccionMensual.calcular(INICIO, FIN, observado(INICIO, unDia.minusNanos(1000), 1));
        assertEquals(Estado.TIEMPO_INSUFICIENTE, corto.estado());
        assertNull(corto.datos());

        var completo = ProyeccionMensual.calcular(INICIO, FIN, observado(INICIO, unDia, 0));
        assertEquals(Estado.PROYECTADO, completo.estado());
        assertEquals(BigInteger.ZERO, completo.datos().litros());
    }

    @Test
    void distingueLecturasInsuficientesCoberturaParcialYMesCerrado() {
        var sinDatos = new ConsumoPeriodo(ConsumoPeriodo.Estado.SIN_DATOS,
            ConsumoPeriodo.Motivo.LECTURAS_INSUFICIENTES, null, null, null, null, null, null);
        var parcial = new ConsumoPeriodo(ConsumoPeriodo.Estado.PARCIAL, ConsumoPeriodo.Motivo.FALTA_LECTURA_INICIAL,
            20L, INICIO.plusSeconds(1), INICIO.plus(Duration.ofDays(3)), "1", "2", "3");

        assertEquals(Estado.SIN_CONSUMO_CALCULABLE, ProyeccionMensual.calcular(INICIO, FIN, sinDatos).estado());
        assertEquals(Estado.COBERTURA_INCOMPLETA, ProyeccionMensual.calcular(INICIO, FIN, parcial).estado());
        assertEquals(Estado.PERIODO_CERRADO,
            ProyeccionMensual.calcular(INICIO, FIN, observado(INICIO, FIN, 20)).estado());
    }

    @Test
    void usaLosDiasDeFebreroBisiestoSinDesbordarElVolumen() {
        var mes = MesConsumo.de(Instant.parse("2028-02-04T05:00:00Z"));
        var inicio = mes.inicio();
        var resultado = ProyeccionMensual.calcular(inicio, mes.fin(),
            observado(inicio, inicio.plus(Duration.ofDays(1)), Long.MAX_VALUE));

        assertEquals(Duration.ofDays(29), resultado.datos().duracionMes());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(29)),
            resultado.datos().litros());
    }

    private static ConsumoPeriodo observado(Instant inicio, Instant corte, long litros) {
        return new ConsumoPeriodo(ConsumoPeriodo.Estado.COMPLETO, null, litros, inicio, corte, "1", "1", "2");
    }
}
