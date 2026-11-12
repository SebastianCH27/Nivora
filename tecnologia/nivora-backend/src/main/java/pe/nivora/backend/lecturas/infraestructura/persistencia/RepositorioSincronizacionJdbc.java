package pe.nivora.backend.lecturas.infraestructura.persistencia;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.compartido.infraestructura.sincronizacion.ProgramacionSincronizacionJdbc;
import pe.nivora.backend.lecturas.dominio.EstadoSincronizacion;
import pe.nivora.backend.lecturas.dominio.ReservaSincronizacion;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioSincronizacion;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioSincronizacionNoDisponibleException;

import static pe.nivora.backend.compartido.infraestructura.sincronizacion.ProgramacionSincronizacionJdbc.utc;

@Repository
public class RepositorioSincronizacionJdbc implements RepositorioSincronizacion {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final ProgramacionSincronizacionJdbc programacion;

    public RepositorioSincronizacionJdbc(JdbcTemplate jdbc, PlatformTransactionManager gestor) {
        this.jdbc = jdbc;
        this.transaccion = new TransactionTemplate(gestor);
        this.transaccion.setTimeout(10);
        this.programacion = new ProgramacionSincronizacionJdbc(jdbc, gestor,
            ProgramacionSincronizacionJdbc.Tabla.LECTURAS);
    }

    @Override
    public void prepararSuministros(String fuente, Instant ahora) {
        acceder(() -> transaccion.execute(estado -> {
            programacion.prepararSuministros(fuente, ahora);
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
        }));
    }

    @Override
    public Optional<ReservaSincronizacion> reservar(String fuente, Instant elegiblesHasta,
            Instant ahora, Duration duracion) {
        return acceder(() -> programacion.reservar(fuente, elegiblesHasta, ahora, duracion)
            .map(reserva -> new ReservaSincronizacion(fuente, new CodigoSuministro(reserva.codigo()),
                reserva.token(), reserva.vence(), reserva.fallos())));
    }

    @Override
    public boolean finalizar(ReservaSincronizacion reserva, EstadoSincronizacion estado,
            int fallos, Instant proximoIntento, Instant ahora) {
        if (fallos < 0) throw new IllegalArgumentException("Fallos invalidos.");
        if ((estado == EstadoSincronizacion.BLOQUEADO) != (proximoIntento == null)) {
            throw new IllegalArgumentException("Solo un bloqueo pausa la programacion.");
        }
        return acceder(() -> programacion.finalizar(reserva.fuente(), reserva.suministro().valor(),
            reserva.token(), estado.name(), estado == EstadoSincronizacion.RECORRIDO_COMPLETO,
            fallos, proximoIntento, ahora));
    }

    private <T> T acceder(Supplier<T> operacion) {
        try {
            return operacion.get();
        } catch (DataAccessException | TransactionException error) {
            throw new RepositorioSincronizacionNoDisponibleException(
                "No se pudo acceder a la programacion de lecturas.", error);
        }
    }
}
