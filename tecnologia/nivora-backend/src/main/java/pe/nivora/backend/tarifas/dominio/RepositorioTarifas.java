package pe.nivora.backend.tarifas.dominio;

import java.time.Instant;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;

public interface RepositorioTarifas {
    Guardado guardar(HistorialTarifario historial, Instant consultadoEn);

    Optional<Guardado> buscar(String fuente, CodigoSuministro suministro);

    record Guardado(long revision, Instant consultadoEn, HistorialTarifario historial) {}
}
