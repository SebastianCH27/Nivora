package pe.nivora.backend.acceso.dominio.administradores;

import java.time.Instant;
import java.util.Set;

public record SesionAdministrativa(String id, Administrador cuenta, Instant creadaEn,
        Instant ultimaActividad, Instant expiraEn, Instant inactivaHasta) {
    public Set<String> permisos() {
        if (cuenta.cambioClaveRequerido()) return Set.of();
        return cuenta.gestionarAdministradores()
            ? Set.of("SUPERVISAR_INTEGRACION", "GESTIONAR_ADMINISTRADORES", "GESTIONAR_TARIFAS", "GESTIONAR_ALERTAS")
            : Set.of("SUPERVISAR_INTEGRACION", "GESTIONAR_TARIFAS", "GESTIONAR_ALERTAS");
    }
}
