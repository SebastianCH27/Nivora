package pe.nivora.backend.lecturas.infraestructura.persistencia;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import pe.nivora.backend.lecturas.dominio.*;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioLecturasNoDisponibleException;
import pe.nivora.backend.lecturas.soporte.BaseDatosRecuperacion;
import static pe.nivora.backend.lecturas.soporte.BaseDatosRecuperacion.*;
import static org.junit.jupiter.api.Assertions.*;

class LecturasIntervaloJdbcTests {
    private static final Instant INICIO = Instant.parse("2026-10-01T05:00:00Z");
    private BaseDatosRecuperacion base;
    @BeforeEach void preparar() { base = new BaseDatosRecuperacion(); }
    @AfterEach void cerrar() { base.close(); }

    @Test void guardaRepiteYRecuperaElIntervaloSinCambiarLaPrimeraRecepcion() {
        var lectura = intervalo("1", 0, 3600, 80);
        assertEquals(new ResultadoIncorporacion(1,0,0), incorporar(lectura));
        var fecha = base.jdbc.queryForObject("SELECT recibida_en_utc FROM lecturas", java.time.LocalDateTime.class);
        assertEquals(new ResultadoIncorporacion(0,1,0), base.lecturas.incorporar(lote(lectura), AHORA.plusSeconds(10)));
        assertEquals(lectura, base.lecturas.buscar("empresa-agua","1").orElseThrow());
        assertEquals(fecha, base.jdbc.queryForObject("SELECT recibida_en_utc FROM lecturas", java.time.LocalDateTime.class));
        assertEquals("INTERVALO", base.jdbc.queryForObject("SELECT tipo_medicion FROM lecturas",String.class));
    }

    @Test void aceptaContiguosYConservaUnaIncidenciaAlRepetirUnSolape() {
        assertEquals(new ResultadoIncorporacion(2,0,0), incorporar(intervalo("1",0,3600,80), intervalo("2",3600,7200,120)));
        var solape = intervalo("3", 1800, 5400, 500);
        assertEquals(new ResultadoIncorporacion(0,0,1), incorporar(solape));
        assertEquals(new ResultadoIncorporacion(0,0,1), incorporar(solape));
        assertEquals(2, base.lecturas.contar(CODIGO));
        assertEquals(1, base.lecturas.contarIncidencias(CODIGO));
        assertEquals(2, base.jdbc.queryForObject("SELECT repeticiones FROM incidencias_lecturas",Integer.class));
        assertEquals("INTERVALO_SUPERPUESTO", base.jdbc.queryForObject("SELECT motivo FROM incidencias_lecturas",String.class));
        assertTrue(base.jdbc.queryForObject("SELECT datos_recibidos FROM incidencias_lecturas",String.class).contains("inicioIntervalo"));
    }

    @Test void otroIdParaLaMismaMedicionYUnIdConOtroInicioNoReemplazanElAceptado() {
        var original = intervalo("1",0,3600,80);
        incorporar(original);
        assertEquals(new ResultadoIncorporacion(0,0,1), incorporar(intervalo("2",0,3600,80)));
        assertEquals(new ResultadoIncorporacion(0,0,1), incorporar(intervalo("1",-1,3600,80)));
        assertEquals(original, base.lecturas.buscar("empresa-agua","1").orElseThrow());
        assertEquals(1, base.lecturas.contar(CODIGO));
        assertEquals(2, base.lecturas.contarIncidencias(CODIGO));
    }

    @Test void dosTrabajadoresNoPuedenAceptarIntervalosSuperpuestos() throws Exception {
        var inicio = new CountDownLatch(1);
        try (var ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = ejecutor.submit(() -> { inicio.await(); return incorporar(intervalo("1",0,4000,80)); });
            var b = ejecutor.submit(() -> { inicio.await(); return incorporar(intervalo("2",3600,7200,120)); });
            inicio.countDown();
            var ra=a.get();var rb=b.get();
            assertEquals(1,ra.nuevas()+rb.nuevas());
            assertEquals(1,ra.rechazadas()+rb.rechazadas());
        }
        assertEquals(1,base.lecturas.contar(CODIGO));
    }

    @Test void elFalloDeLaIncidenciaRevierteLaPaginaCompleta() {
        base.jdbc.execute("ALTER TABLE incidencias_lecturas ADD CONSTRAINT impedir_solape CHECK (motivo <> 'INTERVALO_SUPERPUESTO')");
        assertThrows(RepositorioLecturasNoDisponibleException.class,
            () -> incorporar(intervalo("1",0,4000,80),intervalo("2",3600,7200,120)));
        assertEquals(0,base.lecturas.contar(CODIGO));
        assertEquals(0,base.lecturas.contarIncidencias(CODIGO));
    }

