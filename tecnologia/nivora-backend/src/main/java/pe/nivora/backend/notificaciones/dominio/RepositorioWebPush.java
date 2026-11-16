package pe.nivora.backend.notificaciones.dominio;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;

public interface RepositorioWebPush {
    Registro suscribir(CodigoSuministro suministro, UUID dispositivo, SuscripcionWebPush suscripcion, Instant ahora);

    void eliminar(CodigoSuministro suministro, UUID dispositivo);

    void encolar(Instant ahora);

    Optional<Reserva> reservar(Instant ahora);

    boolean vigente(Reserva reserva, Instant ahora);

    void finalizar(Reserva reserva, Estado estado, Instant proximoIntento, Instant ahora);

    enum Registro { GUARDADA, PREFERENCIA_INACTIVA, ENDPOINT_OCUPADO }

    enum Estado { PENDIENTE, ACEPTADA, FALLIDA, EXPIRADA }

    record Reserva(String idSuscripcion, String token, Notificacion.Clave aviso,
            int intentos, LocalDate vencimiento, SuscripcionWebPush suscripcion) {}

    class NoDisponible extends RuntimeException {
        public NoDisponible(Throwable causa) {
            super("No se pudo acceder a las suscripciones Web Push.", causa);
        }
    }
}
