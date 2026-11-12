package pe.nivora.backend.compartido.infraestructura.sincronizacion;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Operaciones de reserva comunes a las programaciones persistidas. */
public final class ProgramacionSincronizacionJdbc {
    public enum Tabla {
        LECTURAS("programacion_sincronizacion_lecturas"),
        RECIBOS("programacion_sincronizacion_recibos");

        private final String nombre;

        Tabla(String nombre) {
            this.nombre = nombre;
        }
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final String tabla;

    public ProgramacionSincronizacionJdbc(JdbcTemplate jdbc, PlatformTransactionManager gestor, Tabla tabla) {
        this.jdbc = jdbc;
        this.tabla = tabla.nombre;
        this.transaccion = new TransactionTemplate(gestor);
        this.transaccion.setTimeout(10);
    }

    public void prepararSuministros(String fuente, Instant ahora) {
        jdbc.update("""
            INSERT INTO %s (fuente, codigo_suministro, proximo_intento_utc)
            SELECT ?, s.codigo, ? FROM suministros s
            LEFT JOIN %s q ON q.fuente = ? AND q.codigo_suministro = s.codigo
            WHERE q.codigo_suministro IS NULL
            ON DUPLICATE KEY UPDATE fuente = ?
            """.formatted(tabla, tabla), fuente, utc(ahora), fuente, fuente);
    }

    public Optional<Reserva> reservar(String fuente, Instant elegiblesHasta, Instant ahora, Duration duracion) {
        if (duracion.isNegative() || duracion.isZero()) throw new IllegalArgumentException("Reserva invalida.");
        return transaccion.execute(estado -> {
            var pendientes = jdbc.query("""
                SELECT codigo_suministro, fallos_consecutivos FROM %s
                WHERE fuente = ? AND proximo_intento_utc <= ?
                    AND (reserva_token IS NULL OR reserva_hasta_utc <= ?)
                ORDER BY proximo_intento_utc, codigo_suministro LIMIT 1 FOR UPDATE
                """.formatted(tabla), (fila, indice) -> new Pendiente(fila.getString(1), fila.getInt(2)),
                fuente, utc(elegiblesHasta), utc(ahora));
            if (pendientes.isEmpty()) return Optional.empty();

            var tarea = pendientes.getFirst();
            String token = UUID.randomUUID().toString();
            Instant vence = ahora.truncatedTo(ChronoUnit.MICROS).plus(duracion);
            int filas = jdbc.update("""
                UPDATE %s SET reserva_token = ?, reserva_hasta_utc = ?, ultimo_intento_utc = ?
                WHERE fuente = ? AND codigo_suministro = ? AND proximo_intento_utc <= ?
                    AND (reserva_token IS NULL OR reserva_hasta_utc <= ?)
                """.formatted(tabla), token, utc(vence), utc(ahora), fuente, tarea.codigo(),
                utc(elegiblesHasta), utc(ahora));
            return filas == 1 ? Optional.of(new Reserva(tarea.codigo(), token, vence, tarea.fallos()))
                : Optional.empty();
        });
    }

    public boolean finalizar(String fuente, String codigo, String token, String resultado, boolean exito,
            int fallos, Instant proximoIntento, Instant ahora) {
        if (fallos < 0) throw new IllegalArgumentException("Fallos invalidos.");
        return transaccion.execute(estado -> jdbc.update("""
            UPDATE %s
            SET proximo_intento_utc = ?, fallos_consecutivos = ?,
                reserva_token = NULL, reserva_hasta_utc = NULL, ultimo_resultado = ?,
                ultimo_exito_utc = CASE WHEN ? THEN ? ELSE ultimo_exito_utc END
            WHERE fuente = ? AND codigo_suministro = ? AND reserva_token = ?
                AND reserva_hasta_utc > ?
            """.formatted(tabla), utc(proximoIntento), fallos, resultado, exito, utc(ahora),
            fuente, codigo, token, utc(ahora)) == 1);
    }

    public static LocalDateTime utc(Instant instante) {
        return instante == null ? null : LocalDateTime.ofInstant(instante.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
    }

    public record Reserva(String codigo, String token, Instant vence, int fallos) {}

    private record Pendiente(String codigo, int fallos) {}
}