    @Test void elConsumoDeIntervaloNoSeComparaConLosContadoresAcumulados() {
        var primero = new LecturaAcumulada("empresa-agua","1",CODIGO,"391",INICIO,1000);
        var ultimo = new LecturaAcumulada("empresa-agua","3",CODIGO,"391",INICIO.plusSeconds(10800),1100);
        assertEquals(new ResultadoIncorporacion(3,0,0), incorporar(primero,intervalo("2",3600,7200,80),ultimo));
        var datos = consulta().consultar("empresa-agua",CODIGO,INICIO,INICIO.plusSeconds(10800)).orElseThrow();
        assertEquals(ConsumoPeriodo.Motivo.TIPOS_DE_MEDICION_MEZCLADOS,
            ConsumoPeriodo.calcular(INICIO,INICIO.plusSeconds(10800),datos.lecturas()).motivo());
    }

    @Test void consultaCrucesDeMesSoloCuandoYaFueronMedidos() {
        var finMes=Instant.parse("2026-11-01T05:00:00Z");
        var lectura=new LecturaIntervalo("empresa-agua","1",CODIGO,"391",finMes.minusSeconds(3600),finMes.plusSeconds(3600),100);
        incorporar(lectura);
        assertTrue(consulta().consultar("empresa-agua",CODIGO,INICIO,finMes).orElseThrow().lecturas().isEmpty());
        var datos=consulta().consultar("empresa-agua",CODIGO,INICIO,finMes,AHORA).orElseThrow();
        assertEquals(List.of(lectura),datos.lecturas());
        var consumo=ConsumoPeriodo.calcular(INICIO,finMes,datos.lecturas());
        assertEquals(ConsumoPeriodo.Motivo.INTERVALO_CRUZA_LIMITE,consumo.motivo());
        assertNull(consumo.litros());
    }

    @Test void validaAmbosExtremosContraRecepcionPrecisionEInventario() {
        var contexto=new ContextoLecturas("empresa-agua",CODIGO,"10",AHORA,
            List.of(new ContextoLecturas.Medidor("391",LocalDate.of(2026,10,2),null)));
        assertEquals(new ResultadoIncorporacion(0,0,1),base.lecturas.incorporar(lote(intervalo("1",0,86400*2L,10)),AHORA,contexto));
        var precision=new LecturaIntervalo("empresa-agua","2",CODIGO,"391",INICIO.plusNanos(1),INICIO.plusSeconds(3600),10);
        assertEquals(new ResultadoIncorporacion(0,0,1),incorporar(precision));
        var futuro=new LecturaIntervalo("empresa-agua","3",CODIGO,"391",AHORA,AHORA.plusSeconds(1),10);
        assertEquals(new ResultadoIncorporacion(0,0,1),incorporar(futuro));
        assertEquals(0,base.lecturas.contar(CODIGO));
        assertEquals(3,base.lecturas.contarIncidencias(CODIGO));
    }

    @Test void lasRestriccionesDeLaBaseProtegenElTipoYLosExtremos() {
        incorporar(intervalo("1",0,3600,80));
        assertThrows(DataIntegrityViolationException.class, () -> base.jdbc.update("UPDATE lecturas SET inicio_intervalo_utc=NULL"));
        assertThrows(DataIntegrityViolationException.class, () -> base.jdbc.update("UPDATE lecturas SET inicio_intervalo_utc=fecha_medicion_utc"));
        assertThrows(DataIntegrityViolationException.class, () -> base.jdbc.update("UPDATE lecturas SET tipo_medicion='ACUMULADA'"));
        assertEquals(80,base.jdbc.queryForObject("SELECT valor_litros FROM lecturas",Integer.class));
    }

    private ConsultaLecturasPeriodoJdbc consulta() { return new ConsultaLecturasPeriodoJdbc(base.jdbc); }
    private ResultadoIncorporacion incorporar(Lectura... lecturas) { return base.lecturas.incorporar(lote(lecturas),AHORA); }
    private static LoteLecturas lote(Lectura... lecturas) { return new LoteLecturas("empresa-agua",CODIGO,List.of(lecturas),lecturas[lecturas.length-1].idLectura(),false); }
    private static LecturaIntervalo intervalo(String id,long inicio,long fin,long litros) {
        return new LecturaIntervalo("empresa-agua",id,CODIGO,"391",INICIO.plusSeconds(inicio),INICIO.plusSeconds(fin),litros);
    }
}
