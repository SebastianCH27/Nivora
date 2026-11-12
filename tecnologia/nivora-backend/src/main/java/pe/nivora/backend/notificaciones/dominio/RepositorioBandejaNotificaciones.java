package pe.nivora.backend.notificaciones.dominio;

import java.time.Instant;
import java.util.List;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;

public interface RepositorioBandejaNotificaciones {
    List<Notificacion> buscar(CodigoSuministro suministro, Notificacion.Cursor antesDe,
        boolean soloPendientes, int limite);

    boolean marcarLeida(CodigoSuministro suministro, Notificacion.Clave clave, Instant leidaEn);

    class NoDisponible extends RuntimeException {
        public NoDisponible(Throwable causa) {
            super("No se pudo acceder a las notificaciones guardadas.", causa);
        }
    }
}
