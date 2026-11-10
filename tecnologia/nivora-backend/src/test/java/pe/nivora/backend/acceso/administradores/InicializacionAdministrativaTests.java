package pe.nivora.backend.acceso.administradores;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import pe.nivora.backend.acceso.aplicacion.InicializarAdministracion;
import pe.nivora.backend.acceso.dominio.administradores.UsuarioAdministrador;
import pe.nivora.backend.acceso.dominio.contratos.*;
import pe.nivora.backend.acceso.infraestructura.administradores.*;

import static org.junit.jupiter.api.Assertions.*;

class InicializacionAdministrativaTests {
    private static final Instant AHORA = Instant.parse("2026-11-10T22:00:00.123456789Z");
    // Solo para probar transacciones; la prueba de aplicacion utiliza Argon2 real.
    private static final String HASH_PRUEBA = "hash-opaco-de-la-prueba-transaccional";

    @Test
    void primeraCuentaTienePermisoDeGestionYAuditoriaAtomica() throws Exception {
        try (var base = new Base()) {
            var cuenta = base.repositorio.crearPrimero(new UsuarioAdministrador("Admin"),
                "  Responsable Nivora  ", HASH_PRUEBA, AHORA);
            assertEquals("admin", cuenta.usuario().valor());
            assertEquals("Responsable Nivora", cuenta.nombre());
            assertTrue(cuenta.activa());
            assertTrue(cuenta.gestionarAdministradores());
            assertFalse(cuenta.cambioClaveRequerido());
            assertEquals(1, cuenta.versionCredenciales());
            assertEquals(Instant.parse("2026-11-10T22:00:00.123456Z"), cuenta.creadoEn());
            assertEquals(cuenta, base.repositorio.buscar(new UsuarioAdministrador("ADMIN")).orElseThrow());
            assertFalse(cuenta.toString().contains(HASH_PRUEBA));
            var evento = base.jdbc.queryForMap("SELECT * FROM auditoria_administradores");
            assertNull(evento.get("actor_usuario"));
            assertEquals("admin", evento.get("afectado_usuario"));
            assertEquals("CREACION_INICIAL", evento.get("operacion"));
            assertEquals(LocalDateTime.parse("2026-11-10T22:00:00.123456"),
                base.jdbc.queryForObject("SELECT registrado_en_utc FROM auditoria_administradores",
                    (fila, numero) -> fila.getObject(1, LocalDateTime.class)));
            assertFalse(evento.toString().contains(HASH_PRUEBA));
        }
    }

    @Test
    void repetirInicializacionNoReemplazaCredencialesNiCreaOtroGestor() throws Exception {
        try (var base = new Base()) {
            base.primero();
            var antes = base.jdbc.queryForList("SELECT * FROM administradores");
            assertThrows(AdministracionYaInicializadaException.class, () ->
                base.repositorio.crearPrimero(new UsuarioAdministrador("admin"), "Otro nombre", "otro-hash", AHORA.plusSeconds(1)));
            assertThrows(AdministracionYaInicializadaException.class, () ->
                base.repositorio.crearPrimero(new UsuarioAdministrador("segundo"), "Otra persona", "otro-hash", AHORA.plusSeconds(2)));
            assertEquals(antes, base.jdbc.queryForList("SELECT * FROM administradores"));
            assertEquals(1, base.cantidad("auditoria_administradores"));
        }
    }

    @Test
    void cuentasInactivasTampocoPermitenReabrirLaInicializacion() throws Exception {
        try (var base = new Base()) {
            base.primero();
            base.jdbc.update("UPDATE administradores SET activa=FALSE, gestionar_administradores=FALSE");
            assertThrows(AdministracionYaInicializadaException.class, () ->
                base.repositorio.crearPrimero(new UsuarioAdministrador("segundo"), "Otra persona", HASH_PRUEBA, AHORA));
            assertFalse(base.repositorio.buscar(new UsuarioAdministrador("admin")).orElseThrow().activa());
            assertEquals(1, base.cantidad("administradores"));
        }
    }

    @Test
    void dosInicializadoresSoloCreanUnaCuentaYUnEvento() throws Exception {
        try (var base = new Base(); var hilos = Executors.newFixedThreadPool(2)) {
            var salida = new CountDownLatch(1);
            Callable<Boolean> altaUno = () -> intentar(base, salida, "primero");
            Callable<Boolean> altaDos = () -> intentar(base, salida, "segundo");
            var uno = hilos.submit(altaUno);
            var dos = hilos.submit(altaDos);
            salida.countDown();
            assertNotEquals(uno.get(), dos.get());
            assertEquals(1, base.cantidad("administradores"));
            assertEquals(1, base.cantidad("auditoria_administradores"));
        }
    }

    private static boolean intentar(Base base, CountDownLatch salida, String usuario) throws Exception {
        salida.await();
        try {
            base.nuevoRepositorio().crearPrimero(new UsuarioAdministrador(usuario), "Responsable", HASH_PRUEBA, AHORA);
            return true;
        } catch (AdministracionYaInicializadaException existente) { return false; }
    }

