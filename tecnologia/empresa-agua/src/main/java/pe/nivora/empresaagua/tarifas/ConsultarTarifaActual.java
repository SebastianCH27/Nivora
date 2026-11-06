package pe.nivora.empresaagua.tarifas;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

@Service
public class ConsultarTarifaActual {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final TarifaRepositorio repositorio;

    public ConsultarTarifaActual(TarifaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public TarifaActual consultar(String codigo) {
        if (codigo == null || !codigo.matches("[0-9]{1,20}")) {
            throw new IllegalArgumentException(
                "El codigo debe contener entre 1 y 20 digitos.");
        }

        return repositorio.buscar(codigo, LocalDate.now(LIMA))
            .orElseThrow(() -> new NoSuchElementException(
                "No existe un suministro con ese codigo."));
    }
}