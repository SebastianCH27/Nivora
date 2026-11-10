package pe.nivora.backend.lecturas.sincronizacion;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.aplicacion.*;
import pe.nivora.backend.lecturas.dominio.*;
import pe.nivora.backend.lecturas.dominio.contratos.*;
import pe.nivora.backend.lecturas.infraestructura.persistencia.RepositorioSincronizacionJdbc;
import pe.nivora.backend.lecturas.soporte.BaseDatosRecuperacion;
import static pe.nivora.backend.lecturas.soporte.BaseDatosRecuperacion.*;
import static org.junit.jupiter.api.Assertions.*;

class SincronizarLecturasTests {
    private BaseDatosRecuperacion base;
    private RepositorioSincronizacionJdbc repo;
    private Reloj reloj;
    private PoliticaSincronizacion politica;
    @BeforeEach void iniciar(){
        base=new BaseDatosRecuperacion();repo=nuevaInstancia();reloj=new Reloj(AHORA);
        politica=new PoliticaSincronizacion(2,2,1,Duration.ofSeconds(180),Duration.ofSeconds(300),
            Duration.ofSeconds(10),Duration.ofSeconds(30),Duration.ofSeconds(120));
    }
    @AfterEach void cerrar(){base.close();}
    private RepositorioSincronizacionJdbc nuevaInstancia(){
        return new RepositorioSincronizacionJdbc(base.jdbc,new DataSourceTransactionManager(base.jdbc.getDataSource()));
    }
    private SincronizarLecturas caso(Function<CodigoSuministro,RecuperarLecturas.Resultado> accion){
        return new SincronizarLecturas("empresa-agua",repo,accion,politica,reloj);
    }
    private RecuperarLecturas.Resultado resultado(CodigoSuministro codigo,EstadoRecuperacion estado){
        return new RecuperarLecturas.Resultado(1,new ResultadoIncorporacion(0,0,0),
            new ProgresoRecuperacion("empresa-agua",codigo,"0",1,estado,null,reloj.instant()));
    }
    private Instant proximo(){return base.jdbc.queryForObject("SELECT proximo_intento_utc FROM programacion_sincronizacion_lecturas WHERE codigo_suministro='13100390'",LocalDateTime.class).toInstant(ZoneOffset.UTC);}
    private int fallos(){return base.jdbc.queryForObject("SELECT fallos_consecutivos FROM programacion_sincronizacion_lecturas WHERE codigo_suministro='13100390'",Integer.class);}

    @Test void completoEsperaHastaElProximoIntervalo(){
        var llamadas=new AtomicInteger();
        var tarea=caso(c->{
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            llamadas.incrementAndGet();return resultado(c,EstadoRecuperacion.RECORRIDO_COMPLETO);
        });
        assertEquals(1,tarea.ejecutarCiclo().size());
        assertEquals(AHORA.plusSeconds(300),proximo());
        assertTrue(tarea.ejecutarCiclo().isEmpty());
        reloj.avanzar(299);assertTrue(tarea.ejecutarCiclo().isEmpty());
        reloj.avanzar(1);assertEquals(1,tarea.ejecutarCiclo().size());
        assertEquals(2,llamadas.get());
    }

    @Test void procesaUnLoteAcotadoYDejaLosDemasPendientes(){
        for(String codigo:List.of("13100391","13100392"))base.jdbc.update("INSERT INTO suministros VALUES (?, 'Otro titular', ?)",codigo,LocalDateTime.ofInstant(AHORA,ZoneOffset.UTC));
        var tarea=caso(c->resultado(c,EstadoRecuperacion.RECORRIDO_COMPLETO));
        assertEquals(2,tarea.ejecutarCiclo().size());
        assertEquals(1,tarea.ejecutarCiclo().size());
    }

    @Test void falloDeUnaFuenteNoImpideAtenderOtroSuministro(){
        base.jdbc.update("INSERT INTO suministros VALUES ('13100391','Otro titular',?)",LocalDateTime.ofInstant(AHORA,ZoneOffset.UTC));
        var intentos=caso(c->{if(c.equals(CODIGO))throw new FuenteLecturasNoDisponibleException("Caida simulada");return resultado(c,EstadoRecuperacion.RECORRIDO_COMPLETO);}).ejecutarCiclo();
        assertEquals(2,intentos.size());
        assertEquals(EstadoSincronizacion.ERROR_FUENTE,intentos.getFirst().estado());
        assertEquals(EstadoSincronizacion.RECORRIDO_COMPLETO,intentos.getLast().estado());
        assertEquals(AHORA.plusSeconds(30),proximo());
    }

