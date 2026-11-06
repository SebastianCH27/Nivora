package pe.nivora.empresaagua.consumo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record HistorialConsumo(
    String codigoSuministro,
    String titular,
    List<Periodo> periodos
) {
    public record Periodo(
        LocalDate periodo,
        BigDecimal consumoLitros,
        String estado,
        Instant fechaActualizacion,
        Instant cerradoEn
    ) {}
}