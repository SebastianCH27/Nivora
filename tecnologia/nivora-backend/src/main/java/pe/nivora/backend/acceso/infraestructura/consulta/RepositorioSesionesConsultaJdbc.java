package pe.nivora.backend.acceso.infraestructura.consulta;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
import pe.nivora.backend.acceso.dominio.IdentidadConsultada;
import pe.nivora.backend.acceso.dominio.SuministroVerificado;
import pe.nivora.backend.acceso.dominio.TokenOpaco;
import pe.nivora.backend.acceso.dominio.consulta.SesionConsulta;
import pe.nivora.backend.acceso.dominio.consulta.TokenConsulta;
import pe.nivora.backend.acceso.dominio.contratos.RepositorioSesionesConsulta;
import pe.nivora.backend.acceso.dominio.contratos.RepositorioSuministros;
import pe.nivora.backend.acceso.dominio.contratos.RepositorioSuministrosNoDisponibleException;
import pe.nivora.backend.acceso.dominio.contratos.SesionesConsultaNoDisponiblesException;

@Repository
public class RepositorioSesionesConsultaJdbc implements RepositorioSesionesConsulta {

    private final JdbcTemplate jdbc;
    private final RepositorioSuministros suministros;
    private final TransactionTemplate transaccion;

    public RepositorioSesionesConsultaJdbc(JdbcTemplate jdbc,
            RepositorioSuministros suministros, PlatformTransactionManager gestor) {
        this.jdbc = jdbc;
        this.suministros = suministros;
        this.transaccion = new TransactionTemplate(gestor);
        this.transaccion.setTimeout(10);
    }

    @Override
    public SesionConsulta crear(TokenConsulta token, SuministroVerificado suministro,
            IdentidadConsultada identidad, Instant ahora, Instant expiraEn) {
        return ejecutar(() -> transaccion.execute(estado -> {
            suministros.guardar(suministro);
            var sesion = new SesionConsulta(UUID.randomUUID().toString(),
                suministro.suministro().codigo(), identidad.nombreCompleto(), ahora, expiraEn);
            jdbc.update("""
                INSERT INTO sesiones_consulta (
                    id_sesion, hash_token, codigo_suministro, dni_utilizado,
                    nombre_consultado, creada_en_utc, expira_en_utc
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, sesion.id(), token.huella(), sesion.codigoSuministro().valor(),
                identidad.dni().valor(), sesion.nombre(), utc(ahora), utc(expiraEn));
            return sesion;
        }));
    }

    @Override
    public Optional<SesionConsulta> autenticar(TokenConsulta token, Instant ahora) {
        return ejecutar(() -> jdbc.query("""
            SELECT id_sesion, codigo_suministro, nombre_consultado, creada_en_utc, expira_en_utc
            FROM sesiones_consulta
            WHERE hash_token = ? AND revocada_en_utc IS NULL
                AND creada_en_utc <= ? AND expira_en_utc > ?
            """, (fila, numero) -> new SesionConsulta(fila.getString("id_sesion"),
                new CodigoSuministro(fila.getString("codigo_suministro")),
                fila.getString("nombre_consultado"), fecha(fila, "creada_en_utc"),
                fecha(fila, "expira_en_utc")), token.huella(), utc(ahora), utc(ahora))
            .stream().findFirst());
    }

    @Override
    public void cerrar(TokenConsulta token, Instant ahora) {
        ejecutar(() -> jdbc.update("""
            UPDATE sesiones_consulta SET revocada_en_utc = GREATEST(creada_en_utc, ?)
            WHERE hash_token = ? AND revocada_en_utc IS NULL
            """, utc(ahora), token.huella()));
    }

    @Override
    public long consumirIntento(String origen, Instant ahora, Duration ventana, int maximo) {
        if (origen == null || origen.isBlank() || origen.length() > 100) {
            throw new IllegalArgumentException("Origen de acceso invalido.");
        }
        String clave = TokenOpaco.huellaDe("consulta:origen:" + origen);
        return ejecutar(() -> transaccion.execute(estado -> {
            jdbc.update("""
                INSERT INTO limites_acceso_consulta (clave, inicio_ventana_utc, cantidad)
                VALUES (?, ?, 1) ON DUPLICATE KEY UPDATE clave = clave
                """, clave, utc(ahora.minus(ventana)));
            var fila = jdbc.queryForObject("""
                SELECT inicio_ventana_utc, cantidad FROM limites_acceso_consulta
                WHERE clave = ? FOR UPDATE
                """, (r, n) -> new Ventana(fecha(r, "inicio_ventana_utc"), r.getInt("cantidad")), clave);
            Instant fin = fila.inicio().plus(ventana);
            if (!ahora.isBefore(fin)) {
                jdbc.update("""
                    UPDATE limites_acceso_consulta SET inicio_ventana_utc = ?, cantidad = 1
                    WHERE clave = ?
                    """, utc(ahora), clave);
                return 0L;
            }
            if (fila.cantidad() >= maximo) {
                return Math.max(1, (Duration.between(ahora, fin).toMillis() + 999) / 1000);
            }
            jdbc.update("UPDATE limites_acceso_consulta SET cantidad = cantidad + 1 WHERE clave = ?", clave);
            return 0L;
        }));
    }

    private <T> T ejecutar(Supplier<T> operacion) {
        try {
            return operacion.get();
        } catch (DataAccessException | TransactionException | RepositorioSuministrosNoDisponibleException error) {
            // Los detalles de JDBC pueden incluir los valores de la solicitud.
            throw new SesionesConsultaNoDisponiblesException();
        }
    }

    private static LocalDateTime utc(Instant fecha) {
        return LocalDateTime.ofInstant(fecha, ZoneOffset.UTC);
    }

    private static Instant fecha(ResultSet fila, String columna) throws SQLException {
        return fila.getObject(columna, LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }

    private record Ventana(Instant inicio, int cantidad) {}
}
