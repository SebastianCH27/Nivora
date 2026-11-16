package pe.nivora.backend.notificaciones.infraestructura;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.TokenOpaco;
import pe.nivora.backend.notificaciones.dominio.Notificacion;
import pe.nivora.backend.notificaciones.dominio.RepositorioWebPush;
import pe.nivora.backend.notificaciones.dominio.SuscripcionWebPush;

import static pe.nivora.backend.compartido.infraestructura.sincronizacion.ProgramacionSincronizacionJdbc.utc;

@Repository
public class RepositorioWebPushJdbc implements RepositorioWebPush {
    private static final String ENTREGABLES = """
        FROM entregas_web_push e
        JOIN suscripciones_web_push s ON s.id_suscripcion = e.id_suscripcion
        JOIN preferencias_notificaciones_dispositivo p ON p.codigo_suministro = s.codigo_suministro
            AND p.id_dispositivo = s.id_dispositivo
        WHERE e.estado = 'PENDIENTE' AND s.expirada_en_utc IS NULL
            AND p.habilitadas = TRUE AND p.permiso = 'CONCEDIDO'
            AND e.aviso_registrado_en_utc >= p.actualizada_en_utc
        """;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;

    public RepositorioWebPushJdbc(JdbcTemplate jdbc, PlatformTransactionManager gestor) {
        this.jdbc = jdbc;
        transaccion = new TransactionTemplate(gestor);
        transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaccion.setTimeout(10);
    }

