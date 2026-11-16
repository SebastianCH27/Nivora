package pe.nivora.backend.acceso.infraestructura.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.Suministro;
import pe.nivora.backend.acceso.dominio.SuministroVerificado;
import pe.nivora.backend.acceso.dominio.contratos.ConsultaRegistrosAcceso;

@Repository
public class ConsultaRegistrosAccesoJdbc implements ConsultaRegistrosAcceso {
    private static final String SUMINISTROS = """
        SELECT codigo, titular, verificado_en_utc
        FROM suministros
        """;
    private static final String ACCESOS = """
        SELECT c.id_sesion, RIGHT(c.dni_utilizado, 2) AS dni_final,
               c.creada_en_utc, c.expira_en_utc, c.revocada_en_utc
        FROM suministros s
        LEFT JOIN sesiones_consulta c ON c.codigo_suministro = s.codigo
        """;
    private static final String FILTRO_ACCESOS = """
            AND (c.creada_en_utc < ? OR (c.creada_en_utc = ? AND c.id_sesion < ?))
        """;
    private static final String ORDEN_ACCESOS = """
        WHERE s.codigo = ?
        ORDER BY c.creada_en_utc DESC, c.id_sesion DESC
        LIMIT ?
        """;

    private final JdbcTemplate jdbc;

    public ConsultaRegistrosAccesoJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SuministroVerificado> suministros(CodigoSuministro despuesDe, int limite) {
        String orden = "ORDER BY codigo LIMIT ?";
        try {
            if (despuesDe == null) {
                return jdbc.query(SUMINISTROS + orden, ConsultaRegistrosAccesoJdbc::leerSuministro, limite);
            }
            return jdbc.query(SUMINISTROS + "WHERE codigo > ?\n" + orden,
                ConsultaRegistrosAccesoJdbc::leerSuministro, despuesDe.valor(), limite);
        } catch (DataAccessException error) {
            throw new NoDisponible(error);
        }
    }

    @Override
    public Optional<List<Acceso>> accesos(CodigoSuministro suministro, Cursor antesDe, int limite) {
        try {
            List<Acceso> filas;
            if (antesDe == null) {
                filas = jdbc.query(ACCESOS + ORDEN_ACCESOS, ConsultaRegistrosAccesoJdbc::leerAcceso, suministro.valor(), limite);
            } else {
                var fecha = LocalDateTime.ofInstant(antesDe.creadaEn(), ZoneOffset.UTC);
                filas = jdbc.query(ACCESOS + FILTRO_ACCESOS + ORDEN_ACCESOS,
                    ConsultaRegistrosAccesoJdbc::leerAcceso, fecha, fecha, antesDe.id().toString(), suministro.valor(), limite);
            }
            if (filas.isEmpty()) return Optional.empty();
            return Optional.of(filas.getFirst() == null ? List.of() : List.copyOf(filas));
        } catch (DataAccessException error) {
            throw new NoDisponible(error);
        }
    }

    private static SuministroVerificado leerSuministro(ResultSet fila, int numero) throws SQLException {
        return new SuministroVerificado(new Suministro(new CodigoSuministro(fila.getString("codigo")),
            fila.getString("titular")), fila.getObject("verificado_en_utc", LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }

    private static Acceso leerAcceso(ResultSet fila, int numero) throws SQLException {
        var id = fila.getString("id_sesion");
        if (id == null) return null;
        var creada = fila.getObject("creada_en_utc", LocalDateTime.class).toInstant(ZoneOffset.UTC);
        var expira = fila.getObject("expira_en_utc", LocalDateTime.class).toInstant(ZoneOffset.UTC);
        var revocada = fila.getObject("revocada_en_utc", LocalDateTime.class);
        return new Acceso(new Cursor(creada, UUID.fromString(id)), "******" + fila.getString("dni_final"),
            expira, revocada == null ? null : revocada.toInstant(ZoneOffset.UTC));
    }
}
