package pe.nivora.backend.recibos.aplicacion;

import java.time.Clock;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.recibos.dominio.FuenteRecibos;
import pe.nivora.backend.recibos.dominio.FuenteRecibosNoDisponibleException;
import pe.nivora.backend.recibos.dominio.RepositorioRecibos;

public class IncorporarRecibos {
    private final FuenteRecibos fuente;
    private final RepositorioRecibos repositorio;
    private final Clock reloj;

    public IncorporarRecibos(FuenteRecibos fuente, RepositorioRecibos repositorio, Clock reloj) {
        this.fuente = fuente;
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    public Optional<RepositorioRecibos.Incorporacion> ejecutar(CodigoSuministro suministro, int limite) {
        return fuente.consultar(suministro, limite).map(lote -> {
            if (!suministro.equals(lote.suministro())) {
                throw new FuenteRecibosNoDisponibleException();
            }
            return repositorio.guardar(lote, reloj.instant());
        });
    }
}
