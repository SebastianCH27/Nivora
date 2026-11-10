package pe.nivora.backend.lecturas.infraestructura.persistencia;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.EstadoSincronizacion;
import pe.nivora.backend.lecturas.dominio.ReservaSincronizacion;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioSincronizacion;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioSincronizacionNoDisponibleException;

@Repository
public class RepositorioSincronizacionJdbc implements RepositorioSincronizacion {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;

    public RepositorioSincronizacionJdbc(JdbcTemplate jdbc, PlatformTransactionManager gestor) {
        this.jdbc = jdbc;
        this.transaccion = new TransactionTemplate(gestor);
        this.transaccion.setTimeout(10);
    }

    @Override
    public void prepararSuministros(String fuente, Instant ahora) {
        atomico(() -> {
            jdbc.update("""
                INSERT INTO programacion_sincronizacion_lecturas
                    (fuente, codigo_suministro, proximo_intento_utc)
                SELECT ?, s.codigo, ? FROM suministros s
                LEFT JOIN programacion_sincronizacion_lecturas q
                    ON q.fuente = ? AND q.codigo_suministro = s.codigo
                WHERE q.codigo_suministro IS NULL
                ON DUPLICATE KEY UPDATE fuente = ?
                """, fuente, utc(ahora), fuente, fuente);
            // Un reinicio manual de un recorrido bloqueado permite volver a programarlo.
            jdbc.update("""
                UPDATE programacion_sincronizacion_lecturas q
                SET proximo_intento_utc = ?, fallos_consecutivos = 0, ultimo_resultado = NULL
                WHERE fuente = ? AND proximo_intento_utc IS NULL
                    AND ultimo_resultado = 'BLOQUEADO' AND reserva_token IS NULL
                    AND EXISTS (SELECT 1 FROM progreso_recuperacion_lecturas p
                        WHERE p.fuente = q.fuente AND p.codigo_suministro = q.codigo_suministro
                            AND p.estado <> 'BLOQUEADO')
                """, utc(ahora), fuente);
            return null;
        });
    }

    @Override
    public Optional<ReservaSincronizacion> reservar(String fuente, Instant elegiblesHasta,
            Instant ahora, Duration duracion) {
        if (duracion.isNegative() || duracion.isZero()) throw new IllegalArgumentException("Reserva invalida.");
        return atomico(() -> {
            var pendientes = jdbc.query("""
                SELECT codigo_suministro, fallos_consecutivos
                FROM programacion_sincronizacion_lecturas
                WHERE fuente = ? AND proximo_intento_utc <= ?
                    AND (reserva_token IS NULL OR reserva_hasta_utc <= ?)
                ORDER BY proximo_intento_utc, codigo_suministro LIMIT 1 FOR UPDATE
                """, (fila, numero) -> new Pendiente(fila.getString(1), fila.getInt(2)),
                fuente, utc(elegiblesHasta), utc(ahora));
            if (pendientes.isEmpty()) return Optional.empty();
            var tarea = pendientes.getFirst();
            String token = UUID.randomUUID().toString();
            Instant vence = ahora.truncatedTo(ChronoUnit.MICROS).plus(duracion);
            int filas = jdbc.update("""
                UPDATE programacion_sincronizacion_lecturas
                SET reserva_token = ?, reserva_hasta_utc = ?, ultimo_intento_utc = ?
                WHERE fuente = ? AND codigo_suministro = ? AND proximo_intento_utc <= ?
                    AND (reserva_token IS NULL OR reserva_hasta_utc <= ?)
                """, token, utc(vence), utc(ahora), fuente, tarea.codigo(),
                utc(elegiblesHasta), utc(ahora));
            return filas == 1 ? Optional.of(new ReservaSincronizacion(fuente,
                new CodigoSuministro(tarea.codigo()), token, vence, tarea.fallos())) : Optional.empty();
        });
    }

    @Override
    public boolean finalizar(ReservaSincronizacion reserva, EstadoSincronizacion estado,
            int fallos, Instant proximoIntento, Instant ahora) {
        if (fallos < 0) throw new IllegalArgumentException("Fallos invalidos.");
        if ((estado == EstadoSincronizacion.BLOQUEADO) != (proximoIntento == null)) {
            throw new IllegalArgumentException("Solo un bloqueo pausa la programacion.");
        }
        return atomico(() -> jdbc.update("""
            UPDATE programacion_sincronizacion_lecturas
            SET proximo_intento_utc = ?, fallos_consecutivos = ?,
                reserva_token = NULL, reserva_hasta_utc = NULL, ultimo_resultado = ?,
                ultimo_exito_utc = CASE WHEN ? THEN ? ELSE ultimo_exito_utc END
            WHERE fuente = ? AND codigo_suministro = ? AND reserva_token = ?
                AND reserva_hasta_utc > ?
            """, utc(proximoIntento), fallos, estado.name(),
            estado == EstadoSincronizacion.RECORRIDO_COMPLETO, utc(ahora),
            reserva.fuente(), reserva.suministro().valor(), reserva.token(), utc(ahora)) == 1);
    }

    private <T> T atomico(Supplier<T> operacion) {
        try {
            return transaccion.execute(estado -> operacion.get());
        } catch (DataAccessException | TransactionException error) {
            throw new RepositorioSincronizacionNoDisponibleException(
                "No se pudo acceder a la programacion de lecturas.", error);
        }
    }

    private static LocalDateTime utc(Instant fecha) {
        return fecha == null ? null : LocalDateTime.ofInstant(
            fecha.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
    }
    private record Pendiente(String codigo, int fallos) {}
}
