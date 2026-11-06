package pe.nivora.empresaagua.tarifas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TarifaActual(
    String codigoSuministro,
    String titular,
    LocalDate fechaConsulta,
    String moneda,
    String codigoTarifa,
    int versionTarifa,
    String nombreTarifa,
    BigDecimal cargoFijo,
    LocalDate vigenteDesde,
    LocalDate vigenteHasta,
    LocalDate asignadaDesde,
    LocalDate asignadaHasta,
    List<Tramo> tramos
) {
    public record Tramo(
        BigDecimal desdeM3,
        BigDecimal hastaM3,
        BigDecimal precioPorM3
    ) {}
}