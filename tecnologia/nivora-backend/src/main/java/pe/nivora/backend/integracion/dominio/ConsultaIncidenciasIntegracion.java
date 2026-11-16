package pe.nivora.backend.integracion.dominio;

import java.util.List;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.integracion.dominio.IncidenciaIntegracion.Cursor;

public interface ConsultaIncidenciasIntegracion {
    /** Devuelve un Optional vacio si el suministro no esta registrado. */
    Optional<List<IncidenciaIntegracion>> consultar(CodigoSuministro suministro, Cursor antesDe, int maximo);

    class NoDisponible extends RuntimeException {
        public NoDisponible(Throwable causa) {
            super("No se pudieron consultar las incidencias de integracion.", causa);
        }
    }
}
