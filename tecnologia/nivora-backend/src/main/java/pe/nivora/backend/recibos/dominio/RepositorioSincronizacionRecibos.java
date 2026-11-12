package pe.nivora.backend.recibos.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;

public interface RepositorioSincronizacionRecibos {
    void prepararSuministros(Instant ahora);

    Optional<Reserva> reservar(Instant elegiblesHasta, Instant ahora, Duration duracion);

    boolean finalizar(Reserva reserva, Estado estado, int fallos, Instant proximoIntento, Instant ahora);

    enum Estado {
        ACTUALIZADO, ERROR_FUENTE, SUMINISTRO_NO_ENCONTRADO, CONFLICTO, ERROR_ALMACENAMIENTO, ERROR_INTERNO
    }

    record Reserva(CodigoSuministro suministro, String token, Instant venceEn, int fallosConsecutivos) {}

    class NoDisponible extends RuntimeException {
        public NoDisponible(Throwable causa) {
            super("No se pudo acceder a la programacion de recibos.", causa);
        }
    }
}
