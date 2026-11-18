package pe.nivora.backend.notificaciones.infraestructura;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.notificaciones.aplicacion.DespacharWebPush;
import pe.nivora.backend.notificaciones.dominio.EmisorWebPush;
import pe.nivora.backend.notificaciones.dominio.RepositorioConfiguracionNotificaciones.Permiso;
import pe.nivora.backend.notificaciones.dominio.RepositorioConfiguracionNotificaciones.Preferencia;
import pe.nivora.backend.notificaciones.dominio.RepositorioWebPush;

import static org.junit.jupiter.api.Assertions.*;
import static pe.nivora.backend.compartido.infraestructura.sincronizacion.ProgramacionSincronizacionJdbc.utc;

class ColaWebPushTests {
    private static final Instant AHORA = Instant.parse("2026-11-16T12:00:00Z");
    private static final CodigoSuministro SUMINISTRO = new CodigoSuministro("13100390");
    private final UUID dispositivo = UUID.randomUUID();
    private JdbcTemplate jdbc;
    private Connection esquema;
    private RepositorioWebPush cola;
    private RepositorioConfiguracionNotificacionesJdbc preferencias;
    private EmisorPrueba emisor;

    @BeforeEach
    void preparar() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:webpush_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS nivora\\;SET SCHEMA nivora",
            "sa", "");
        var migraciones = new String[]{"02-suministros.sql", "07-administradores.sql", "12-recibos.sql",
            "14-alertas-consumo.sql", "15-avisos-recibo.sql", "17-configuracion-notificaciones.sql", "18-entregas-web-push.sql"};
        var poblador = new ResourceDatabasePopulator();
        for (var archivo : migraciones) poblador.addScript(new FileSystemResource("base-datos/" + archivo));
        // H2 conserva la sesion de creacion al evaluar restricciones IN.
        esquema = ds.getConnection();
        poblador.populate(esquema);
        jdbc = new JdbcTemplate(ds);
        var transacciones = new DataSourceTransactionManager(ds);
        cola = new RepositorioWebPushJdbc(jdbc, transacciones);
        preferencias = new RepositorioConfiguracionNotificacionesJdbc(jdbc, transacciones);
        emisor = new EmisorPrueba();
        suministro(SUMINISTRO);
    }

    @AfterEach
    void cerrarEsquema() throws Exception {
        if (esquema != null) esquema.close();
    }

    @Test
    void reintentaYConservaUnSoloRegistroPorAviso() throws Exception {
        asociar("uno");
        alerta("2026-10-01", AHORA.minusSeconds(1));
        alerta("2026-11-01", AHORA);
        emisor.respuestas.add(new EmisorWebPush.Respuesta(EmisorWebPush.Resultado.TEMPORAL, AHORA.plusSeconds(90)));
        emisor.respuestas.add(new EmisorWebPush.Respuesta(EmisorWebPush.Resultado.ACEPTADA, null));
        despachar(AHORA);
        assertEquals("PENDIENTE", estado());
        despachar(AHORA.plusSeconds(89));
        assertEquals(1, emisor.envios);
        despachar(AHORA.plusSeconds(90));
        despachar(AHORA.plusSeconds(91));
        assertEquals("ACEPTADA", estado());
        assertEquals(2, jdbc.queryForObject("SELECT intentos FROM entregas_web_push", Integer.class));
        assertEquals(2, emisor.envios);
    }

    @Test
    void agotaSeisIntentosTemporalesYConservaLaEntregaFallidaSinDuplicarla() throws Exception {
        asociar("agotada");
        alerta("2026-11-01", AHORA);
        Instant ahora = AHORA;
        for (int intento = 0; intento < 6; intento++) {
            emisor.respuestas.add(new EmisorWebPush.Respuesta(EmisorWebPush.Resultado.TEMPORAL, null));
            despachar(ahora);
            assertEquals(intento + 1, emisor.envios);
            assertEquals(intento + 1, jdbc.queryForObject("SELECT intentos FROM entregas_web_push", Integer.class));
            ahora = ahora.plusSeconds(30L << intento);
        }
        assertEquals("FALLIDA", estado());
        despachar(AHORA.plusSeconds(7200));
        assertEquals(6, emisor.envios);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM entregas_web_push", Integer.class));
    }

    @Test
    void unaReservaVencidaNoPuedeConfirmarLaEntregaReservadaPorOtroTrabajador() throws Exception {
        asociar("reserva-vencida");
        alerta("2026-11-01", AHORA);
        cola.encolar(AHORA);
        var primera = cola.reservar(AHORA).orElseThrow();
        var otraInstancia = new RepositorioWebPushJdbc(jdbc, new DataSourceTransactionManager(jdbc.getDataSource()));
        assertTrue(otraInstancia.reservar(AHORA.plusSeconds(59)).isEmpty());
        var nueva = otraInstancia.reservar(AHORA.plusSeconds(60)).orElseThrow();
        assertEquals(primera.idSuscripcion(), nueva.idSuscripcion());
        assertNotEquals(primera.token(), nueva.token());
        assertFalse(cola.vigente(primera, AHORA.plusSeconds(60)));
        assertTrue(otraInstancia.vigente(nueva, AHORA.plusSeconds(60)));

        cola.finalizar(primera, RepositorioWebPush.Estado.ACEPTADA, AHORA.plusSeconds(60), AHORA.plusSeconds(60));
        assertEquals("PENDIENTE", estado());
        assertEquals(0, jdbc.queryForObject("SELECT intentos FROM entregas_web_push", Integer.class));
        otraInstancia.finalizar(nueva, RepositorioWebPush.Estado.ACEPTADA, AHORA.plusSeconds(60), AHORA.plusSeconds(60));
        cola.encolar(AHORA.plusSeconds(90));
        assertEquals("ACEPTADA", estado());
        assertEquals(1, jdbc.queryForObject("SELECT intentos FROM entregas_web_push", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM entregas_web_push", Integer.class));
        assertTrue(cola.reservar(AHORA.plusSeconds(90)).isEmpty());
    }

    @Test
    void expiraSuscripcionYDescartaLosEnviosPendientes() throws Exception {
        asociar("expirada");
        alerta("2026-11-01", AHORA);
        emisor.respuestas.add(new EmisorWebPush.Respuesta(EmisorWebPush.Resultado.EXPIRADA, null));
        despachar(AHORA);
        assertEquals("EXPIRADA", estado());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM suscripciones_web_push WHERE expirada_en_utc IS NOT NULL", Integer.class));
        despachar(AHORA.plusSeconds(90));
        assertEquals(1, emisor.envios);
        cola.eliminar(SUMINISTRO, dispositivo);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM entregas_web_push", Integer.class));
    }

    @Test
    void respetaPreferenciasYEvitaReasignarElEndpoint() throws Exception {
        var suscripcion = EmisorWebPushHttpTests.suscripcion("compartido");
        assertEquals(RepositorioWebPush.Registro.PREFERENCIA_INACTIVA,
            cola.suscribir(SUMINISTRO, dispositivo, suscripcion, AHORA));
        preferencia(SUMINISTRO);
        assertEquals(RepositorioWebPush.Registro.GUARDADA, cola.suscribir(SUMINISTRO, dispositivo, suscripcion, AHORA));
        var otro = new CodigoSuministro("200");
        suministro(otro);
        preferencia(otro);
        assertEquals(RepositorioWebPush.Registro.ENDPOINT_OCUPADO,
            cola.suscribir(otro, dispositivo, suscripcion, AHORA));
        cola.eliminar(otro, dispositivo);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM suscripciones_web_push", Integer.class));
        alerta("2026-11-01", AHORA);
        cola.encolar(AHORA);
        var reserva = cola.reservar(AHORA).orElseThrow();
        assertTrue(cola.reservar(AHORA).isEmpty());
        jdbc.update("UPDATE preferencias_notificaciones_dispositivo SET habilitadas = FALSE WHERE codigo_suministro = ?",
            SUMINISTRO.valor());
        assertFalse(cola.vigente(reserva, AHORA));
        despachar(AHORA.plusSeconds(70));
        assertEquals(0, emisor.envios);
    }

    private void asociar(String id) throws Exception {
        preferencia(SUMINISTRO);
        assertEquals(RepositorioWebPush.Registro.GUARDADA,
            cola.suscribir(SUMINISTRO, dispositivo, EmisorWebPushHttpTests.suscripcion(id), AHORA));
    }

    private void suministro(CodigoSuministro codigo) {
        jdbc.update("INSERT INTO suministros VALUES (?, 'Prueba', ?)", codigo.valor(), utc(AHORA));
    }

    private void preferencia(CodigoSuministro codigo) {
        preferencias.guardarPreferencia(codigo, new Preferencia(dispositivo, Permiso.CONCEDIDO, true, AHORA));
    }

    private void alerta(String periodo, Instant registrada) {
        jdbc.update("INSERT INTO alertas_consumo VALUES (?, CAST(? AS DATE), 2200, 2000, ?, ?)",
            SUMINISTRO.valor(), periodo, utc(registrada), utc(registrada));
    }

    private String estado() {
        return jdbc.queryForObject("SELECT estado FROM entregas_web_push", String.class);
    }

    private void despachar(Instant ahora) throws InterruptedException {
        new DespacharWebPush(cola, emisor, Clock.fixed(ahora, ZoneOffset.UTC)).ejecutar();
    }

    private static class EmisorPrueba implements EmisorWebPush {
        private final ArrayDeque<Respuesta> respuestas = new ArrayDeque<>();
        private int envios;

        @Override
        public String clavePublica() {
            return "";
        }

        @Override
        public Respuesta enviar(RepositorioWebPush.Reserva reserva, Instant ahora) {
            envios++;
            return respuestas.removeFirst();
        }
    }
}
