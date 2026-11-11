package pe.nivora.backend.acceso.infraestructura.administradores;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import pe.nivora.backend.acceso.dominio.administradores.*;
import pe.nivora.backend.acceso.dominio.contratos.*;

@Repository
public class RepositorioGestionAdministradoresJdbc implements RepositorioGestionAdministradores {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    public RepositorioGestionAdministradoresJdbc(JdbcTemplate jdbc,
            PlatformTransactionManager gestor, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
        transaccion = new TransactionTemplate(gestor);
        transaccion.setTimeout(10);
    }

    @Override
    public PaginaAdministradores listar(ActorAdministrativo actor, String despuesDe, int limite) {
        return atomico(() -> {
            autorizar(actor, true);
            var filas = jdbc.query("SELECT * FROM administradores WHERE usuario > ? ORDER BY usuario LIMIT ?",
                (r, n) -> cuenta(r), despuesDe, limite + 1);
            boolean mas = filas.size() > limite;
            var pagina = filas.subList(0, Math.min(limite, filas.size()));
            return new PaginaAdministradores(pagina,
                pagina.isEmpty() ? null : pagina.getLast().usuario().valor(), mas);
        });
    }

    @Override
    public Administrador consultar(ActorAdministrativo actor, UsuarioAdministrador usuario) {
        return atomico(() -> {
            autorizar(actor, true);
            return buscar(usuario, false).orElseThrow(AdministradorNoEncontradoException::new).cuenta();
        });
    }

    @Override
    public PaginaAuditoriaAdministrativa auditoria(ActorAdministrativo actor,
            UsuarioAdministrador usuario, long despuesDe, int limite) {
        return atomico(() -> {
            autorizar(actor, true);
            buscar(usuario, false).orElseThrow(AdministradorNoEncontradoException::new);
            var filas = jdbc.query("""
                SELECT id_evento, actor_usuario, afectado_usuario, operacion, registrado_en_utc
                FROM auditoria_administradores WHERE afectado_usuario = ? AND id_evento > ?
                ORDER BY id_evento LIMIT ?
                """, (r, n) -> new PaginaAuditoriaAdministrativa.Evento(
                    Long.toString(r.getLong("id_evento")), r.getString("actor_usuario"),
                    r.getString("afectado_usuario"), r.getString("operacion"), fecha(r, "registrado_en_utc")),
                usuario.valor(), despuesDe, limite + 1);
            boolean mas = filas.size() > limite;
            var pagina = filas.subList(0, Math.min(limite, filas.size()));
            return new PaginaAuditoriaAdministrativa(pagina,
                pagina.isEmpty() ? Long.toString(despuesDe) : pagina.getLast().idEvento(), mas);
        });
    }

    @Override
    public ResultadoGestionAdministrativa crear(ConfirmacionAdministrativa confirmacion,
            UsuarioAdministrador usuario, String nombre, String hash, boolean gestionar) {
        validarHash(hash);
        return atomico(() -> {
            coordinar();
            confirmar(confirmacion, true);
            if (buscar(usuario, true).isPresent()) throw conflicto("Ya existe una cuenta con ese usuario.");
            Instant ahora = ahora();
            jdbc.update("""
                INSERT INTO administradores (usuario, nombre, hash_clave, activa,
                    gestionar_administradores, cambio_clave_requerido, version_credenciales,
                    creado_en_utc, actualizado_en_utc)
                VALUES (?, ?, ?, TRUE, ?, TRUE, 1, ?, ?)
                """, usuario.valor(), nombre, hash, gestionar, utc(ahora), utc(ahora));
            auditar(confirmacion.actor().usuario(), usuario, "CREACION_ADMINISTRADOR", ahora);
            if (gestionar) auditar(confirmacion.actor().usuario(), usuario,
                "CONCESION_GESTION_ADMINISTRADORES", ahora);
            return new ResultadoGestionAdministrativa(new Administrador(usuario, nombre, true,
                gestionar, true, 1, ahora, ahora), true, false);
        });
    }

    @Override
    public ResultadoGestionAdministrativa cambiarEstado(ConfirmacionAdministrativa confirmacion,
            UsuarioAdministrador usuario, boolean activa) {
        return atomico(() -> {
            coordinar(); confirmar(confirmacion, true);
            var actual = destino(usuario);
            if (actual.activa() == activa) return new ResultadoGestionAdministrativa(actual, false, false);
            comprobarGestorRestante(actual, activa, actual.gestionarAdministradores(), actual.cambioClaveRequerido());
            return actualizar(confirmacion, actual, null, activa, actual.gestionarAdministradores(),
                actual.cambioClaveRequerido(), activa ? "ACTIVACION_ADMINISTRADOR" : "DESACTIVACION_ADMINISTRADOR");
        });
    }

