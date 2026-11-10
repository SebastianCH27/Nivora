package pe.nivora.backend.lecturas.dominio.contratos;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import pe.nivora.backend.lecturas.dominio.EstadoSincronizacion;
import pe.nivora.backend.lecturas.dominio.ReservaSincronizacion;

public interface RepositorioSincronizacion {
    void prepararSuministros(String fuente, Instant ahora);
    Optional<ReservaSincronizacion> reservar(String fuente, Instant elegiblesHasta,
        Instant ahora, Duration duracion);
    boolean finalizar(ReservaSincronizacion reserva, EstadoSincronizacion estado,
        int fallos, Instant proximoIntento, Instant ahora);
}