    @Test
    void falloDeAuditoriaRevierteLaCuentaCompleta() throws Exception {
        try (var base = new Base()) {
            base.jdbc.execute("DROP TABLE auditoria_administradores");
            assertThrows(RepositorioAdministradoresNoDisponibleException.class, base::primero);
            assertEquals(0, base.cantidad("administradores"));
            base.aplicar();
            base.primero();
            assertEquals(1, base.cantidad("auditoria_administradores"));
        }
    }

    @Test
    void controlAusenteSeInformaYNoCreaCuentasSinCoordinar() throws Exception {
        try (var base = new Base()) {
            base.jdbc.update("DELETE FROM control_administradores");
            assertThrows(RepositorioAdministradoresNoDisponibleException.class, base::primero);
            assertEquals(0, base.cantidad("administradores"));
            assertEquals(0, base.cantidad("auditoria_administradores"));
        }
    }

    @Test
    void reaplicarElScriptConservaLaCuentaYLaAuditoria() throws Exception {
        try (var base = new Base()) {
            base.primero();
            var cuentas = base.jdbc.queryForList("SELECT * FROM administradores");
            var eventos = base.jdbc.queryForList("SELECT * FROM auditoria_administradores");
            base.aplicar();
            assertEquals(cuentas, base.jdbc.queryForList("SELECT * FROM administradores"));
            assertEquals(eventos, base.jdbc.queryForList("SELECT * FROM auditoria_administradores"));
            assertEquals(1, base.cantidad("control_administradores"));
        }
    }

    @Test
    void ausenciaDeCuentaYFalloDeBaseSonResultadosDistintos() throws Exception {
        try (var base = new Base()) {
            var usuario = new UsuarioAdministrador("ausente");
            assertTrue(base.repositorio.buscar(usuario).isEmpty());
            base.jdbc.execute("ALTER TABLE administradores RENAME TO administradores_fuera");
            assertThrows(RepositorioAdministradoresNoDisponibleException.class, () -> base.repositorio.buscar(usuario));
        }
    }

    @Test
    void casoDeUsoPersisteHashRealYFechaUtcSinExponerLaClave() throws Exception {
        try (var base = new Base()) {
            var protector = new ProtectorClavesArgon2();
            var inicializar = new InicializarAdministracion(base.repositorio, protector, Clock.fixed(AHORA, ZoneOffset.UTC));
            char[] clave = "Una frase ficticia de prueba".toCharArray();
            var cuenta = inicializar.ejecutar("Admin.Nivora", "Responsable", clave);
            String hash = base.jdbc.queryForObject("SELECT hash_clave FROM administradores", String.class);
            assertTrue(protector.coincide(clave, hash));
            assertFalse(hash.contains(new String(clave)));
            assertFalse(cuenta.toString().contains(hash));
            assertEquals("admin.nivora", cuenta.usuario().valor());
            assertEquals(1, base.cantidad("auditoria_administradores"));
        }
    }

    @Test
    void datosInvalidosNoDejanUnaCuentaNiUnaAuditoriaParcial() throws Exception {
        try (var base = new Base()) {
            var inicializar = new InicializarAdministracion(base.repositorio,
                new ProtectorClavesArgon2(), Clock.fixed(AHORA, ZoneOffset.UTC));
            assertThrows(IllegalArgumentException.class, () -> inicializar.ejecutar("1invalido", "Responsable", "Una frase ficticia larga".toCharArray()));
            assertThrows(IllegalArgumentException.class, () -> inicializar.ejecutar("admin", " ", "Una frase ficticia larga".toCharArray()));
            assertThrows(IllegalArgumentException.class, () -> inicializar.ejecutar("admin", "Responsable", "corta".toCharArray()));
            assertEquals(0, base.cantidad("administradores"));
            assertEquals(0, base.cantidad("auditoria_administradores"));
        }
    }

    private static final class Base implements AutoCloseable {
        final DriverManagerDataSource fuente;
        final JdbcTemplate jdbc;
        final RepositorioAdministradoresJdbc repositorio;
        final Connection esquema;

        Base() throws Exception {
            fuente = new DriverManagerDataSource("jdbc:h2:mem:administradores_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
                + ";INIT=CREATE SCHEMA IF NOT EXISTS nivora\\;SET SCHEMA nivora", "sa", "");
            jdbc = new JdbcTemplate(fuente);
            esquema = fuente.getConnection();
            aplicar();
            repositorio = nuevoRepositorio();
        }

        void aplicar() { new ResourceDatabasePopulator(new FileSystemResource("base-datos/07-administradores.sql")).populate(esquema); }
        RepositorioAdministradoresJdbc nuevoRepositorio() { return new RepositorioAdministradoresJdbc(jdbc, new DataSourceTransactionManager(fuente)); }
        void primero() { repositorio.crearPrimero(new UsuarioAdministrador("admin"), "Responsable", HASH_PRUEBA, AHORA); }
        long cantidad(String tabla) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Long.class); }
        @Override public void close() throws Exception { esquema.close(); jdbc.execute("SHUTDOWN"); }
    }
}
