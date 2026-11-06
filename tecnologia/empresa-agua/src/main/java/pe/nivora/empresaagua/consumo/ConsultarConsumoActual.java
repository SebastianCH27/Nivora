package pe.nivora.empresaagua.consumo;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

@Service
public class ConsultarConsumoActual {

    private static final ZoneId LIMA =
        ZoneId.of("America/Lima");

    private final ConsumoRepositorio repositorio;

    public ConsultarConsumoActual(
            ConsumoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public ConsumoActual consultar(String codigo) {
        if (codigo == null
                || !codigo.matches("[0-9]{1,20}")) {

            throw new IllegalArgumentException(
                "El codigo debe contener entre 1 y 20 digitos.");
        }

        LocalDate periodo = LocalDate.now(LIMA)
            .withDayOfMonth(1);

        ConsumoActual consumo = repositorio
            .buscar(codigo, periodo)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe un suministro con ese codigo."));

        if (consumo.periodo() == null
                || consumo.consumoLitros() == null
                || consumo.actualizadoHasta() == null
                || !consumo.actualizadoHasta()
                    .atZone(LIMA)
                    .toLocalDate()
                    .withDayOfMonth(1)
                    .equals(periodo)) {

            throw new IllegalStateException(
                "El consumo del mes todavia no esta disponible. "
                    + "Intenta nuevamente.");
        }

        return consumo;
    }
}