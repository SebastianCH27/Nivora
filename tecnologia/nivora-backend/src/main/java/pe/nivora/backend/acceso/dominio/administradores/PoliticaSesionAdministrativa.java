package pe.nivora.backend.acceso.dominio.administradores;

import java.time.Duration;

public record PoliticaSesionAdministrativa(Duration inactividad, Duration duracionMaxima,
        Duration ventanaIntentos, int intentosPorUsuario, int intentosPorOrigen) {
    public PoliticaSesionAdministrativa {
        for (Duration duracion : new Duration[]{inactividad, duracionMaxima, ventanaIntentos}) {
            if (duracion == null || duracion.isNegative() || duracion.isZero()
                    || duracion.getNano() != 0 || duracion.compareTo(Duration.ofDays(1)) > 0) {
                throw new IllegalArgumentException("Duracion de sesion administrativa invalida.");
            }
        }
        if (inactividad.compareTo(duracionMaxima) > 0 || intentosPorUsuario < 1
                || intentosPorUsuario > 100 || intentosPorOrigen < intentosPorUsuario
                || intentosPorOrigen > 1000) throw new IllegalArgumentException("Limites administrativos invalidos.");
    }
}
