package pe.nivora.empresaagua.suministros;

import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

@Service
public class ConsultarSuministro {

    private final SuministroRepositorio repositorio;

    public ConsultarSuministro(SuministroRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public Suministro consultar(String codigo) {
        if (codigo == null || !codigo.matches("[0-9]{1,20}")) {
            throw new IllegalArgumentException(
                "El codigo debe contener entre 1 y 20 digitos.");
        }

        return repositorio.buscarPorCodigo(codigo)
            .orElseThrow(() -> new NoSuchElementException(
                "No existe un suministro con ese codigo."));
    }
}
