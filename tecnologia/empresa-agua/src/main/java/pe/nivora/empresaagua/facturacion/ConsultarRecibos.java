package pe.nivora.empresaagua.facturacion;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

@Service
public class ConsultarRecibos {

    private static final ZoneId LIMA =
        ZoneId.of("America/Lima");

    private final ReciboRepositorio repositorio;

    public ConsultarRecibos(ReciboRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public RecibosSuministro consultar(
            String codigo, int limite) {

        if (codigo == null
                || !codigo.matches("[0-9]{1,20}")) {

            throw new IllegalArgumentException(
                "El codigo debe contener entre 1 y 20 digitos.");
        }

        if (limite < 1 || limite > 60) {
            throw new IllegalArgumentException(
                "El parametro limite debe estar entre 1 y 60.");
        }

        return repositorio.buscar(
                codigo,
                LocalDate.now(LIMA),
                limite)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe un suministro con ese codigo."));
    }
}