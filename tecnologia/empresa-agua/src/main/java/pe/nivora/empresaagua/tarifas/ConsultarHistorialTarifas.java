package pe.nivora.empresaagua.tarifas;

import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

@Service
public class ConsultarHistorialTarifas {
    private final TarifaRepositorio repositorio;

    public ConsultarHistorialTarifas(TarifaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public HistorialTarifas consultar(String codigo) {
        if (codigo == null || !codigo.matches("[0-9]{1,20}")) {
            throw new IllegalArgumentException("El codigo debe contener entre 1 y 20 digitos.");
        }
        return repositorio.buscarHistorial(codigo)
            .orElseThrow(() -> new NoSuchElementException("No existe un suministro con ese codigo."));
    }
}
