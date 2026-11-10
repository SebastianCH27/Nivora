package pe.nivora.empresaagua.simulacion;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

class LecturasContinuasTests {

    private JdbcTemplate jdbc;
    private Connection conexion;
    private TransactionTemplate transaccion;
    private PublicadorLecturasContinuas publicador;
    private GeneradorConsumoContinuo generador;

    @BeforeEach
    void preparar() throws Exception {
        var ds = new DriverManagerDataSource(
            "jdbc:h2:mem:continua_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        conexion = ds.getConnection();
        jdbc = new JdbcTemplate(ds);
        var gestor = new DataSourceTransactionManager(ds);
        transaccion = new TransactionTemplate(gestor);
        publicador = new PublicadorLecturasContinuas(jdbc, 300, 30);
        generador = new GeneradorConsumoContinuo(jdbc, gestor, publicador);
        jdbc.execute("CREATE TABLE suministros (id_suministro BIGINT PRIMARY KEY)");
        jdbc.execute("""
            CREATE TABLE medidores (
                id_medidor BIGINT PRIMARY KEY, id_suministro BIGINT NOT NULL,
                fecha_instalacion DATE NOT NULL, fecha_retiro DATE,
                UNIQUE(id_medidor, id_suministro))
            """);
        jdbc.execute("""
            CREATE TABLE lecturas (
                id_lectura BIGINT AUTO_INCREMENT PRIMARY KEY,
                id_suministro BIGINT NOT NULL, id_medidor BIGINT NOT NULL,
                fecha_medicion DATETIME(6) NOT NULL,
                lectura_acumulada_m3 DECIMAL(15,3) NOT NULL,
                UNIQUE(id_medidor, id_suministro, fecha_medicion),
                FOREIGN KEY (id_medidor, id_suministro)
                    REFERENCES medidores(id_medidor, id_suministro))
            """);
        jdbc.execute("""
            CREATE TABLE consumos_mensuales (
                id_suministro BIGINT NOT NULL, periodo DATE NOT NULL,
                consumo_litros DECIMAL(15,3) NOT NULL,
                fecha_actualizacion DATETIME(6) NOT NULL, cerrado_en DATETIME(6),
                PRIMARY KEY(id_suministro, periodo))
            """);
        jdbc.execute("""
            CREATE TABLE control_consumo (
                id_control TINYINT PRIMARY KEY,
                actualizado_hasta DATETIME(6), semilla BIGINT NOT NULL)
            """);
        jdbc.execute("""
            CREATE TABLE control_emision_lecturas (
                id_control TINYINT PRIMARY KEY, ultima_emision_utc DATETIME(6))
            """);
        jdbc.update("INSERT INTO control_consumo VALUES (1, '2026-10-01 05:00:00', 2026)");
        jdbc.update("INSERT INTO control_emision_lecturas VALUES (1, NULL)");
        suministro(1);
    }

    private void suministro(long id) {
        jdbc.update("INSERT INTO suministros VALUES (?)", id);
        jdbc.update("INSERT INTO medidores VALUES (?, ?, '2026-09-01', NULL)", id, id);
        jdbc.update("INSERT INTO lecturas (id_suministro, id_medidor, fecha_medicion, lectura_acumulada_m3) VALUES (?, ?, '2026-10-01 05:00:00', 100)", id, id);
        jdbc.update("INSERT INTO consumos_mensuales VALUES (?, '2026-10-01', 0, '2026-10-01 05:00:00', NULL)", id);
    }

    @AfterEach
    void cerrar() throws Exception {
        jdbc.execute("SHUTDOWN");
        conexion.close();
    }

    @Test
    void lecturaNuevaCoincideConTotalSinSumarDeNuevoLasLecturas() {
        Instant fin = Instant.parse("2026-10-01T18:00:00Z");
        generador.actualizarHasta(fin);
        BigDecimal mensual = mensual("2026-10-01");
        assertTrue(mensual.signum() > 0);
        BigDecimal medido = ultimo().subtract(new BigDecimal("100.000"))
            .movePointRight(3);
        assertTrue(mensual.subtract(medido).signum() >= 0);
        assertTrue(mensual.subtract(medido).compareTo(BigDecimal.ONE) < 0);
        assertEquals(2, cantidad());
        generador.actualizarHasta(fin);
        assertEquals(2, cantidad());
        assertEquals(mensual, mensual("2026-10-01"));
    }

    @Test
    void respetaIntervaloAunqueElConsumoSigaAvanzando() {
        Instant primera = Instant.parse("2026-10-01T18:00:00Z");
        generador.actualizarHasta(primera);
        generador.actualizarHasta(primera.plusSeconds(100));
        assertEquals(2, cantidad());
        assertEquals(LocalDateTime.parse("2026-10-01T18:00:00"), marca());
        assertEquals(LocalDateTime.parse("2026-10-01T18:01:40"), control());
    }

    @Test
    void noEmiteFilasSinCambioYConservaFraccionesEnElTotalMensual() {
        jdbc.update("UPDATE consumos_mensuales SET consumo_litros=12.875");
        publicar("2026-10-01T18:00:00Z", false);
        assertEquals(new BigDecimal("100.012"), ultimo());
        publicar("2026-10-01T18:05:00Z", false);
        assertEquals(2, cantidad());
        assertEquals(new BigDecimal("12.875"), mensual("2026-10-01"));
        assertEquals(LocalDateTime.parse("2026-10-01T18:05:00"), marca());
    }

    @Test
    void cerrarMesConservaContadorYProporcionaBaseParaElNuevoMes() {
        generador.actualizarHasta(Instant.parse("2026-10-31T23:59:50Z"));
        generador.actualizarHasta(Instant.parse("2026-11-01T04:59:50Z"));
        // El cierre debe emitirse aunque no hayan pasado los 300 segundos.
        generador.actualizarHasta(Instant.parse("2026-11-01T05:00:00Z"));
        BigDecimal cierre = ultimo();
        assertTrue(cierre.compareTo(new BigDecimal("100.000")) > 0);
        assertEquals(mensual("2026-10-01"), cierre.subtract(new BigDecimal("100.000")).movePointRight(3).setScale(3));
        assertEquals(new BigDecimal("0.000"), mensual("2026-11-01"));
        assertNotNull(jdbc.queryForObject("SELECT cerrado_en FROM consumos_mensuales WHERE periodo='2026-10-01'", LocalDateTime.class));
        assertEquals(LocalDateTime.parse("2026-11-01T05:00:00"), marca());
        generador.actualizarHasta(Instant.parse("2026-11-01T18:00:00Z"));
        assertTrue(ultimo().compareTo(cierre) > 0);
        BigDecimal diferencia = mensual("2026-11-01").subtract(ultimo().subtract(cierre).movePointRight(3));
        assertTrue(diferencia.signum() >= 0 && diferencia.compareTo(BigDecimal.ONE) < 0);
    }

    @Test
    void cierreSinIncrementoTambienGeneraUnaBase() {
        publicar("2026-11-01T05:00:00Z", true);
        assertEquals(2, cantidad());
        assertEquals(new BigDecimal("100.000"), ultimo());
    }

    @Test
    void falloDeLecturasRevierteConsumoControlYLecturasDelMismoCiclo() {
        // El suministro 1001 falla despues de insertar el primer lote.
        for (int id = 2; id <= 1001; id++) {
            suministro(id);
        }
        jdbc.update("DELETE FROM lecturas WHERE id_suministro=1001");
        assertThrows(IllegalStateException.class, () ->
            generador.actualizarHasta(Instant.parse("2026-10-01T18:00:00Z")));
        assertEquals(1000, cantidad());
        assertEquals(new BigDecimal("0.000"), mensual("2026-10-01"));
        assertEquals(LocalDateTime.parse("2026-10-01T05:00:00"), control());
        assertNull(marca());
    }

    @Test
    void publicaTodosLosLotesYConservaCadaSuministro() {
        for (int id = 2; id <= 1001; id++) {
            suministro(id);
        }
        jdbc.update("UPDATE consumos_mensuales SET consumo_litros=12.875");
        publicar("2026-10-01T18:00:00Z", false);
        assertEquals(2002, cantidad());
        assertEquals(1001, jdbc.queryForObject("SELECT COUNT(*) FROM lecturas WHERE fecha_medicion='2026-10-01 18:00:00' AND lectura_acumulada_m3=100.012", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lecturas WHERE id_suministro=1001 AND fecha_medicion='2026-10-01 18:00:00'", Integer.class));
    }

    @Test
    void seleccionaLaUltimaFechaAunqueUnaLecturaAntiguaTengaMayorId() {
        jdbc.update("INSERT INTO lecturas (id_suministro,id_medidor,fecha_medicion,lectura_acumulada_m3) VALUES (1,1,'2026-10-01 17:00:00',100.010)");
        jdbc.update("INSERT INTO lecturas (id_suministro,id_medidor,fecha_medicion,lectura_acumulada_m3) VALUES (1,1,'2026-10-01 16:00:00',100.001)");
        jdbc.update("UPDATE consumos_mensuales SET consumo_litros=12.875");
        publicar("2026-10-01T18:00:00Z", false);
        assertEquals(4, cantidad());
        assertEquals(new BigDecimal("100.012"), ultimo());
    }

    @Test
    void exigeUnSoloMedidorActivoPorSuministro() {
        jdbc.update("INSERT INTO medidores VALUES (2, 1, '2026-09-01', NULL)");
        assertThrows(IllegalStateException.class, () ->
            generador.actualizarHasta(Instant.parse("2026-10-01T18:00:00Z")));
        assertEquals(new BigDecimal("0.000"), mensual("2026-10-01"));
        assertNull(marca());
    }

    @Test
    void rechazaLecturaFuturaYContadorQueRetrocede() {
        jdbc.update("INSERT INTO lecturas (id_suministro,id_medidor,fecha_medicion,lectura_acumulada_m3) VALUES (1,1,'2026-10-02 05:00:00',999)");
        assertThrows(IllegalStateException.class, () -> publicar("2026-10-01T18:00:00Z", false));
        assertNull(marca());
        jdbc.update("UPDATE lecturas SET fecha_medicion='2026-10-01 17:00:00' WHERE lectura_acumulada_m3=999");
        assertThrows(IllegalStateException.class, () -> publicar("2026-10-01T18:00:00Z", false));
    }

    @Test
    void noReemplazaUnaLecturaConLaMismaFechaYDistintoValor() {
        jdbc.update("UPDATE consumos_mensuales SET consumo_litros=1");
        publicar("2026-10-01T18:00:00Z", false);
        jdbc.update("UPDATE consumos_mensuales SET consumo_litros=2");
        assertThrows(IllegalStateException.class, () -> publicar("2026-10-01T18:00:00Z", true));
        assertEquals(2, cantidad());
        assertEquals(new BigDecimal("100.001"), ultimo());
    }

    @Test
    void reinicioDelProcesoConservaLaFechaDeEmision() {
        generador.actualizarHasta(Instant.parse("2026-10-01T18:00:00Z"));
        var gestor = new DataSourceTransactionManager(jdbc.getDataSource());
        var otro = new GeneradorConsumoContinuo(jdbc, gestor, new PublicadorLecturasContinuas(jdbc, 300, 30));
        otro.actualizarHasta(Instant.parse("2026-10-01T18:00:10Z"));
        assertEquals(2, cantidad());
        otro.actualizarHasta(Instant.parse("2026-10-02T18:00:00Z"));
        assertTrue(cantidad() > 2);
        assertEquals(LocalDateTime.parse("2026-10-02T18:00:00"), marca());
    }

    @Test
    void exigeControlPreparadoYTransaccionCompartida() {
        assertThrows(IllegalStateException.class, () -> publicador.publicar(
            LocalDate.parse("2026-10-01"), Instant.parse("2026-10-01T18:00:00Z"), false));
        jdbc.update("DELETE FROM control_emision_lecturas");
        assertThrows(IllegalStateException.class, () -> publicar("2026-10-01T18:00:00Z", false));
        assertEquals(1, cantidad());
    }

    @Test
    void generadorDiarioQuedaBloqueadoDespuesDePrimeraEmision() {
        generador.actualizarHasta(Instant.parse("2026-10-01T18:00:00Z"));
        var diario = new GeneradorLecturasDiarias(jdbc,
            new DataSourceTransactionManager(jdbc.getDataSource()));
        IllegalStateException fallo = assertThrows(IllegalStateException.class,
            () -> diario.generarHasta(LocalDate.parse("2026-10-02")));
        assertTrue(fallo.getMessage().contains("continua ya comenzo"));
    }

    private void publicar(String fecha, boolean cierre) {
        transaccion.executeWithoutResult(estado -> publicador.publicar(
            LocalDate.parse("2026-10-01"), Instant.parse(fecha), cierre));
    }

    private int cantidad() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM lecturas", Integer.class);
    }

    private BigDecimal ultimo() {
        return jdbc.queryForObject("SELECT lectura_acumulada_m3 FROM lecturas WHERE id_suministro=1 ORDER BY fecha_medicion DESC LIMIT 1", BigDecimal.class);
    }

    private BigDecimal mensual(String mes) {
        return jdbc.queryForObject("SELECT consumo_litros FROM consumos_mensuales WHERE id_suministro=1 AND periodo=?", BigDecimal.class, LocalDate.parse(mes));
    }

    private LocalDateTime control() {
        return jdbc.queryForObject("SELECT actualizado_hasta FROM control_consumo", LocalDateTime.class);
    }

    private LocalDateTime marca() {
        return jdbc.queryForObject("SELECT ultima_emision_utc FROM control_emision_lecturas", LocalDateTime.class);
    }
}
