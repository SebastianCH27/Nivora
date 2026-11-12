package pe.nivora.backend.notificaciones.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.notificaciones.dominio.Notificacion;
import pe.nivora.backend.notificaciones.dominio.RepositorioBandejaNotificaciones;

@Repository
public class RepositorioBandejaNotificacionesJdbc implements RepositorioBandejaNotificaciones {
    private static final String NOTIFICACIONES = """
        SELECT n.*, l.leida_en_utc
        FROM (
            SELECT 'ALERTA_CONSUMO' AS tipo, a.periodo, a.detectada_en_utc AS registrada_en_utc,
                   a.consumo_actual_litros, a.consumo_anterior_litros,
                   NULL AS numero_recibo, NULL AS fecha_disponibilidad, NULL AS fecha_vencimiento
            FROM alertas_consumo a
            WHERE a.codigo_suministro = :codigo
            UNION ALL
            SELECT 'RECIBO_DISPONIBLE', a.periodo, a.registrado_en_utc,
                   NULL, NULL, r.numero_recibo, r.fecha_disponibilidad, r.fecha_vencimiento
            FROM avisos_recibo a
            JOIN recibos r ON r.codigo_suministro = a.codigo_suministro AND r.periodo = a.periodo
            WHERE a.codigo_suministro = :codigo
        ) n
        LEFT JOIN notificaciones_leidas l ON l.codigo_suministro = :codigo
            AND l.tipo = n.tipo AND l.periodo = n.periodo
        WHERE 1 = 1
        """;
    private static final String ANTES_DE = """
          AND (n.registrada_en_utc < :fecha OR (n.registrada_en_utc = :fecha
            AND (n.tipo < :tipo OR (n.tipo = :tipo AND n.periodo < :periodo))))
        """;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate consultas;

    public RepositorioBandejaNotificacionesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.consultas = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public List<Notificacion> buscar(CodigoSuministro suministro, Notificacion.Cursor antesDe,
            boolean soloPendientes, int limite) {
        var parametros = new MapSqlParameterSource("codigo", suministro.valor()).addValue("limite", limite);
        var sql = new StringBuilder(NOTIFICACIONES);
        if (soloPendientes) sql.append(" AND l.leida_en_utc IS NULL\n");
        if (antesDe != null) {
            sql.append(ANTES_DE);
            parametros.addValue("fecha", utc(antesDe.registradaEn()))
                .addValue("tipo", antesDe.clave().tipo().name())
                .addValue("periodo", antesDe.clave().periodo().atDay(1));
        }
        sql.append(" ORDER BY n.registrada_en_utc DESC, n.tipo DESC, n.periodo DESC LIMIT :limite");
        try {
            return consultas.query(sql.toString(), parametros, (fila, indice) -> leer(fila));
        } catch (DataAccessException error) {
            throw new NoDisponible(error);
        }
    }

    @Override
    public boolean marcarLeida(CodigoSuministro suministro, Notificacion.Clave clave, Instant leidaEn) {
        String tabla = switch (clave.tipo()) {
            case ALERTA_CONSUMO -> "alertas_consumo";
            case RECIBO_DISPONIBLE -> "avisos_recibo";
        };
        try {
            return jdbc.update("""
                INSERT INTO notificaciones_leidas (codigo_suministro, tipo, periodo, leida_en_utc)
                SELECT codigo_suministro, ?, periodo, ?
                FROM %s WHERE codigo_suministro = ? AND periodo = ?
                """.formatted(tabla), clave.tipo().name(), utc(leidaEn),
                suministro.valor(), clave.periodo().atDay(1)) == 1;
        } catch (DuplicateKeyException error) {
            return true;
        } catch (DataAccessException error) {
            throw new NoDisponible(error);
        }
    }

    private static Notificacion leer(ResultSet fila) throws SQLException {
        var tipo = Notificacion.Tipo.valueOf(fila.getString("tipo"));
        var clave = new Notificacion.Clave(tipo, YearMonth.from(fila.getObject("periodo", LocalDate.class)));
        var leida = fila.getObject("leida_en_utc", LocalDateTime.class);
        var consumo = tipo == Notificacion.Tipo.ALERTA_CONSUMO
            ? new Notificacion.Consumo(fila.getLong("consumo_actual_litros"), fila.getLong("consumo_anterior_litros"))
            : null;
        var recibo = tipo == Notificacion.Tipo.RECIBO_DISPONIBLE
            ? new Notificacion.Recibo(fila.getString("numero_recibo"),
                fila.getObject("fecha_disponibilidad", LocalDate.class), fila.getObject("fecha_vencimiento", LocalDate.class))
            : null;
        return new Notificacion(clave, fila.getObject("registrada_en_utc", LocalDateTime.class).toInstant(ZoneOffset.UTC),
            leida == null ? null : leida.toInstant(ZoneOffset.UTC), consumo, recibo);
    }

    private static LocalDateTime utc(Instant instante) {
        return LocalDateTime.ofInstant(instante.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
    }
}
