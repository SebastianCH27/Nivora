package pe.nivora.backend.notificaciones.dominio;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.administradores.UsuarioAdministrador;

public interface RepositorioConfiguracionNotificaciones {
    Optional<Preferencia> buscarPreferencia(CodigoSuministro suministro, UUID dispositivo);

    Preferencia guardarPreferencia(CodigoSuministro suministro, Preferencia preferencia);

    Optional<ControlAlertas> buscarControlAlertas();

    ControlAlertas guardarControlAlertas(boolean habilitada, UsuarioAdministrador actor, Instant ahora);

    enum Permiso { CONCEDIDO, DENEGADO, PENDIENTE }

    record Preferencia(UUID idDispositivo, Permiso permiso, boolean habilitadas, Instant actualizadaEn) {
        public Preferencia {
            if (habilitadas && permiso != Permiso.CONCEDIDO) {
                throw new IllegalArgumentException("El dispositivo requiere permiso concedido para activar las notificaciones.");
            }
        }
    }

    record ControlAlertas(boolean habilitada, String actualizadaPor, Instant actualizadaEn) {}

    class NoDisponible extends RuntimeException {
        public NoDisponible(Throwable causa) {
            super("No se pudo acceder a la configuracion de notificaciones.", causa);
        }
    }
}