    @Override
    public ResultadoGestionAdministrativa cambiarPermiso(ConfirmacionAdministrativa confirmacion,
            UsuarioAdministrador usuario, boolean gestionar) {
        return atomico(() -> {
            coordinar(); confirmar(confirmacion, true);
            var actual = destino(usuario);
            if (actual.gestionarAdministradores() == gestionar) return new ResultadoGestionAdministrativa(actual, false, false);
            comprobarGestorRestante(actual, actual.activa(), gestionar, actual.cambioClaveRequerido());
            return actualizar(confirmacion, actual, null, actual.activa(), gestionar,
                actual.cambioClaveRequerido(), gestionar
                    ? "CONCESION_GESTION_ADMINISTRADORES" : "REVOCACION_GESTION_ADMINISTRADORES");
        });
    }

    @Override
    public ResultadoGestionAdministrativa restablecerClave(ConfirmacionAdministrativa confirmacion,
            UsuarioAdministrador usuario, String hash) {
        validarHash(hash);
        return atomico(() -> {
            coordinar(); confirmar(confirmacion, true);
            if (usuario.equals(confirmacion.actor().usuario())) {
                throw conflicto("Para tu propia cuenta utiliza el cambio de clave de la sesion actual.");
            }
            var actual = destino(usuario);
            comprobarGestorRestante(actual, actual.activa(), actual.gestionarAdministradores(), true);
            return actualizar(confirmacion, actual, hash, actual.activa(),
                actual.gestionarAdministradores(), true, "RESTABLECIMIENTO_CLAVE");
        });
    }

    @Override
    public ResultadoGestionAdministrativa cambiarClavePropia(ConfirmacionAdministrativa confirmacion,
            String hash) {
        validarHash(hash);
        return atomico(() -> {
            coordinar();
            var actual = confirmar(confirmacion, false).cuenta();
            return actualizar(confirmacion, actual, hash, actual.activa(),
                actual.gestionarAdministradores(), false, "CAMBIO_CLAVE");
        });
    }

    private ResultadoGestionAdministrativa actualizar(ConfirmacionAdministrativa confirmacion,
            Administrador actual, String hash, boolean activa, boolean gestionar,
            boolean cambioRequerido, String operacion) {
        if (actual.versionCredenciales() == Long.MAX_VALUE) throw conflicto("La version de la cuenta no admite mas cambios.");
        long version = actual.versionCredenciales() + 1;
        Instant ahora = ahora();
        jdbc.update("""
            UPDATE administradores SET hash_clave = COALESCE(?, hash_clave), activa = ?,
                gestionar_administradores = ?, cambio_clave_requerido = ?,
                version_credenciales = ?, actualizado_en_utc = ? WHERE usuario = ?
            """, hash, activa, gestionar, cambioRequerido, version, utc(ahora), actual.usuario().valor());
        jdbc.update("UPDATE sesiones_administrativas SET revocada_en_utc = ? WHERE usuario = ? AND revocada_en_utc IS NULL",
            utc(ahora), actual.usuario().valor());
        auditar(confirmacion.actor().usuario(), actual.usuario(), operacion, ahora);
        return new ResultadoGestionAdministrativa(new Administrador(actual.usuario(), actual.nombre(),
            activa, gestionar, cambioRequerido, version, actual.creadoEn(), ahora), true,
            actual.usuario().equals(confirmacion.actor().usuario()));
    }

    private void comprobarGestorRestante(Administrador actual, boolean activa,
            boolean gestionar, boolean cambioPendiente) {
        boolean eraGestor = actual.activa() && actual.gestionarAdministradores() && !actual.cambioClaveRequerido();
        if (!eraGestor || (activa && gestionar && !cambioPendiente)) return;
        Long gestores = jdbc.queryForObject("SELECT COUNT(*) FROM administradores WHERE activa=TRUE AND gestionar_administradores=TRUE AND cambio_clave_requerido=FALSE", Long.class);
        if (gestores == null || gestores <= 1) {
            throw conflicto("Debe permanecer al menos un administrador con acceso y permiso para gestionar cuentas.");
        }
    }

    private CredencialAdministrativa confirmar(ConfirmacionAdministrativa confirmacion, boolean gestor) {
        var actual = autorizar(confirmacion.actor(), gestor);
        if (!actual.cuenta().usuario().equals(confirmacion.credencial().cuenta().usuario())
                || actual.cuenta().versionCredenciales() != confirmacion.credencial().cuenta().versionCredenciales()
                || !actual.hashClave().equals(confirmacion.credencial().hashClave())) {
            throw new CredencialesAdministrativasInvalidasException();
        }
        return actual;
    }

