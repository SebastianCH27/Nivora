package pe.nivora.backend.lecturas.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.ConsumoPeriodo.Estado;
import pe.nivora.backend.lecturas.dominio.ConsumoPeriodo.Motivo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConsumoPeriodoTests {
    private static final Instant INICIO = Instant.parse("2026-11-01T05:00:00Z");
    private static final Instant FIN = INICIO.plusSeconds(60);

    @Test
    void calculaLaDiferenciaExactaYConservaLasReferencias() {
        var resultado = calcular(lectura("1", "1", 0, 0),
            lectura("2", "1", 30, 1_200), lectura("3", "1", 60, Long.MAX_VALUE));

        assertEquals(Estado.COMPLETO, resultado.estado());
        assertNull(resultado.motivo());
        assertEquals(Long.MAX_VALUE, resultado.litros());
        assertEquals(new BigDecimal("9223372036854775.807"), resultado.metrosCubicos());
        assertEquals(INICIO, resultado.desde());
        assertEquals(FIN, resultado.hasta());
        assertEquals("1", resultado.idMedidor());
        assertEquals("1", resultado.idLecturaInicial());
        assertEquals("3", resultado.idLecturaFinal());
    }

    @Test
    void identificaLaCoberturaParcialPorCadaExtremo() {
        var faltaInicio = calcular(lectura("1", "1", 1, 50), lectura("2", "1", 60, 90));
        assertEquals(Estado.PARCIAL, faltaInicio.estado());
        assertEquals(Motivo.FALTA_LECTURA_INICIAL, faltaInicio.motivo());
        assertEquals(40L, faltaInicio.litros());
        assertEquals(INICIO.plusSeconds(1), faltaInicio.desde());

        var faltaFin = calcular(lectura("1", "1", 0, 50), lectura("2", "1", 59, 90));
        assertEquals(Estado.PARCIAL, faltaFin.estado());
        assertEquals(Motivo.FALTA_LECTURA_FINAL, faltaFin.motivo());
        assertEquals(INICIO.plusSeconds(59), faltaFin.hasta());
    }

    @Test
    void diferenciaAusenciaDeLecturasDeConsumoCero() {
        var sinDatos = calcular(lectura("1", "1", 0, 50));
        assertEquals(Estado.SIN_DATOS, sinDatos.estado());
        assertEquals(Motivo.LECTURAS_INSUFICIENTES, sinDatos.motivo());
        assertNull(sinDatos.litros());
        assertNull(sinDatos.metrosCubicos());

        var cero = calcular(lectura("1", "1", 0, 50), lectura("2", "1", 60, 50));
        assertEquals(Estado.COMPLETO, cero.estado());
        assertEquals(0L, cero.litros());
        assertEquals(new BigDecimal("0.000"), cero.metrosCubicos());
    }

    @Test
    void rechazaElCambioDeMedidorYElRetrocesoAunqueLosExtremosSuban() {
        var cambio = calcular(lectura("1", "1", 0, 50), lectura("2", "2", 60, 80));
        assertEquals(Estado.INCOMPATIBLE, cambio.estado());
        assertEquals(Motivo.CAMBIO_DE_MEDIDOR, cambio.motivo());
        assertNull(cambio.litros());

        var retroceso = calcular(lectura("1", "1", 0, 50),
            lectura("2", "1", 30, 30), lectura("3", "1", 60, 80));
        assertEquals(Estado.INCOMPATIBLE, retroceso.estado());
        assertEquals(Motivo.RETROCESO_DEL_CONTADOR, retroceso.motivo());
        assertNull(retroceso.litros());
    }

    private static ConsumoPeriodo calcular(LecturaAcumulada... lecturas) {
        return ConsumoPeriodo.calcular(INICIO, FIN, List.of(lecturas));
    }

    private static LecturaAcumulada lectura(String id, String medidor, long segundos, long litros) {
        return new LecturaAcumulada("empresa-agua", id, new CodigoSuministro("000101"),
            medidor, INICIO.plusSeconds(segundos), litros);
    }
}
