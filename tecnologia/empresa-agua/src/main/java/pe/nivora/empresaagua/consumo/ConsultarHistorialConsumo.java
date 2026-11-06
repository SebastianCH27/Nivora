package pe.nivora.empresaagua.consumo;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

@Service
public class ConsultarHistorialConsumo {

    private static final ZoneId LIMA =
        ZoneId.of("America/Lima");

    private final ConsumoRepositorio repositorio;

    public ConsultarHistorialConsumo(
            ConsumoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public HistorialConsumo consultar(String codigo, int meses) {
        if (codigo == null
                || !codigo.matches("[0-9]{1,20}")) {

            throw new IllegalArgumentException(
                "El codigo debe contener entre 1 y 20 digitos.");
        }

        if (meses < 1 || meses > 60) {
            throw new IllegalArgumentException(
                "El parametro meses debe estar entre 1 y 60.");
        }

        LocalDate hasta = LocalDate.now(LIMA)
            .withDayOfMonth(1);

        LocalDate desde = hasta.minusMonths(meses - 1L);

        return repositorio.buscarHistorial(codigo, desde, hasta)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe un suministro con ese codigo."));
    }
}