    private CredencialAdministrativa autorizar(ActorAdministrativo actor, boolean gestor) {
        var propietarios = jdbc.query("SELECT usuario FROM sesiones_administrativas WHERE hash_token = ?",
            (r, n) -> r.getString(1), actor.token().huella());
        if (propietarios.isEmpty() || !propietarios.getFirst().equals(actor.usuario().valor())) {
            throw new CredencialesAdministrativasInvalidasException();
        }
        var actual = buscar(actor.usuario(), true).orElseThrow(CredencialesAdministrativasInvalidasException::new);
        var sesiones = jdbc.query("SELECT * FROM sesiones_administrativas WHERE hash_token = ? FOR UPDATE",
            (r, n) -> new Vigencia(r.getLong("version_credenciales"), fecha(r, "creada_en_utc"),
                fecha(r, "expira_en_utc"), fecha(r, "inactiva_hasta_utc"), fecha(r, "revocada_en_utc")), actor.token().huella());
        Instant ahora = ahora();
        if (sesiones.isEmpty() || !actual.cuenta().activa()) throw new CredencialesAdministrativasInvalidasException();
        var sesion = sesiones.getFirst();
        if (sesion.revocada() != null || sesion.version() != actual.cuenta().versionCredenciales()
                || ahora.isBefore(sesion.creada()) || !ahora.isBefore(sesion.expira()) || !ahora.isBefore(sesion.inactiva())) {
            throw new CredencialesAdministrativasInvalidasException();
        }
        if (gestor && (!actual.cuenta().gestionarAdministradores() || actual.cuenta().cambioClaveRequerido())) {
            throw new PermisoGestionAdministrativaException();
        }
        return actual;
    }

    private Administrador destino(UsuarioAdministrador usuario) {
        return buscar(usuario, true).orElseThrow(AdministradorNoEncontradoException::new).cuenta();
    }

    private Optional<CredencialAdministrativa> buscar(UsuarioAdministrador usuario, boolean bloquear) {
        return jdbc.query("SELECT * FROM administradores WHERE usuario = ?" + (bloquear ? " FOR UPDATE" : ""),
            (r, n) -> new CredencialAdministrativa(cuenta(r), r.getString("hash_clave")), usuario.valor())
            .stream().findFirst();
    }

    private static Administrador cuenta(ResultSet r) throws SQLException {
        return new Administrador(new UsuarioAdministrador(r.getString("usuario")), r.getString("nombre"),
            r.getBoolean("activa"), r.getBoolean("gestionar_administradores"), r.getBoolean("cambio_clave_requerido"),
            r.getLong("version_credenciales"), fecha(r, "creado_en_utc"), fecha(r, "actualizado_en_utc"));
    }
    private void coordinar() { jdbc.queryForObject("SELECT id_control FROM control_administradores WHERE id_control=1 FOR UPDATE", Integer.class); }
    private void auditar(UsuarioAdministrador actor, UsuarioAdministrador afectado, String operacion, Instant ahora) {
        jdbc.update("INSERT INTO auditoria_administradores (actor_usuario, afectado_usuario, operacion, registrado_en_utc) VALUES (?, ?, ?, ?)",
            actor.valor(), afectado.valor(), operacion, utc(ahora));
    }
    private <T> T atomico(Supplier<T> operacion) {
        try { return transaccion.execute(estado -> operacion.get()); }
        catch (DuplicateKeyException error) { throw conflicto("La operacion entra en conflicto con una cuenta existente."); }
        catch (DataAccessException | TransactionException error) { throw new RepositorioAdministradoresNoDisponibleException(error); }
    }
    private Instant ahora() { return reloj.instant().truncatedTo(ChronoUnit.MICROS); }
    private static void validarHash(String hash) {
        if (hash == null || hash.isBlank() || hash.length() > 255) throw new IllegalArgumentException("Hash administrativo invalido.");
    }
    private static ConflictoGestionAdministrativaException conflicto(String mensaje) { return new ConflictoGestionAdministrativaException(mensaje); }
    private static LocalDateTime utc(Instant fecha) { return LocalDateTime.ofInstant(fecha, ZoneOffset.UTC); }
    private static Instant fecha(ResultSet r, String nombre) throws SQLException {
        var fecha = r.getObject(nombre, LocalDateTime.class);
        return fecha == null ? null : fecha.toInstant(ZoneOffset.UTC);
    }
    private record Vigencia(long version, Instant creada, Instant expira, Instant inactiva, Instant revocada) {}
}
