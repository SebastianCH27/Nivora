package pe.nivora.empresaagua.consumo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ConsumoActual(
    String codigoSuministro,
    String titular,
    LocalDate periodo,
    BigDecimal consumoLitros,
    Instant actualizadoHasta
) {}