package pe.nivora.backend.integracion.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.integracion.dominio.ConsultaIncidenciasIntegracion;
import pe.nivora.backend.integracion.dominio.IncidenciaIntegracion;
import pe.nivora.backend.integracion.dominio.IncidenciaIntegracion.Cursor;
import pe.nivora.backend.integracion.dominio.IncidenciaIntegracion.Estado;
import pe.nivora.backend.integracion.dominio.IncidenciaIntegracion.Origen;

@Repository
public class ConsultaIncidenciasIntegracionJdbc implements ConsultaIncidenciasIntegracion {
    private static final String INCIDENCIAS = """
        SELECT i.*
        FROM suministros s
        LEFT JOIN (
            SELECT 'RECHAZO_LECTURA' AS origen, fuente, TRIM(CAST(id_incidencia AS CHAR(40))) AS referencia,
                id_lectura, motivo, 'POR_REVISAR' AS estado,
                primera_recepcion_utc, ultima_recepcion_utc, repeticiones, NULL AS ultima_resolucion_utc
            FROM incidencias_lecturas
            WHERE codigo_suministro = ?
            UNION ALL
            SELECT 'RECUPERACION_LECTURAS', fuente, tipo, NULL, tipo,
                CASE WHEN abierta THEN 'ABIERTA' ELSE 'RESUELTA' END,
                primera_recepcion_utc, ultima_recepcion_utc, repeticiones, ultima_resolucion_utc
            FROM incidencias_recuperacion_lecturas
            WHERE codigo_suministro = ?
            UNION ALL
            SELECT 'SINCRONIZACION_RECIBOS', fuente, motivo, NULL, motivo,
                CASE WHEN abierta THEN 'ABIERTA' ELSE 'RESUELTA' END,
                primera_recepcion_utc, ultima_recepcion_utc, repeticiones, ultima_resolucion_utc
            FROM incidencias_sincronizacion_recibos
            WHERE codigo_suministro = ?
        ) i ON TRUE
        """;
    private static final String PAGINA_ANTERIOR = """
        AND (i.primera_recepcion_utc, i.origen, i.fuente, i.referencia) < (?, ?, ?, ?)
        """;
    private static final String ORDEN = """
        WHERE s.codigo = ?
        ORDER BY i.primera_recepcion_utc DESC, i.origen DESC, i.fuente DESC, i.referencia DESC
        LIMIT ?
        """;

    private final JdbcTemplate jdbc;

    public ConsultaIncidenciasIntegracionJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<List<IncidenciaIntegracion>> consultar(CodigoSuministro suministro, Cursor antesDe, int maximo) {
        try {
            String codigo = suministro.valor();
            List<IncidenciaIntegracion> filas;
            if (antesDe == null) {
                filas = jdbc.query(INCIDENCIAS + ORDEN, ConsultaIncidenciasIntegracionJdbc::leer,
                    codigo, codigo, codigo, codigo, maximo);
            } else {
                var fecha = LocalDateTime.ofInstant(antesDe.primeraRecepcion(), ZoneOffset.UTC);
                filas = jdbc.query(INCIDENCIAS + PAGINA_ANTERIOR + ORDEN, ConsultaIncidenciasIntegracionJdbc::leer,
                    codigo, codigo, codigo, fecha, antesDe.origen().name(), antesDe.fuente(), antesDe.referencia(),
                    codigo, maximo);
            }
            if (filas.isEmpty()) return Optional.empty();
            return Optional.of(filas.getFirst() == null ? List.of() : List.copyOf(filas));
        } catch (DataAccessException error) {
            throw new NoDisponible(error);
        }
    }

    private static IncidenciaIntegracion leer(ResultSet fila, int numero) throws SQLException {
        var origen = fila.getString("origen");
        if (origen == null) return null;
        return new IncidenciaIntegracion(Origen.valueOf(origen), fila.getString("fuente"),
            fila.getString("referencia"), fila.getString("id_lectura"), fila.getString("motivo"),
            Estado.valueOf(fila.getString("estado")), fecha(fila, "primera_recepcion_utc"),
            fecha(fila, "ultima_recepcion_utc"), fila.getLong("repeticiones"), fecha(fila, "ultima_resolucion_utc"));
    }

    private static Instant fecha(ResultSet fila, String columna) throws SQLException {
        var valor = fila.getObject(columna, LocalDateTime.class);
        return valor == null ? null : valor.toInstant(ZoneOffset.UTC);
    }
}
