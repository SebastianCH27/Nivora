package pe.nivora.empresaagua.tarifas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record HistorialTarifas(String fuente, String codigoSuministro, String moneda,
        String zonaFechas, List<Asignacion> asignaciones) {

    public record Asignacion(String codigoTarifa, int versionTarifa, String nombreTarifa,
            BigDecimal cargoFijo, LocalDate vigenteDesde, LocalDate vigenteHasta,
            LocalDate asignadaDesde, LocalDate asignadaHasta, LocalDate aplicableDesde,
            LocalDate aplicableHasta, List<TarifaActual.Tramo> tramos) {}
}
