package pe.nivora.backend.notificaciones.infraestructura;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.administradores.UsuarioAdministrador;
import pe.nivora.backend.notificaciones.dominio.RepositorioConfiguracionNotificaciones;

@Repository
public class RepositorioConfiguracionNotificacionesJdbc implements RepositorioConfiguracionNotificaciones {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;

    public RepositorioConfiguracionNotificacionesJdbc(JdbcTemplate jdbc, PlatformTransactionManager gestor) {
        this.jdbc = jdbc;
        this.transaccion = new TransactionTemplate(gestor);
    }

    @Override
    public Optional<Preferencia> buscarPreferencia(CodigoSuministro suministro, UUID dispositivo) {
        try {
            return jdbc.query("""
                SELECT permiso, habilitadas, actualizada_en_utc
                FROM preferencias_notificaciones_dispositivo
                WHERE codigo_suministro = ? AND id_dispositivo = ?
                """, (fila, indice) -> new Preferencia(dispositivo, Permiso.valueOf(fila.getString("permiso")),
                    fila.getBoolean("habilitadas"), fila.getObject("actualizada_en_utc", LocalDateTime.class)
                        .toInstant(ZoneOffset.UTC)), suministro.valor(), dispositivo.toString()).stream().findFirst();
        } catch (DataAccessException error) {
            throw new NoDisponible(error);
        }
    }

    @Override
    public Preferencia guardarPreferencia(CodigoSuministro suministro, Preferencia preferencia) {
        var permiso = preferencia.permiso().name();
        var habilitadas = preferencia.habilitadas();
        var actualizada = utc(preferencia.actualizadaEn());
        try {
            return transaccion.execute(estado -> {
                jdbc.update("""
                    INSERT INTO preferencias_notificaciones_dispositivo
                        (codigo_suministro, id_dispositivo, permiso, habilitadas, actualizada_en_utc)
                    VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        actualizada_en_utc = CASE WHEN permiso <> ? OR habilitadas <> ? THEN ? ELSE actualizada_en_utc END,
                        permiso = ?, habilitadas = ?
                    """, suministro.valor(), preferencia.idDispositivo().toString(), permiso, habilitadas, actualizada,
                    permiso, habilitadas, actualizada, permiso, habilitadas);
                return buscarPreferencia(suministro, preferencia.idDispositivo()).orElseThrow();
            });
        } catch (DataAccessException | TransactionException error) {
            throw new NoDisponible(error);
        }
    }

    @Override
    public Optional<ControlAlertas> buscarControlAlertas() {
        try {
            return jdbc.query("""
                SELECT habilitada, actualizada_por, actualizada_en_utc
                FROM configuracion_alertas_consumo WHERE id_configuracion = 1
                """, (fila, indice) -> new ControlAlertas(fila.getBoolean("habilitada"),
                    fila.getString("actualizada_por"), fila.getObject("actualizada_en_utc", LocalDateTime.class)
                        .toInstant(ZoneOffset.UTC))).stream().findFirst();
        } catch (DataAccessException error) {
            throw new NoDisponible(error);
        }
    }

    @Override
    public ControlAlertas guardarControlAlertas(boolean habilitada, UsuarioAdministrador actor, Instant ahora) {
        var actualizada = utc(ahora);
        try {
            return transaccion.execute(estado -> {
                jdbc.update("""
                    INSERT INTO configuracion_alertas_consumo
                        (id_configuracion, habilitada, actualizada_por, actualizada_en_utc)
                    VALUES (1, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        actualizada_por = CASE WHEN habilitada <> ? THEN ? ELSE actualizada_por END,
                        actualizada_en_utc = CASE WHEN habilitada <> ? THEN ? ELSE actualizada_en_utc END,
                        habilitada = ?
                    """, habilitada, actor.valor(), actualizada, habilitada, actor.valor(),
                    habilitada, actualizada, habilitada);
                return buscarControlAlertas().orElseThrow();
            });
        } catch (DataAccessException | TransactionException error) {
            throw new NoDisponible(error);
        }
    }

    private static LocalDateTime utc(Instant instante) {
        return LocalDateTime.ofInstant(instante.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
    }
}
