package pe.nivora.backend.lecturas.sincronizacion;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pe.nivora.backend.lecturas.aplicacion.PoliticaSincronizacion;
import pe.nivora.backend.lecturas.dominio.EstadoSincronizacion;
import static org.junit.jupiter.api.Assertions.*;

class PoliticaSincronizacionTests {
    private static final Instant AHORA=Instant.parse("2026-11-10T18:00:00Z");
    private PoliticaSincronizacion politica(){return nueva(2,100,2,30,1800);}
    private PoliticaSincronizacion nueva(int intentos,int limite,int paginas,long inicial,long maximo){
        return new PoliticaSincronizacion(intentos,limite,paginas,Duration.ofSeconds(180),Duration.ofSeconds(300),Duration.ofSeconds(10),Duration.ofSeconds(inicial),Duration.ofSeconds(maximo));
    }
    @ParameterizedTest
    @EnumSource(value=EstadoSincronizacion.class,names={"ERROR_FUENTE","SUMINISTRO_NO_ENCONTRADO","ERROR_ALMACENAMIENTO","ERROR_INTERNO"})
    void todosLosFallosSeReintentanConUnMaximo(EstadoSincronizacion estado){
        assertEquals(AHORA.plusSeconds(30),politica().siguiente(estado,0,AHORA).proximoIntento());
        assertEquals(AHORA.plusSeconds(60),politica().siguiente(estado,1,AHORA).proximoIntento());
        assertEquals(AHORA.plusSeconds(1800),politica().siguiente(estado,Integer.MAX_VALUE,AHORA).proximoIntento());
        assertEquals(Integer.MAX_VALUE,politica().siguiente(estado,Integer.MAX_VALUE,AHORA).fallos());
    }
    @Test void soloElBloqueoPausaLaProgramacion(){
        assertNull(politica().siguiente(EstadoSincronizacion.BLOQUEADO,2,AHORA).proximoIntento());
        assertEquals(0,politica().siguiente(EstadoSincronizacion.BLOQUEADO,2,AHORA).fallos());
    }
    @Test void validaLimitesYTiempos(){
        assertThrows(IllegalArgumentException.class,()->nueva(0,100,2,30,1800));
        assertThrows(IllegalArgumentException.class,()->nueva(2,501,2,30,1800));
        assertThrows(IllegalArgumentException.class,()->nueva(2,100,11,30,1800));
        assertThrows(IllegalArgumentException.class,()->nueva(2,100,2,0,1800));
        assertThrows(IllegalArgumentException.class,()->nueva(2,100,2,60,30));
    }
}
