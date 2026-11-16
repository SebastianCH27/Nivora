package pe.nivora.backend.recibos.dominio;

import java.time.Instant;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;

public interface ConsultaSupervisionRecibos {
    Optional<SupervisionRecibos> consultar(CodigoSuministro suministro, Instant ahora);

    class NoDisponible extends RuntimeException {
        public NoDisponible(Throwable causa) {
            super("No se pudo consultar el estado guardado de los recibos.", causa);
        }
    }
}
