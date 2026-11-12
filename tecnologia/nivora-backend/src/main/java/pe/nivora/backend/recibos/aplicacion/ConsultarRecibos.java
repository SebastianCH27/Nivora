package pe.nivora.backend.recibos.aplicacion;

import java.time.YearMonth;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.recibos.dominio.RepositorioRecibos;

public class ConsultarRecibos {
    private final RepositorioRecibos repositorio;

    public ConsultarRecibos(RepositorioRecibos repositorio) {
        this.repositorio = repositorio;
    }

    public RepositorioRecibos.Pagina ejecutar(CodigoSuministro suministro, YearMonth antesDe, int limite) {
        return repositorio.buscarPagina(suministro, antesDe, limite);
    }
}