    @Test void reintentoExponencialPersisteEntreInstanciasYRespetaElMaximo(){
        Function<CodigoSuministro,RecuperarLecturas.Resultado> caida=c->{throw new FuenteLecturasNoDisponibleException("Caida simulada");};
        int[] esperas={30,60,120,120};
        for(int n=0;n<esperas.length;n++){
            repo=nuevaInstancia();
            assertEquals(1,caso(caida).ejecutarCiclo().size());
            assertEquals(n+1,fallos());
            assertEquals(reloj.instant().plusSeconds(esperas[n]),proximo());
            assertTrue(caso(caida).ejecutarCiclo().isEmpty());
            reloj.avanzar(esperas[n]);
        }
    }

    @Test void unaRecuperacionExitosaRestableceLosFallos(){
        caso(c->{throw new FuenteLecturasNoDisponibleException("Caida simulada");}).ejecutarCiclo();
        reloj.avanzar(30);
        caso(c->resultado(c,EstadoRecuperacion.RECORRIDO_COMPLETO)).ejecutarCiclo();
        assertEquals(0,fallos());assertEquals(reloj.instant().plusSeconds(300),proximo());
    }

    @Test void ausenciaConfirmadaSeReintentaSinEliminarElSuministro(){
        var r=caso(c->resultado(c,EstadoRecuperacion.SUMINISTRO_NO_ENCONTRADO)).ejecutarCiclo().getFirst();
        assertEquals(EstadoSincronizacion.SUMINISTRO_NO_ENCONTRADO,r.estado());
        assertEquals(1,fallos());assertEquals(AHORA.plusSeconds(30),proximo());
        assertEquals(1,base.jdbc.queryForObject("SELECT COUNT(*) FROM suministros",Integer.class));
    }

    @Test void bloqueoSePausaYNoSeConsultaRepetidamente(){
        var llamadas=new AtomicInteger();var tarea=caso(c->{llamadas.incrementAndGet();return resultado(c,EstadoRecuperacion.BLOQUEADO);});
        assertEquals(EstadoSincronizacion.BLOQUEADO,tarea.ejecutarCiclo().getFirst().estado());
        reloj.avanzar(1000);assertTrue(tarea.ejecutarCiclo().isEmpty());assertEquals(1,llamadas.get());
        assertNull(base.jdbc.queryForObject("SELECT proximo_intento_utc FROM programacion_sincronizacion_lecturas",LocalDateTime.class));
    }

    @Test void conflictoDeRevisionSeReprogramaSinContarloComoFalloDeFuente(){
        var r=caso(c->{throw new ProgresoRecuperacionModificadoException();}).ejecutarCiclo().getFirst();
        assertEquals(EstadoSincronizacion.CONFLICTO,r.estado());assertEquals(0,fallos());
        assertEquals(AHORA.plusSeconds(10),proximo());
    }

    @Test void errorDeAlmacenamientoSeReprograma(){
        var r=caso(c->{throw new RepositorioLecturasNoDisponibleException("Fallo simulado",new IllegalStateException());}).ejecutarCiclo().getFirst();
        assertEquals(EstadoSincronizacion.ERROR_ALMACENAMIENTO,r.estado());assertEquals(1,fallos());
    }

    @Test void errorInesperadoNoSeConfundeConRecorridoCompleto(){
        var r=caso(c->{throw new IllegalStateException("Fallo simulado");}).ejecutarCiclo().getFirst();
        assertEquals(EstadoSincronizacion.ERROR_INTERNO,r.estado());
        assertNull(base.jdbc.queryForObject("SELECT ultimo_exito_utc FROM programacion_sincronizacion_lecturas",LocalDateTime.class));
    }

    @Test void respuestaDeOtroSuministroNoConfirmaExito(){
        var r=caso(c->resultado(new CodigoSuministro("13100391"),EstadoRecuperacion.RECORRIDO_COMPLETO)).ejecutarCiclo().getFirst();
        assertEquals(EstadoSincronizacion.ERROR_INTERNO,r.estado());
    }

