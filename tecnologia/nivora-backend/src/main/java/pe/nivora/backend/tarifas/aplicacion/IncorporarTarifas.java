package pe.nivora.backend.tarifas.aplicacion;

import java.time.Clock;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.tarifas.dominio.FuenteTarifas;
import pe.nivora.backend.tarifas.dominio.FuenteTarifasNoDisponibleException;
import pe.nivora.backend.tarifas.dominio.RepositorioTarifas;

public class IncorporarTarifas {
    private final FuenteTarifas fuente;
    private final RepositorioTarifas repositorio;
    private final Clock reloj;

    public IncorporarTarifas(FuenteTarifas fuente, RepositorioTarifas repositorio, Clock reloj) {
        this.fuente = fuente;
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    public Optional<RepositorioTarifas.Guardado> ejecutar(CodigoSuministro suministro) {
        var consultadoEn = reloj.instant();
        return fuente.consultar(suministro).map(historial -> {
            if (!suministro.equals(historial.suministro()) || !"empresa-agua".equals(historial.fuente())) {
                throw new FuenteTarifasNoDisponibleException();
            }
            return repositorio.guardar(historial, consultadoEn);
        });
    }
}
