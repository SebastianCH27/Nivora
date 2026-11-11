package pe.nivora.backend.acceso.infraestructura.administradores;

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

import pe.nivora.backend.acceso.dominio.administradores.*;
import pe.nivora.backend.acceso.dominio.contratos.*;

@Repository
public class RepositorioSesionesAdministrativasJdbc implements RepositorioSesionesAdministrativas {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;

    public RepositorioSesionesAdministrativasJdbc(JdbcTemplate jdbc, PlatformTransactionManager gestor) {
        this.jdbc = jdbc;
        transaccion = new TransactionTemplate(gestor);
        transaccion.setTimeout(10);
    }

    @Override
    public Optional<CredencialAdministrativa> buscarCredencial(UsuarioAdministrador usuario) {
        return ejecutar(() -> cuenta(usuario, false));
    }

    @Override
    public void consumirIntento(UsuarioAdministrador usuario, String origen, Instant ahora,
            PoliticaSesionAdministrativa politica) {
        consumir("", usuario, origen, ahora, politica);
    }

    @Override
    public void consumirConfirmacion(UsuarioAdministrador usuario, String origen, Instant ahora,
            PoliticaSesionAdministrativa politica) {
        consumir("confirmacion:", usuario, origen, ahora, politica);
    }

    private void consumir(String ambito, UsuarioAdministrador usuario, String origen, Instant ahora,
            PoliticaSesionAdministrativa politica) {
        if (origen == null || origen.isBlank() || origen.length() > 100) {
            throw new IllegalArgumentException("Origen de acceso invalido.");
        }
        long espera = ejecutar(() -> transaccion.execute(estado -> {
            long ip = limitar(TokenAdministrativo.huellaDe(ambito + "origen:" + origen), ahora,
                politica.ventanaIntentos(), politica.intentosPorOrigen());
            return ip > 0 ? ip : limitar(TokenAdministrativo.huellaDe(ambito + "usuario:" + usuario.valor()),
                ahora, politica.ventanaIntentos(), politica.intentosPorUsuario());
        }));
        if (espera > 0) throw new AccesoAdministrativoLimitadoException(espera);
    }

    private long limitar(String clave, Instant ahora, Duration ventana, int maximo) {
        jdbc.update("""
            INSERT INTO limites_acceso_administrativo (clave, inicio_ventana_utc, cantidad)
            VALUES (?, ?, 1) ON DUPLICATE KEY UPDATE clave = clave
            """, clave, utc(ahora.minus(ventana)));
        var fila = jdbc.queryForObject("SELECT inicio_ventana_utc, cantidad FROM limites_acceso_administrativo WHERE clave = ? FOR UPDATE",
            (r, n) -> new Ventana(fecha(r, "inicio_ventana_utc"), r.getInt("cantidad")), clave);
        Instant inicio = fila.inicio();
        int cantidad = fila.cantidad();
        if (!ahora.isBefore(inicio.plus(ventana))) {
            jdbc.update("UPDATE limites_acceso_administrativo SET inicio_ventana_utc = ?, cantidad = 1 WHERE clave = ?", utc(ahora), clave);
            return 0;
        }
        if (cantidad >= maximo) return Math.max(1, (Duration.between(ahora, inicio.plus(ventana)).toMillis() + 999) / 1000);
        jdbc.update("UPDATE limites_acceso_administrativo SET cantidad = cantidad + 1 WHERE clave = ?", clave);
        return 0;
    }

    @Override
    public SesionAdministrativa crear(TokenAdministrativo token, CredencialAdministrativa esperada,
            Instant ahora, PoliticaSesionAdministrativa politica) {
        return ejecutar(() -> transaccion.execute(estado -> {
            var actual = cuenta(esperada.cuenta().usuario(), true)
                .orElseThrow(CredencialesAdministrativasInvalidasException::new);
            if (!actual.cuenta().activa()
                    || actual.cuenta().versionCredenciales() != esperada.cuenta().versionCredenciales()
                    || !actual.hashClave().equals(esperada.hashClave())) {
                throw new CredencialesAdministrativasInvalidasException();
            }
            var sesion = new SesionAdministrativa(UUID.randomUUID().toString(), actual.cuenta(),
                ahora, ahora, ahora.plus(politica.duracionMaxima()), ahora.plus(politica.inactividad()));
            jdbc.update("""
                INSERT INTO sesiones_administrativas (id_sesion, hash_token, usuario,
                    version_credenciales, creada_en_utc, ultima_actividad_utc,
                    expira_en_utc, inactiva_hasta_utc, revocada_en_utc)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL)
                """, sesion.id(), token.huella(), actual.cuenta().usuario().valor(),
                actual.cuenta().versionCredenciales(), utc(sesion.creadaEn()), utc(sesion.ultimaActividad()),
                utc(sesion.expiraEn()), utc(sesion.inactivaHasta()));
            auditar(actual.cuenta().usuario().valor(), "INICIO_SESION", ahora);
            return sesion;
        }));
    }

