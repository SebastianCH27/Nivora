package pe.nivora.backend.lecturas.sincronizacion;

import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.FileSystemResource;
import pe.nivora.backend.lecturas.dominio.*;
import pe.nivora.backend.lecturas.infraestructura.persistencia.RepositorioSincronizacionJdbc;
import pe.nivora.backend.lecturas.soporte.BaseDatosRecuperacion;
import static pe.nivora.backend.lecturas.soporte.BaseDatosRecuperacion.*;
import static org.junit.jupiter.api.Assertions.*;

class ProgramacionSincronizacionTests {
    private BaseDatosRecuperacion base;
    private RepositorioSincronizacionJdbc repo;
    private static final Duration RESERVA=Duration.ofSeconds(180);
    @BeforeEach void iniciar() {
        base=new BaseDatosRecuperacion(); repo=nuevaInstancia();
    }
    @AfterEach void cerrar(){base.close();}
    private RepositorioSincronizacionJdbc nuevaInstancia(){
        return new RepositorioSincronizacionJdbc(base.jdbc,
            new DataSourceTransactionManager(base.jdbc.getDataSource()));
    }
    private ReservaSincronizacion reservar(Instant ahora){
        return repo.reservar("empresa-agua",ahora,ahora,RESERVA).orElseThrow();
    }
    private void preparar(){repo.prepararSuministros("empresa-agua",AHORA);}

    @Test void registroEsIdempotenteYConservaLaProgramacionAnterior(){
        preparar();var reserva=reservar(AHORA);
        assertTrue(repo.finalizar(reserva,EstadoSincronizacion.ERROR_FUENTE,2,AHORA.plusSeconds(60),AHORA));
        repo.prepararSuministros("empresa-agua",AHORA.plusSeconds(10));
        assertEquals(1,base.jdbc.queryForObject("SELECT COUNT(*) FROM programacion_sincronizacion_lecturas",Integer.class));
        assertEquals(2,base.jdbc.queryForObject("SELECT fallos_consecutivos FROM programacion_sincronizacion_lecturas",Integer.class));
        assertTrue(repo.reservar("empresa-agua",AHORA.plusSeconds(10),AHORA.plusSeconds(10),RESERVA).isEmpty());
    }

    @Test void incorporaSuministrosNuevosSinReprogramarLosExistentes(){
        preparar();var r=reservar(AHORA);
        repo.finalizar(r,EstadoSincronizacion.RECORRIDO_COMPLETO,0,AHORA.plusSeconds(300),AHORA);
        base.jdbc.update("INSERT INTO suministros VALUES ('13100391','Otro titular',?)",LocalDateTime.ofInstant(AHORA,ZoneOffset.UTC));
        repo.prepararSuministros("empresa-agua",AHORA.plusSeconds(1));
        assertEquals("13100391",reservar(AHORA.plusSeconds(1)).suministro().valor());
    }

    @Test void unaReservaVigenteExcluyeOtroIntento(){
        preparar();reservar(AHORA);
        assertTrue(nuevaInstancia().reservar("empresa-agua",AHORA,AHORA,RESERVA).isEmpty());
    }

    @Test void dosProcesosNoReservanElMismoSuministro()throws Exception{
        preparar();var inicio=new CountDownLatch(1);
        try(var hilos=Executors.newFixedThreadPool(2)){
            Callable<Boolean> tarea=()->{inicio.await();return nuevaInstancia().reservar("empresa-agua",AHORA,AHORA,RESERVA).isPresent();};
            var a=hilos.submit(tarea);var b=hilos.submit(tarea);inicio.countDown();
            assertEquals(1,(a.get(10,TimeUnit.SECONDS)?1:0)+(b.get(10,TimeUnit.SECONDS)?1:0));
        }
    }

    @Test void distintosProcesosPuedenReservarSuministrosDiferentes(){
        base.jdbc.update("INSERT INTO suministros VALUES ('13100391','Otro titular',?)",LocalDateTime.ofInstant(AHORA,ZoneOffset.UTC));
        preparar();var a=reservar(AHORA);var b=nuevaInstancia().reservar("empresa-agua",AHORA,AHORA,RESERVA).orElseThrow();
        assertNotEquals(a.suministro(),b.suministro());
    }

