package pe.nivora.backend.tarifas.dominio;

import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;

public interface FuenteTarifas {
    Optional<HistorialTarifario> consultar(CodigoSuministro suministro);
}
