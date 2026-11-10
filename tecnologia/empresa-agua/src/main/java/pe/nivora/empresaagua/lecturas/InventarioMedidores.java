package pe.nivora.empresaagua.lecturas;

import java.time.LocalDate;
import java.util.List;

public record InventarioMedidores(
    String fuente, String codigoSuministro, String zonaFechas,
    List<Medidor> medidores) {

    public InventarioMedidores {
        medidores = List.copyOf(medidores);
    }

    public record Medidor(
        String idMedidor, LocalDate fechaInstalacion, LocalDate fechaRetiro) {
    }
}
