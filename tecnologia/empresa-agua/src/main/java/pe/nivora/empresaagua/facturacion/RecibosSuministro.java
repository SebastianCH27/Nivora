package pe.nivora.empresaagua.facturacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RecibosSuministro(
    String codigoSuministro,
    String titular,
    String moneda,
    List<Recibo> recibos
) {
    public record Recibo(
        String numeroRecibo,
        LocalDate periodo,
        BigDecimal consumoLitros,
        BigDecimal cargoFijo,
        BigDecimal importeConsumo,
        BigDecimal importeTotal,
        LocalDate fechaDisponibilidad,
        LocalDate fechaVencimiento,
        String codigoTarifa,
        int versionTarifa
    ) {}
}