package pe.nivora.backend.lecturas.soporte;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import tools.jackson.databind.json.JsonMapper;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.LecturaAcumulada;
import pe.nivora.backend.lecturas.dominio.LoteLecturas;
import pe.nivora.backend.lecturas.infraestructura.persistencia.RepositorioLecturasJdbc;
import pe.nivora.backend.lecturas.infraestructura.persistencia.RepositorioRecuperacionLecturasJdbc;

public final class BaseDatosRecuperacion implements AutoCloseable {
    public static final CodigoSuministro CODIGO = new CodigoSuministro("13100390");
    public static final Instant AHORA = Instant.parse("2026-11-09T20:00:00.123456Z");
    public final JdbcTemplate jdbc;
    public final RepositorioLecturasJdbc lecturas;
    public final RepositorioRecuperacionLecturasJdbc progreso;
    private final DataSourceTransactionManager gestor;
    private final Connection conexionEsquema;

    public BaseDatosRecuperacion() {
        var fuente = new DriverManagerDataSource("jdbc:h2:mem:recuperacion_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
            + ";INIT=CREATE SCHEMA IF NOT EXISTS nivora\\;SET SCHEMA nivora", "sa", "");
        jdbc = new JdbcTemplate(fuente);
        try {
            // Mantener la sesion de creacion: H2 la usa al evaluar sus restricciones IN.
            conexionEsquema = fuente.getConnection();
            new ResourceDatabasePopulator(new FileSystemResource("base-datos/02-suministros.sql"),
                new FileSystemResource("base-datos/03-lecturas.sql"),
                new FileSystemResource("base-datos/04-recuperacion-lecturas.sql"),
                new FileSystemResource("base-datos/05-contexto-recuperacion-lecturas.sql")).populate(conexionEsquema);
        } catch (SQLException ex) {
            throw new IllegalStateException("No se pudo preparar la base de pruebas.", ex);
        }
        jdbc.update("INSERT INTO suministros VALUES (?, ?, ?)", CODIGO.valor(), "Titular ficticio",
            LocalDateTime.parse("2026-11-09T19:00:00"));
        gestor = new DataSourceTransactionManager(fuente);
        lecturas = new RepositorioLecturasJdbc(jdbc, gestor, JsonMapper.builder().build());
        progreso = nuevaInstanciaProgreso();
    }

    public RepositorioRecuperacionLecturasJdbc nuevaInstanciaProgreso() {
        return new RepositorioRecuperacionLecturasJdbc(jdbc, gestor, lecturas);
    }

    public static LecturaAcumulada lectura(long id) {
        return new LecturaAcumulada("empresa-agua", Long.toString(id), CODIGO, "391",
            Instant.parse("2026-10-01T05:00:00Z").plusSeconds(id), 100 + id);
    }

    public static LoteLecturas pagina(boolean hayMas, long... ids) {
        List<LecturaAcumulada> filas = Arrays.stream(ids).mapToObj(BaseDatosRecuperacion::lectura).toList();
        return new LoteLecturas("empresa-agua", CODIGO, filas,
            ids.length == 0 ? "0" : Long.toString(ids[ids.length - 1]), hayMas);
    }

    @Override
    public void close() {
        try {
            conexionEsquema.close();
        } catch (SQLException ex) {
            throw new IllegalStateException("No se pudo cerrar la sesion de pruebas.", ex);
        } finally {
            jdbc.execute("SHUTDOWN");
        }
    }
}