    @Test void unaReservaVencidaSeReintentaSinConfirmarLaProgramacionAntigua(){
        politica=new PoliticaSincronizacion(1,politica.limitePagina(),politica.maximoPaginas(),
            politica.reserva(),politica.intervaloCompleto(),politica.continuacion(),
            politica.reintentoInicial(),politica.reintentoMaximo());
        var primera=new AtomicBoolean(true);
        var tarea=caso(c->{if(primera.getAndSet(false))reloj.avanzar(181);return resultado(c,EstadoRecuperacion.RECORRIDO_COMPLETO);});
        assertFalse(tarea.ejecutarCiclo().getFirst().programacionConfirmada());
        assertTrue(tarea.ejecutarCiclo().getFirst().programacionConfirmada());
        assertEquals(EstadoSincronizacion.RECORRIDO_COMPLETO.name(),base.jdbc.queryForObject("SELECT ultimo_resultado FROM programacion_sincronizacion_lecturas",String.class));
    }

    @Test void falloAlGuardarLaProgramacionNoCancelaOtroSuministro(){
        base.jdbc.update("INSERT INTO suministros VALUES ('13100391','Otro titular',?)",LocalDateTime.ofInstant(AHORA,ZoneOffset.UTC));
        RepositorioSincronizacion intermitente=new RepositorioSincronizacion(){
            public void prepararSuministros(String f,Instant a){repo.prepararSuministros(f,a);}
            public Optional<ReservaSincronizacion> reservar(String f,Instant e,Instant a,Duration d){return repo.reservar(f,e,a,d);}
            public boolean finalizar(ReservaSincronizacion r,EstadoSincronizacion e,int n,Instant p,Instant a){
                if(r.suministro().equals(CODIGO))throw new RepositorioSincronizacionNoDisponibleException("Fallo simulado",new IllegalStateException());
                return repo.finalizar(r,e,n,p,a);
            }
        };
        var tarea=new SincronizarLecturas("empresa-agua",intermitente,c->resultado(c,EstadoRecuperacion.RECORRIDO_COMPLETO),politica,reloj);
        var r=tarea.ejecutarCiclo();assertEquals(2,r.size());
        assertFalse(r.getFirst().programacionConfirmada());assertTrue(r.getLast().programacionConfirmada());
    }

    @Test void elTrabajadorRealContinuaElMismoCorteEnCiclosPosteriores(){
        var tope=new AtomicReference<>("4");var contextos=new AtomicInteger();
        FuenteLecturasConCorte fuente=new FuenteLecturasConCorte(){
            public Optional<ContextoLecturas> consultarContexto(CodigoSuministro codigo){
                contextos.incrementAndGet();return Optional.of(new ContextoLecturas("empresa-agua",codigo,tope.get(),AHORA.minusSeconds(60),
                    List.of(new ContextoLecturas.Medidor("391",LocalDate.parse("2026-10-01"),null))));
            }
            public Optional<LoteLecturas> consultar(CodigoSuministro codigo,String cursor,int limite,ContextoLecturas contexto){
                var pendientes=java.util.stream.LongStream.rangeClosed(1,6).filter(id->id>Long.parseLong(cursor)&&id<=Long.parseLong(contexto.hastaIdLectura())).mapToObj(BaseDatosRecuperacion::lectura).toList();
                var filas=pendientes.subList(0,Math.min(limite,pendientes.size()));
                return Optional.of(new LoteLecturas("empresa-agua",codigo,filas,filas.isEmpty()?cursor:filas.getLast().idLectura(),pendientes.size()>limite));
            }
        };
        var recuperar=new RecuperarLecturasConCorte("empresa-agua",fuente,base.progreso,reloj);
        var tarea=caso(c->recuperar.ejecutar(c,politica.limitePagina(),politica.maximoPaginas()));
        assertEquals(EstadoSincronizacion.EN_CURSO,tarea.ejecutarCiclo().getFirst().estado());
        assertEquals(2,base.lecturas.contar(CODIGO));
        tope.set("6");reloj.avanzar(10);
        assertEquals(EstadoSincronizacion.RECORRIDO_COMPLETO,tarea.ejecutarCiclo().getFirst().estado());
        assertEquals(4,base.lecturas.contar(CODIGO));assertEquals(1,contextos.get());
        assertEquals("4",base.progreso.consultar("empresa-agua",CODIGO).contexto().hastaIdLectura());
    }

    private static final class Reloj extends Clock {
        private final AtomicReference<Instant> instante;
        private final ZoneId zona;
        Reloj(Instant inicial){this(new AtomicReference<>(inicial),ZoneOffset.UTC);}
        private Reloj(AtomicReference<Instant> instante,ZoneId zona){this.instante=instante;this.zona=zona;}
        void avanzar(long segundos){instante.updateAndGet(i->i.plusSeconds(segundos));}
        @Override public ZoneId getZone(){return zona;}
        @Override public Clock withZone(ZoneId zona){return new Reloj(instante,zona);}
        @Override public Instant instant(){return instante.get();}
    }
}
