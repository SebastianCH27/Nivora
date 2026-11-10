package pe.nivora.backend.acceso.infraestructura.administradores;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import pe.nivora.backend.acceso.dominio.administradores.Administrador;
import pe.nivora.backend.acceso.dominio.administradores.UsuarioAdministrador;
import pe.nivora.backend.acceso.dominio.contratos.*;

@Repository
public class RepositorioAdministradoresJdbc implements RepositorioAdministradores {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;

    public RepositorioAdministradoresJdbc(JdbcTemplate jdbc, PlatformTransactionManager gestor) {
        this.jdbc = jdbc;
        transaccion = new TransactionTemplate(gestor);
        transaccion.setTimeout(10);
    }

    @Override
    public Administrador crearPrimero(UsuarioAdministrador usuario, String nombre,
            String hashClave, Instant ahora) {
        Instant fecha = ahora.truncatedTo(ChronoUnit.MICROS);
        var cuenta = new Administrador(usuario, nombre, true, true, false, 1, fecha, fecha);
        if (hashClave == null || hashClave.isBlank() || hashClave.length() > 255) {
            throw new IllegalArgumentException("Hash administrativo invalido.");
        }
        try {
            return transaccion.execute(estado -> {
                jdbc.queryForObject("SELECT id_control FROM control_administradores WHERE id_control = 1 FOR UPDATE", Integer.class);
                Long cantidad = jdbc.queryForObject("SELECT COUNT(*) FROM administradores", Long.class);
                if (cantidad == null || cantidad > 0) throw new AdministracionYaInicializadaException();
                jdbc.update("""
                    INSERT INTO administradores (usuario, nombre, hash_clave, activa,
                        gestionar_administradores, cambio_clave_requerido, version_credenciales,
                        creado_en_utc, actualizado_en_utc)
                    VALUES (?, ?, ?, TRUE, TRUE, FALSE, 1, ?, ?)
                    """, usuario.valor(), cuenta.nombre(), hashClave, utc(fecha), utc(fecha));
                jdbc.update("""
                    INSERT INTO auditoria_administradores
                        (actor_usuario, afectado_usuario, operacion, registrado_en_utc)
                    VALUES (NULL, ?, 'CREACION_INICIAL', ?)
                    """, usuario.valor(), utc(fecha));
                return cuenta;
            });
        } catch (DataAccessException | TransactionException error) {
            throw new RepositorioAdministradoresNoDisponibleException(error);
        }
    }

    @Override
    public Optional<Administrador> buscar(UsuarioAdministrador usuario) {
        try {
            return jdbc.query("""
                SELECT usuario, nombre, activa, gestionar_administradores,
                    cambio_clave_requerido, version_credenciales, creado_en_utc, actualizado_en_utc
                FROM administradores WHERE usuario = ?
                """, (fila, numero) -> new Administrador(new UsuarioAdministrador(fila.getString("usuario")),
                    fila.getString("nombre"), fila.getBoolean("activa"),
                    fila.getBoolean("gestionar_administradores"), fila.getBoolean("cambio_clave_requerido"),
                    fila.getLong("version_credenciales"),
                    fila.getObject("creado_en_utc", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                    fila.getObject("actualizado_en_utc", LocalDateTime.class).toInstant(ZoneOffset.UTC)),
                usuario.valor()).stream().findFirst();
        } catch (DataAccessException error) {
            throw new RepositorioAdministradoresNoDisponibleException(error);
        }
    }

    private static LocalDateTime utc(Instant fecha) { return LocalDateTime.ofInstant(fecha, ZoneOffset.UTC); }
}
