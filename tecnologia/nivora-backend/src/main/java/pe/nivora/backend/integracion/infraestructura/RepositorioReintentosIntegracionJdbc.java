package pe.nivora.backend.integracion.infraestructura;

import static pe.nivora.backend.compartido.infraestructura.sincronizacion.ProgramacionSincronizacionJdbc.utc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.integracion.dominio.RepositorioReintentosIntegracion;

@Repository
public class RepositorioReintentosIntegracionJdbc implements RepositorioReintentosIntegracion {
    private static final String FUENTE = "empresa-agua";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;

    public RepositorioReintentosIntegracionJdbc(JdbcTemplate jdbc, PlatformTransactionManager gestor) {
        this.jdbc = jdbc;
        this.transaccion = new TransactionTemplate(gestor);
        this.transaccion.setTimeout(10);
    }

    @Override
    public Solicitud programar(CodigoSuministro suministro, Proceso proceso, Instant ahora) {
        String codigo = suministro.valor();
        String tabla = switch (proceso) {
            case LECTURAS -> "programacion_sincronizacion_lecturas";
            case RECIBOS -> "programacion_sincronizacion_recibos";
        };
        var fecha = utc(ahora);
        try {
            return transaccion.execute(estado -> {
                // El upsert crea la programacion si falta y coordina con las reservas del trabajador.
                jdbc.update("""
                    INSERT INTO %s (fuente, codigo_suministro, proximo_intento_utc)
                    SELECT ?, codigo, ? FROM suministros WHERE codigo = ?
                    ON DUPLICATE KEY UPDATE fuente = ?
                    """.formatted(tabla), FUENTE, fecha, codigo, FUENTE);
                var filas = jdbc.query("""
                    SELECT proximo_intento_utc, reserva_hasta_utc
                    FROM %s WHERE fuente = ? AND codigo_suministro = ? FOR UPDATE
                    """.formatted(tabla), (fila, numero) -> new Calendario(
                        fila.getObject("proximo_intento_utc", LocalDateTime.class),
                        fila.getObject("reserva_hasta_utc", LocalDateTime.class)), FUENTE, codigo);
                if (filas.isEmpty()) return new Solicitud(Estado.SUMINISTRO_NO_REGISTRADO, null);
                var calendario = filas.getFirst();
                if (calendario.reservaHasta() != null && calendario.reservaHasta().isAfter(fecha)) {
                    return new Solicitud(Estado.EN_CURSO, null);
                }
                if (proceso == Proceso.LECTURAS && recorridoBloqueado(codigo)) {
                    estado.setRollbackOnly();
                    return new Solicitud(Estado.BLOQUEADO, null);
                }
                var proximo = calendario.proximoIntento();
                if (proximo != null && !proximo.isAfter(fecha)) {
                    return new Solicitud(Estado.PROGRAMADO, proximo.toInstant(ZoneOffset.UTC));
                }
                jdbc.update("""
                    UPDATE %s SET proximo_intento_utc = ? WHERE fuente = ? AND codigo_suministro = ?
                    """.formatted(tabla), fecha, FUENTE, codigo);
                return new Solicitud(Estado.PROGRAMADO, ahora);
            });
        } catch (DataAccessException | TransactionException error) {
            throw new NoDisponible(error);
        }
    }

    private boolean recorridoBloqueado(String codigo) {
        return !jdbc.queryForList("""
            SELECT codigo_suministro FROM progreso_recuperacion_lecturas
            WHERE fuente = ? AND codigo_suministro = ? AND estado = 'BLOQUEADO'
            """, String.class, FUENTE, codigo).isEmpty();
    }

    private record Calendario(LocalDateTime proximoIntento, LocalDateTime reservaHasta) {}
}