    @Override
    public Registro suscribir(CodigoSuministro suministro, UUID dispositivo, SuscripcionWebPush suscripcion, Instant ahora) {
        String codigo = suministro.valor();
        String id = dispositivo.toString();
        try {
            return transaccion.execute(estado -> {
                if (jdbc.queryForList("""
                    SELECT id_dispositivo FROM preferencias_notificaciones_dispositivo
                    WHERE codigo_suministro = ? AND id_dispositivo = ?
                        AND habilitadas = TRUE AND permiso = 'CONCEDIDO' FOR UPDATE
                    """, String.class, codigo, id).isEmpty()) {
                    return Registro.PREFERENCIA_INACTIVA;
                }
                if (!jdbc.queryForList("""
                    SELECT id_suscripcion FROM suscripciones_web_push
                    WHERE codigo_suministro = ? AND id_dispositivo = ? AND expirada_en_utc IS NULL
                        AND endpoint = ? AND p256dh = ? AND auth = ? FOR UPDATE
                    """, String.class, codigo, id, suscripcion.endpoint(), suscripcion.p256dh(), suscripcion.auth())
                        .isEmpty()) {
                    return Registro.GUARDADA;
                }
                eliminar(suministro, dispositivo);
                jdbc.update("""
                    INSERT INTO suscripciones_web_push (id_suscripcion, codigo_suministro, id_dispositivo,
                        huella_endpoint, endpoint, p256dh, auth, registrada_en_utc)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID().toString(), codigo, id, TokenOpaco.huellaDe(suscripcion.endpoint()),
                    suscripcion.endpoint(), suscripcion.p256dh(), suscripcion.auth(), utc(ahora));
                return Registro.GUARDADA;
            });
        } catch (DuplicateKeyException error) {
            return Registro.ENDPOINT_OCUPADO;
        } catch (DataAccessException | TransactionException error) {
            throw new NoDisponible(error);
        }
    }

    @Override
    public void eliminar(CodigoSuministro suministro, UUID dispositivo) {
        try {
            jdbc.update("DELETE FROM suscripciones_web_push WHERE codigo_suministro = ? AND id_dispositivo = ?",
                suministro.valor(), dispositivo.toString());
        } catch (DataAccessException error) {
            throw new NoDisponible(error);
        }
    }

    @Override
    public void encolar(Instant ahora) {
        jdbc.update("""
            INSERT INTO entregas_web_push
                (id_suscripcion, tipo, periodo, aviso_registrado_en_utc, proximo_intento_utc)
            SELECT s.id_suscripcion, n.tipo, n.periodo, n.registrada, ?
            FROM suscripciones_web_push s
            JOIN preferencias_notificaciones_dispositivo p ON p.codigo_suministro = s.codigo_suministro
                AND p.id_dispositivo = s.id_dispositivo
            JOIN (
                SELECT codigo_suministro, 'ALERTA_CONSUMO' AS tipo, periodo, detectada_en_utc AS registrada
                FROM alertas_consumo
                UNION ALL
                SELECT codigo_suministro, 'RECIBO_DISPONIBLE', periodo, registrado_en_utc
                FROM avisos_recibo
            ) n ON n.codigo_suministro = s.codigo_suministro
            WHERE s.expirada_en_utc IS NULL AND p.habilitadas = TRUE AND p.permiso = 'CONCEDIDO'
                AND n.registrada >= s.registrada_en_utc AND n.registrada >= p.actualizada_en_utc
                AND NOT EXISTS (
                    SELECT 1 FROM entregas_web_push e WHERE e.id_suscripcion = s.id_suscripcion
                        AND e.tipo = n.tipo AND e.periodo = n.periodo
                )
            ORDER BY n.registrada, s.id_suscripcion, n.tipo, n.periodo
            LIMIT 100
            ON DUPLICATE KEY UPDATE intentos = entregas_web_push.intentos
            """, utc(ahora));
    }

    @Override
    public Optional<Reserva> reservar(Instant ahora) {
        var fecha = utc(ahora);
        String token = UUID.randomUUID().toString();
        return transaccion.execute(estado -> {
            var tareas = jdbc.query("""
                SELECT e.id_suscripcion, e.tipo, e.periodo, e.intentos, s.endpoint, s.p256dh, s.auth,
                    (SELECT r.fecha_vencimiento FROM recibos r
                        WHERE r.codigo_suministro = s.codigo_suministro AND r.periodo = e.periodo
                            AND e.tipo = 'RECIBO_DISPONIBLE') AS fecha_vencimiento
                """ + ENTREGABLES + """
                    AND e.proximo_intento_utc <= ?
                    AND (e.reserva_hasta_utc IS NULL OR e.reserva_hasta_utc <= ?)
                ORDER BY e.proximo_intento_utc, e.id_suscripcion, e.tipo, e.periodo LIMIT 1 FOR UPDATE
                """, (fila, indice) -> {
                    var clave = new Notificacion.Clave(Notificacion.Tipo.valueOf(fila.getString("tipo")),
                        YearMonth.from(fila.getObject("periodo", LocalDate.class)));
                    return new Reserva(fila.getString("id_suscripcion"), token, clave, fila.getInt("intentos"),
                        fila.getObject("fecha_vencimiento", LocalDate.class),
                        new SuscripcionWebPush(fila.getString("endpoint"), fila.getString("p256dh"), fila.getString("auth")));
                }, fecha, fecha);
            if (tareas.isEmpty()) return Optional.empty();
            var tarea = tareas.getFirst();
            jdbc.update("""
                UPDATE entregas_web_push SET reserva_token = ?, reserva_hasta_utc = ?
                WHERE id_suscripcion = ? AND tipo = ? AND periodo = ?
                """, token, fecha.plusSeconds(60), tarea.idSuscripcion(), tarea.aviso().tipo().name(),
                tarea.aviso().periodo().atDay(1));
            return Optional.of(tarea);
        });
    }

    @Override
    public boolean vigente(Reserva reserva, Instant ahora) {
        return !jdbc.queryForList("SELECT e.id_suscripcion\n" + ENTREGABLES + """
                AND e.id_suscripcion = ? AND e.tipo = ? AND e.periodo = ?
                AND e.reserva_token = ? AND e.reserva_hasta_utc > ?
            """, String.class, reserva.idSuscripcion(), reserva.aviso().tipo().name(),
            reserva.aviso().periodo().atDay(1), reserva.token(), utc(ahora)).isEmpty();
    }

    @Override
    public void finalizar(Reserva reserva, Estado estado, Instant proximoIntento, Instant ahora) {
        transaccion.executeWithoutResult(transaccion -> {
            int filas = jdbc.update("""
                UPDATE entregas_web_push SET estado = ?, intentos = intentos + 1, proximo_intento_utc = ?,
                    reserva_token = NULL, reserva_hasta_utc = NULL
                WHERE id_suscripcion = ? AND tipo = ? AND periodo = ?
                    AND reserva_token = ? AND reserva_hasta_utc > ?
                """, estado.name(), utc(proximoIntento), reserva.idSuscripcion(), reserva.aviso().tipo().name(),
                reserva.aviso().periodo().atDay(1), reserva.token(), utc(ahora));
            if (filas == 1 && estado == Estado.EXPIRADA) {
                jdbc.update("UPDATE suscripciones_web_push SET expirada_en_utc = ? WHERE id_suscripcion = ?",
                    utc(ahora), reserva.idSuscripcion());
                jdbc.update("""
                    UPDATE entregas_web_push SET estado = 'EXPIRADA', reserva_token = NULL, reserva_hasta_utc = NULL
                    WHERE id_suscripcion = ? AND estado = 'PENDIENTE'
                    """, reserva.idSuscripcion());
            }
        });
    }
}
