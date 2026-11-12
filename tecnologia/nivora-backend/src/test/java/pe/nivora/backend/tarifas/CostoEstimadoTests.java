package pe.nivora.backend.tarifas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import pe.nivora.backend.tarifas.dominio.CostoEstimado;
import pe.nivora.backend.tarifas.dominio.VersionTarifaria;
import pe.nivora.backend.tarifas.dominio.VersionTarifaria.Tramo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CostoEstimadoTests {
    // Datos del simulador Yakumetro para Huamanga, domestico, con medidor y sin subsidio.
    private static final VersionTarifaria REFERENCIA_HUAMANGA = new VersionTarifaria(
        "HUAMANGA-REFERENCIA", 1, "Referencia de prueba", LocalDate.parse("2026-10-01"),
        decimal("3.60"), decimal("18"), List.of(
            tramo("0", "8", "1.16", "0.51"),
            tramo("8", "18", "1.28", "0.56"),
            tramo("18", null, "1.99", "0.89")));

    @ParameterizedTest
    @CsvSource({
        "12000, 14.40, 6.32, 4.38, 28.70, 2",
        "20000, 26.06, 11.46, 7.40, 48.52, 3",
        "18000, 22.08, 9.68, 6.36, 41.72, 2",
        "0, 0.00, 0.00, 0.65, 4.25, 0"
    })
    void calculaPorPorcionesYRedondeaCadaConcepto(long litros, String agua, String alcantarillado,
            String igv, String total, int tramos) {
        var costo = CostoEstimado.calcular(litros, REFERENCIA_HUAMANGA);
        assertEquals(decimal(agua), costo.agua());
        assertEquals(decimal(alcantarillado), costo.alcantarillado());
        assertEquals(decimal("3.60"), costo.cargoFijo());
        assertEquals(decimal(igv), costo.igv());
        assertEquals(decimal(total), costo.totalEstimado());
        assertEquals(tramos, costo.tramos().size());
    }

    @Test
    void conservaCentavosAlSumarAntesDeRedondearLosTramos() {
        var tarifa = new VersionTarifaria("FRACT", 1, "Decimales", LocalDate.parse("2026-10-01"),
            decimal("0.00"), decimal("0"), List.of(
                tramo("0", "0.002", "2.50", "0"),
                tramo("0.002", null, "2.50", "0")));
        var costo = CostoEstimado.calcular(4, tarifa);
        assertEquals(decimal("0.01"), costo.agua());
        assertEquals(decimal("0.00"), costo.alcantarillado());
        assertEquals(decimal("0.00"), costo.igv());
        assertEquals(decimal("0.01"), costo.totalEstimado());
    }

    @Test
    void unaTarifaParcialSoloEntregaElSubtotalDeConceptosConocidos() {
        var tarifa = new VersionTarifaria("DOMESTICA", 1, "Demostracion", LocalDate.parse("2026-10-01"),
            decimal("5.00"), null, List.of(tramo("0", null, "1.50", null)));
        var costo = CostoEstimado.calcular(5637, tarifa);
        assertEquals(decimal("8.46"), costo.agua());
        assertEquals(decimal("13.46"), costo.subtotalConocido());
        assertNull(costo.alcantarillado());
        assertNull(costo.igv());
        assertNull(costo.totalEstimado());
        assertThrows(IllegalArgumentException.class, () -> CostoEstimado.calcular(-1, tarifa));
    }

    private static Tramo tramo(String desde, String hasta, String agua, String alcantarillado) {
        return new Tramo(decimal(desde), decimal(hasta), decimal(agua), decimal(alcantarillado));
    }

    private static BigDecimal decimal(String valor) {
        return valor == null ? null : new BigDecimal(valor);
    }
}