    @Override
    public Optional<SesionAdministrativa> autenticar(TokenAdministrativo token, Instant ahora,
            PoliticaSesionAdministrativa politica) {
        return ejecutar(() -> transaccion.execute(estado -> {
            var usuario = propietario(token);
            if (usuario.isEmpty()) return Optional.empty();
            // Todas las operaciones toman primero cuenta y despues sesion.
            var cuenta = cuenta(usuario.get(), true);
            if (cuenta.isEmpty()) return Optional.empty();
            var guardada = sesion(token);
            if (guardada.isEmpty() || guardada.get().revocada() != null) return Optional.empty();
            var actual = guardada.get();
            if (!cuenta.get().cuenta().activa()
                    || actual.version() != cuenta.get().cuenta().versionCredenciales()
                    || ahora.isBefore(actual.creada()) || !ahora.isBefore(actual.expira())
                    || !ahora.isBefore(actual.inactiva())) {
                jdbc.update("UPDATE sesiones_administrativas SET revocada_en_utc = ? WHERE hash_token = ? AND revocada_en_utc IS NULL", utc(ahora), token.huella());
                return Optional.empty();
            }
            Instant actividad = actual.actividad().isAfter(ahora) ? actual.actividad() : ahora;
            Instant inactiva = ahora.plus(politica.inactividad());
            if (inactiva.isBefore(actual.inactiva())) inactiva = actual.inactiva();
            if (inactiva.isAfter(actual.expira())) inactiva = actual.expira();
            jdbc.update("UPDATE sesiones_administrativas SET ultima_actividad_utc = ?, inactiva_hasta_utc = ? WHERE hash_token = ? AND revocada_en_utc IS NULL",
                utc(actividad), utc(inactiva), token.huella());
            return Optional.of(new SesionAdministrativa(actual.id(), cuenta.get().cuenta(),
                actual.creada(), actividad, actual.expira(), inactiva));
        }));
    }

    @Override
    public boolean cerrar(TokenAdministrativo token, Instant ahora) {
        return ejecutar(() -> transaccion.execute(estado -> {
            var usuario = propietario(token);
            if (usuario.isEmpty() || cuenta(usuario.get(), true).isEmpty()) return false;
            var actual = sesion(token);
            if (actual.isEmpty() || actual.get().revocada() != null) return false;
            int filas = jdbc.update("UPDATE sesiones_administrativas SET revocada_en_utc = ? WHERE hash_token = ? AND revocada_en_utc IS NULL", utc(ahora), token.huella());
            if (filas == 1) auditar(usuario.get().valor(), "CIERRE_SESION", ahora);
            return filas == 1;
        }));
    }

    private Optional<UsuarioAdministrador> propietario(TokenAdministrativo token) {
        return jdbc.query("SELECT usuario FROM sesiones_administrativas WHERE hash_token = ?",
            (fila, numero) -> new UsuarioAdministrador(fila.getString(1)), token.huella()).stream().findFirst();
    }

    private Optional<Guardada> sesion(TokenAdministrativo token) {
        return jdbc.query("SELECT * FROM sesiones_administrativas WHERE hash_token = ? FOR UPDATE",
            (fila, numero) -> new Guardada(fila.getString("id_sesion"), fila.getLong("version_credenciales"),
                fecha(fila, "creada_en_utc"), fecha(fila, "ultima_actividad_utc"),
                fecha(fila, "expira_en_utc"), fecha(fila, "inactiva_hasta_utc"), fecha(fila, "revocada_en_utc")),
            token.huella()).stream().findFirst();
    }

    private Optional<CredencialAdministrativa> cuenta(UsuarioAdministrador usuario, boolean bloquear) {
        return jdbc.query("SELECT * FROM administradores WHERE usuario = ?" + (bloquear ? " FOR UPDATE" : ""),
            (fila, numero) -> new CredencialAdministrativa(new Administrador(new UsuarioAdministrador(fila.getString("usuario")),
                fila.getString("nombre"), fila.getBoolean("activa"), fila.getBoolean("gestionar_administradores"),
                fila.getBoolean("cambio_clave_requerido"), fila.getLong("version_credenciales"),
                fecha(fila, "creado_en_utc"), fecha(fila, "actualizado_en_utc")), fila.getString("hash_clave")),
            usuario.valor()).stream().findFirst();
    }

    private void auditar(String usuario, String operacion, Instant ahora) {
        jdbc.update("INSERT INTO auditoria_administradores (actor_usuario, afectado_usuario, operacion, registrado_en_utc) VALUES (?, ?, ?, ?)",
            usuario, usuario, operacion, utc(ahora));
    }

    private <T> T ejecutar(Supplier<T> trabajo) {
        try { return trabajo.get(); }
        catch (DataAccessException | TransactionException error) { throw new SesionesAdministrativasNoDisponiblesException(error); }
    }

    private static Instant fecha(ResultSet fila, String columna) throws SQLException {
        var fecha = fila.getObject(columna, LocalDateTime.class);
        return fecha == null ? null : fecha.toInstant(ZoneOffset.UTC);
    }
    private static LocalDateTime utc(Instant fecha) { return LocalDateTime.ofInstant(fecha, ZoneOffset.UTC); }
    private record Guardada(String id, long version, Instant creada, Instant actividad,
        Instant expira, Instant inactiva, Instant revocada) {}
    private record Ventana(Instant inicio, int cantidad) {}
}
