package pe.nivora.backend.recibos.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.recibos.dominio.ConsultaSupervisionRecibos;
import pe.nivora.backend.recibos.dominio.RepositorioSincronizacionRecibos.Estado;
import pe.nivora.backend.recibos.dominio.SupervisionRecibos;
import pe.nivora.backend.recibos.dominio.SupervisionRecibos.Almacenamiento;
import pe.nivora.backend.recibos.dominio.SupervisionRecibos.Programacion;

@Repository
public class ConsultaSupervisionRecibosJdbc implements ConsultaSupervisionRecibos {
    private final JdbcTemplate jdbc;

    public ConsultaSupervisionRecibosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SupervisionRecibos> consultar(CodigoSuministro suministro, Instant ahora) {
        try {
            return jdbc.query("""
                SELECT r.cantidad, r.ultimo_periodo, r.ultima_recepcion,
                    a.codigo_suministro AS programado, a.ultimo_resultado, a.fallos_consecutivos,
                    a.ultimo_intento_utc, a.ultimo_exito_utc, a.proximo_intento_utc, a.reserva_hasta_utc
                FROM suministros s
                CROSS JOIN (
                    SELECT COUNT(*) AS cantidad, MAX(periodo) AS ultimo_periodo,
                        MAX(recibido_en_utc) AS ultima_recepcion
                    FROM recibos
                    WHERE codigo_suministro = ?
                ) r
                LEFT JOIN programacion_sincronizacion_recibos a
                    ON a.fuente = 'empresa-agua' AND a.codigo_suministro = s.codigo
                WHERE s.codigo = ?
                """, (fila, numero) -> {
                    var periodo = fila.getObject("ultimo_periodo", LocalDate.class);
                    var almacenamiento = new Almacenamiento(fila.getLong("cantidad"),
                        periodo == null ? null : YearMonth.from(periodo), fecha(fila, "ultima_recepcion"));
                    return new SupervisionRecibos(suministro, ahora, almacenamiento, programacion(fila, ahora));
                }, suministro.valor(), suministro.valor()).stream().findFirst();
        } catch (DataAccessException | IllegalArgumentException error) {
            throw new NoDisponible(error);
        }
    }

    private static Programacion programacion(ResultSet fila, Instant ahora) throws SQLException {
        if (fila.getString("programado") == null) return null;
        String resultado = fila.getString("ultimo_resultado");
        var reserva = fecha(fila, "reserva_hasta_utc");
        boolean vigente = reserva != null && reserva.isAfter(ahora);
        return new Programacion(resultado == null ? null : Estado.valueOf(resultado),
            fila.getInt("fallos_consecutivos"), fecha(fila, "ultimo_intento_utc"), fecha(fila, "ultimo_exito_utc"),
            fecha(fila, "proximo_intento_utc"), reserva, vigente, reserva != null && !vigente);
    }

    private static Instant fecha(ResultSet fila, String columna) throws SQLException {
        var guardada = fila.getObject(columna, LocalDateTime.class);
        return guardada == null ? null : guardada.toInstant(ZoneOffset.UTC);
    }
}