    @Test void unaReservaVencidaSeRecuperaYElPropietarioAnteriorNoLaSobrescribe(){
        preparar();var antigua=reservar(AHORA);
        var nueva=reservar(AHORA.plusSeconds(181));
        assertNotEquals(antigua.token(),nueva.token());
        assertFalse(repo.finalizar(antigua,EstadoSincronizacion.RECORRIDO_COMPLETO,0,AHORA.plusSeconds(600),AHORA.plusSeconds(181)));
        assertEquals(nueva.token(),base.jdbc.queryForObject("SELECT reserva_token FROM programacion_sincronizacion_lecturas",String.class));
    }

    @Test void unaReservaVencidaTampocoSeConfirmaSiNadieLaHaReasignado(){
        preparar();var r=reservar(AHORA);
        assertFalse(repo.finalizar(r,EstadoSincronizacion.RECORRIDO_COMPLETO,0,AHORA.plusSeconds(600),AHORA.plusSeconds(181)));
        assertNull(base.jdbc.queryForObject("SELECT ultimo_exito_utc FROM programacion_sincronizacion_lecturas",LocalDateTime.class));
    }

    @Test void soloUnRecorridoCompletoActualizaLaFechaDeExito(){
        preparar();var r=reservar(AHORA);
        repo.finalizar(r,EstadoSincronizacion.RECORRIDO_COMPLETO,0,AHORA.plusSeconds(10),AHORA);
        var segundo=reservar(AHORA.plusSeconds(10));
        repo.finalizar(segundo,EstadoSincronizacion.ERROR_FUENTE,1,AHORA.plusSeconds(40),AHORA.plusSeconds(10));
        assertEquals(LocalDateTime.ofInstant(AHORA,ZoneOffset.UTC),base.jdbc.queryForObject("SELECT ultimo_exito_utc FROM programacion_sincronizacion_lecturas",LocalDateTime.class));
    }

    @Test void bloqueoSePausaYUnReinicioManualPermiteReactivarlo(){
        var mala=new LecturaAcumulada("empresa-agua","1",CODIGO,"391",AHORA.plusSeconds(1),100);
        var bloqueado=base.progreso.confirmar(base.progreso.consultar("empresa-agua",CODIGO),
            new LoteLecturas("empresa-agua",CODIGO,List.of(mala),"1",false),AHORA).progreso();
        assertEquals(EstadoRecuperacion.BLOQUEADO,bloqueado.estado());
        preparar();var r=reservar(AHORA);
        repo.finalizar(r,EstadoSincronizacion.BLOQUEADO,0,null,AHORA);
        preparar();assertTrue(repo.reservar("empresa-agua",AHORA,AHORA,RESERVA).isEmpty());
        base.progreso.reiniciar(bloqueado,AHORA);
        preparar();assertTrue(repo.reservar("empresa-agua",AHORA,AHORA,RESERVA).isPresent());
        assertEquals(1,base.lecturas.contarIncidencias(CODIGO));
    }

    @Test void repetirMigracionConservaReservasYFechas(){
        preparar();var r=reservar(AHORA);
        new ResourceDatabasePopulator(new FileSystemResource("base-datos/06-sincronizacion-lecturas.sql")).execute(base.jdbc.getDataSource());
        assertEquals(r.token(),base.jdbc.queryForObject("SELECT reserva_token FROM programacion_sincronizacion_lecturas",String.class));
        assertTrue(repo.reservar("empresa-agua",AHORA,AHORA,RESERVA).isEmpty());
    }

    @Test void cadaCicloRespetaElInstanteDeElegibilidadInicial(){
        preparar();var r=reservar(AHORA);
        repo.finalizar(r,EstadoSincronizacion.EN_CURSO,0,AHORA.plusSeconds(1),AHORA);
        assertTrue(repo.reservar("empresa-agua",AHORA,AHORA.plusSeconds(10),RESERVA).isEmpty());
    }
}
