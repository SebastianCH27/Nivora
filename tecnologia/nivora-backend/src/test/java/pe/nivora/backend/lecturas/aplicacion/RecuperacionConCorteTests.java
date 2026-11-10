package pe.nivora.backend.lecturas.aplicacion;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.json.JsonMapper;
import pe.nivora.backend.compartido.infraestructura.empresaagua.ClienteEmpresaAgua;
import pe.nivora.backend.lecturas.dominio.*;
import pe.nivora.backend.lecturas.dominio.contratos.*;
import pe.nivora.backend.lecturas.infraestructura.empresaagua.FuenteLecturasConCorteHttp;
import pe.nivora.backend.lecturas.soporte.BaseDatosRecuperacion;

import static pe.nivora.backend.lecturas.soporte.BaseDatosRecuperacion.*;
import static org.junit.jupiter.api.Assertions.*;

/** HTTP real contra un proveedor local y persistencia transaccional H2. */
class RecuperacionConCorteTests {
    private static final String INVENTARIO = """
        {"fuente":"empresa-agua","codigoSuministro":"13100390","zonaFechas":"America/Lima",
         "medidores":[{"idMedidor":"391","fechaInstalacion":"2026-10-01","fechaRetiro":null}]}
        """;
    private static final String CORTE = """
        {"fuente":"empresa-agua","hastaIdLectura":"4","confirmadoHasta":"2026-11-09T19:59:00Z"}
        """;
    private BaseDatosRecuperacion base;
    private HttpServer servidor;
    private ExecutorService ejecutor;
    private FuenteLecturasConCorteHttp fuente;
    private final JsonMapper json = JsonMapper.builder().build();
    private final AtomicReference<String> corte = new AtomicReference<>();
    private final AtomicReference<String> inventario = new AtomicReference<>();
    private final AtomicReference<List<LecturaAcumulada>> datos = new AtomicReference<>();
    private final AtomicReference<String> fallo = new AtomicReference<>("");
    private final AtomicInteger contextos = new AtomicInteger();
    private final List<String> limites = new CopyOnWriteArrayList<>();
    private final List<String> cursores = new CopyOnWriteArrayList<>();

    @BeforeEach
    void iniciar() throws Exception {
        base = new BaseDatosRecuperacion();
        corte.set(CORTE); inventario.set(INVENTARIO); fallo.set("");
        datos.set(List.of(lectura(1),lectura(2),lectura(3),lectura(4),lectura(5),lectura(6)));
        contextos.set(0); limites.clear(); cursores.clear();
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        ejecutor = Executors.newVirtualThreadPerTaskExecutor();
        servidor.setExecutor(ejecutor);
        servidor.createContext("/",this::responder);
        servidor.start();
        fuente = new FuenteLecturasConCorteHttp(new ClienteEmpresaAgua(
            "http://127.0.0.1:"+servidor.getAddress().getPort(),1000,1000,json));
    }

    @AfterEach
    void cerrar() {
        servidor.stop(0); ejecutor.shutdownNow(); base.close();
    }

    private RecuperarLecturasConCorte caso() {
        return new RecuperarLecturasConCorte("empresa-agua",fuente,
            base.nuevaInstanciaProgreso(),Clock.fixed(AHORA,ZoneOffset.UTC));
    }

    @Test
    void reanudaElMismoCorteAunqueLaFuentePubliqueMasDatos() {
        var primera=caso().ejecutar(CODIGO,2,1);
        assertEquals(EstadoRecuperacion.EN_CURSO,primera.progreso().estado());
        assertEquals("2",primera.progreso().cursor());
        assertEquals("4",primera.progreso().contexto().hastaIdLectura());
        corte.set(CORTE.replace("\"4\"","\"6\""));
        inventario.set(INVENTARIO.replace("\"391\"","\"999\""));
        var segunda=caso().ejecutar(CODIGO,2,10);
        assertEquals(EstadoRecuperacion.RECORRIDO_COMPLETO,segunda.progreso().estado());
        assertEquals(2,segunda.incorporacion().nuevas());
        assertEquals(4,base.lecturas.contar(CODIGO));
        assertEquals(1,contextos.get());
        assertEquals(List.of("4","4"),limites);
        assertEquals("391",segunda.progreso().contexto().medidores().getFirst().idMedidor());
        assertEquals(segunda.progreso(),base.nuevaInstanciaProgreso().consultar("empresa-agua",CODIGO));
    }

    @Test
    void nuevoRecorridoObtieneNuevoCorteYNoDuplicaLoAnterior() {
        caso().ejecutar(CODIGO,2,10);
        corte.set(CORTE.replace("\"4\"","\"6\""));
        var segunda=caso().ejecutar(CODIGO,2,10);
        assertEquals(2,segunda.incorporacion().nuevas());
        assertEquals(4,segunda.incorporacion().repetidas());
        assertEquals(6,base.lecturas.contar(CODIGO));
        assertEquals("6",segunda.progreso().contexto().hastaIdLectura());
        assertEquals(2,contextos.get());
    }

    @Test
    void falloDePaginaConservaCorteCursorYDatosParaOtraInstancia() {
        fallo.set("pagina2");
        assertThrows(FuenteLecturasNoDisponibleException.class,()->caso().ejecutar(CODIGO,2,10));
        var guardado=base.progreso.consultar("empresa-agua",CODIGO);
        assertEquals("2",guardado.cursor());
        assertEquals("4",guardado.contexto().hastaIdLectura());
        assertEquals(EstadoRecuperacion.ERROR_FUENTE,guardado.estado());
        assertEquals(2,base.lecturas.contar(CODIGO));
        fallo.set(""); corte.set(CORTE.replace("\"4\"","\"6\""));
        var resultado=caso().ejecutar(CODIGO,2,10);
        assertEquals(2,resultado.incorporacion().nuevas());
        assertEquals(4,base.lecturas.contar(CODIGO));
        assertEquals(1,contextos.get());
        assertFalse(base.jdbc.queryForObject("SELECT abierta FROM incidencias_recuperacion_lecturas",Boolean.class));
    }

    @Test
    void falloAntesDeLaPrimeraPaginaConservaElContexto() {
        fallo.set("paginas");
        assertThrows(FuenteLecturasNoDisponibleException.class,()->caso().ejecutar(CODIGO,2,10));
        assertEquals("4",base.progreso.consultar("empresa-agua",CODIGO).contexto().hastaIdLectura());
        fallo.set(""); corte.set(CORTE.replace("\"4\"","\"6\""));
        assertEquals(4,caso().ejecutar(CODIGO,2,10).incorporacion().nuevas());
        assertEquals(1,contextos.get());
    }

    @Test
    void progresoAnteriorSinContextoRevisaDesdeCeroYConservaSusLecturas() {
        base.progreso.confirmar(base.progreso.consultar("empresa-agua",CODIGO),pagina(true,1,2),AHORA);
        var resultado=caso().ejecutar(CODIGO,2,1);
        assertEquals(List.of("0"),cursores);
        assertEquals(2,resultado.incorporacion().repetidas());
        assertEquals(2,base.lecturas.contar(CODIGO));
        assertNotNull(resultado.progreso().contexto());
    }

    @Test
    void corteVacioTerminaSinInventarLecturas() {
        corte.set(CORTE.replace("\"4\"","\"0\""));
        var resultado=caso().ejecutar(CODIGO,2,10);
        assertEquals(EstadoRecuperacion.RECORRIDO_COMPLETO,resultado.progreso().estado());
        assertEquals("0",resultado.progreso().cursor());
        assertEquals(0,base.lecturas.contar(CODIGO));
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("contextosInvalidos")
    void contextoIncompatibleNoTieneAlternativaSinCorte(String caso,String nuevoCorte,String nuevoInventario) {
        corte.set(nuevoCorte); inventario.set(nuevoInventario);
        assertThrows(FuenteLecturasNoDisponibleException.class,()->caso().ejecutar(CODIGO,2,10),caso);
        assertEquals(0,base.lecturas.contar(CODIGO));
        var progreso=base.progreso.consultar("empresa-agua",CODIGO);
        assertEquals(EstadoRecuperacion.ERROR_FUENTE,progreso.estado());
        assertNull(progreso.contexto());
        assertEquals(List.of(),limites);
    }

    static Stream<Arguments> contextosInvalidos() {
        return Stream.of(
            Arguments.of("corte numerico",CORTE.replace("\"4\"","4"),INVENTARIO),
            Arguments.of("corte fuera de rango",CORTE.replace("\"4\"","\"9223372036854775808\""),INVENTARIO),
            Arguments.of("corte no canonico",CORTE.replace("\"4\"","\"04\""),INVENTARIO),
            Arguments.of("fuente de corte distinta",CORTE.replace("empresa-agua","otra"),INVENTARIO),
            Arguments.of("corte sin UTC",CORTE.replace("19:59:00Z","19:59:00"),INVENTARIO),
            Arguments.of("corte futuro",CORTE.replace("19:59:00Z","20:01:00Z"),INVENTARIO),
            Arguments.of("precision no soportada",CORTE.replace("19:59:00Z","19:59:00.123456789Z"),INVENTARIO),
            Arguments.of("inventario de otro suministro",CORTE,INVENTARIO.replace("13100390","99999999")),
            Arguments.of("inventario de otra fuente",CORTE,INVENTARIO.replace("empresa-agua","otra")),
            Arguments.of("zona incompatible",CORTE,INVENTARIO.replace("America/Lima","UTC")),
            Arguments.of("medidor numerico",CORTE,INVENTARIO.replace("\"391\"","391")),
            Arguments.of("fechas invertidas",CORTE,INVENTARIO.replace("\"fechaRetiro\":null","\"fechaRetiro\":\"2026-09-01\"")),
            Arguments.of("inventario vacio",CORTE,INVENTARIO.replace("[{\"idMedidor\":\"391\",\"fechaInstalacion\":\"2026-10-01\",\"fechaRetiro\":null}]","[]")),
            Arguments.of("fecha retiro ausente",CORTE,INVENTARIO.replace(",\"fechaRetiro\":null",""))
        );
    }

    @Test
    void suministroAusenteEnInventarioSeDistingueDeUnFallo() {
        fallo.set("inventario404");
        var resultado=caso().ejecutar(CODIGO,2,10);
        assertEquals(EstadoRecuperacion.SUMINISTRO_NO_ENCONTRADO,resultado.progreso().estado());
        assertEquals(0,contextos.get()); assertEquals(0,resultado.paginas());
    }

    @Test
    void corteIndisponibleNoSeConvierteEnAusenciaDelSuministro() {
        fallo.set("corte503");
        assertThrows(FuenteLecturasNoDisponibleException.class,()->caso().ejecutar(CODIGO,2,10));
        assertEquals(EstadoRecuperacion.ERROR_FUENTE,base.progreso.consultar("empresa-agua",CODIGO).estado());
    }

    @Test
    void paginaQueSuperaElCorteNoSeIncorporaNiAvanza() {
        fallo.set("superarCorte");
        assertThrows(FuenteLecturasNoDisponibleException.class,()->caso().ejecutar(CODIGO,500,10));
        assertEquals(0,base.lecturas.contar(CODIGO));
        var estado=base.progreso.consultar("empresa-agua",CODIGO);
        assertEquals("0",estado.cursor()); assertEquals("4",estado.contexto().hastaIdLectura());
    }

    @Test
    void lecturaPosteriorAlCorteRegistraIncidenciaYBloqueaElCursor() {
        datos.set(List.of(new LecturaAcumulada("empresa-agua","1",CODIGO,"391",
            AHORA.minusSeconds(30),100),lectura(2)));
        verificarRechazo("FECHA_POSTERIOR_AL_CORTE",1);
        assertEquals(1,base.lecturas.contar(CODIGO));
    }

    @Test
    void medidorAjenoAlInventarioSeConservaComoIncidencia() {
        datos.set(List.of(new LecturaAcumulada("empresa-agua","1",CODIGO,"999",lectura(1).fechaMedicion(),100)));
        verificarRechazo("MEDIDOR_NO_REGISTRADO",1);
    }

    @Test
    void medicionAntesDeLaInstalacionSeRechaza() {
        datos.set(List.of(lectura(1)));
        inventario.set(INVENTARIO.replace("2026-10-01","2026-10-02"));
        verificarRechazo("FECHA_FUERA_VIGENCIA_MEDIDOR",1);
    }

    @Test
    void medicionDespuesDelDiaDeRetiroSeRechaza() {
        datos.set(List.of(lectura(1)));
        inventario.set(INVENTARIO.replace("2026-10-01","2026-09-01")
            .replace("\"fechaRetiro\":null","\"fechaRetiro\":\"2026-09-30\""));
        verificarRechazo("FECHA_FUERA_VIGENCIA_MEDIDOR",1);
    }

    @Test
    void comprobacionDelDiaDeRetiroUsaLaFechaDeLima() {
        datos.set(List.of(lectura(1)));
        inventario.set(INVENTARIO.replace("\"fechaRetiro\":null","\"fechaRetiro\":\"2026-10-01\""));
        assertEquals(1,caso().ejecutar(CODIGO,2,10).incorporacion().nuevas());
    }

    @Test
    void lecturaAceptadaAnteriormenteTambienSeRevisaContraElInventario() {
        datos.set(List.of(lectura(1)));
        base.lecturas.incorporar(pagina(false,1),AHORA);
        inventario.set(INVENTARIO.replace("\"391\"","\"999\""));
        verificarRechazo("MEDIDOR_NO_REGISTRADO",1);
        assertEquals(lectura(1),base.lecturas.buscar("empresa-agua","1").orElseThrow());
        assertEquals(1,base.lecturas.contar(CODIGO));
    }

    @Test
    void contextoYProgresoSeGuardanAtomicamente() {
        base.jdbc.execute("ALTER TABLE contexto_recuperacion_lecturas ADD CONSTRAINT solo_corte_cero CHECK(hasta_id_lectura='0')");
        assertThrows(RepositorioLecturasNoDisponibleException.class,()->caso().ejecutar(CODIGO,2,10));
        assertEquals(0,base.jdbc.queryForObject("SELECT COUNT(*) FROM progreso_recuperacion_lecturas",Long.class));
        assertEquals(0,base.jdbc.queryForObject("SELECT COUNT(*) FROM contexto_recuperacion_lecturas",Long.class));
        assertEquals(0,base.lecturas.contar(CODIGO));
    }

    @Test
    void paginaObsoletaNoPuedeConfirmarseEnOtroContexto() {
        var inicial=base.progreso.consultar("empresa-agua",CODIGO);
        var contexto=new ContextoLecturas("empresa-agua",CODIGO,"4",AHORA.minusSeconds(60),
            List.of(new ContextoLecturas.Medidor("391",LocalDate.parse("2026-10-01"),null)));
        var primero=base.progreso.iniciarContexto(inicial,contexto,AHORA);
        var reiniciado=base.progreso.reiniciar(primero,AHORA);
        var segundo=base.progreso.iniciarContexto(reiniciado,contexto,AHORA);
        assertThrows(ProgresoRecuperacionModificadoException.class,
            ()->base.progreso.confirmar(primero,pagina(true,1,2),AHORA));
        assertEquals(segundo,base.progreso.consultar("empresa-agua",CODIGO));
        assertEquals(0,base.lecturas.contar(CODIGO));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void contextoCorruptoEnLaBaseNoSeSustituyeSilenciosamente(boolean extra) {
        caso().ejecutar(CODIGO,2,1);
        String inventarioGuardado=base.jdbc.queryForObject(
            "SELECT inventario_json FROM contexto_recuperacion_lecturas",String.class);
        base.jdbc.update("UPDATE contexto_recuperacion_lecturas SET inventario_json=?",
            extra ? inventarioGuardado+"{}" : "{");
        assertThrows(RepositorioLecturasNoDisponibleException.class,()->caso().ejecutar(CODIGO,2,10));
        assertEquals(1,contextos.get());
        assertEquals(2,base.lecturas.contar(CODIGO));
    }

    @Test
    void repetirLaMigracionConservaElContextoYLosDatos() {
        var primera=caso().ejecutar(CODIGO,2,1).progreso();
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
            new org.springframework.core.io.FileSystemResource("base-datos/05-contexto-recuperacion-lecturas.sql"))
            .execute(base.jdbc.getDataSource());
        assertEquals(primera,base.progreso.consultar("empresa-agua",CODIGO));
        assertEquals(2,base.lecturas.contar(CODIGO));
    }

    @Test
    void falloAlConfirmarLaPaginaConservaContextoPeroRevierteLecturas() {
        fallo.set("paginas");
        assertThrows(FuenteLecturasNoDisponibleException.class,()->caso().ejecutar(CODIGO,2,1));
        var antes=base.progreso.consultar("empresa-agua",CODIGO);
        base.jdbc.execute("ALTER TABLE progreso_recuperacion_lecturas ADD CONSTRAINT cursor_cero CHECK(cursor_confirmado='0')");
        fallo.set("");
        assertThrows(RepositorioLecturasNoDisponibleException.class,()->caso().ejecutar(CODIGO,2,1));
        assertEquals(antes,base.progreso.consultar("empresa-agua",CODIGO));
        assertEquals(0,base.lecturas.contar(CODIGO));
    }

    private void verificarRechazo(String motivo,int cantidad) {
        var resultado=caso().ejecutar(CODIGO,2,10);
        assertEquals(cantidad,resultado.incorporacion().rechazadas());
        assertEquals(EstadoRecuperacion.BLOQUEADO,resultado.progreso().estado());
        assertEquals("0",resultado.progreso().cursor());
        assertEquals(motivo,base.jdbc.queryForObject("SELECT motivo FROM incidencias_lecturas LIMIT 1",String.class));
        int consultas=cursores.size();
        assertEquals(0,caso().ejecutar(CODIGO,2,10).paginas());
        assertEquals(consultas,cursores.size());
    }

    private void responder(HttpExchange intercambio) throws IOException {
        String ruta=intercambio.getRequestURI().getPath();
        String cuerpo; int estado=200;
        if(ruta.endsWith("/medidores")) {
            if(fallo.get().equals("inventario404")) {
                estado=404; cuerpo="{\"status\":404,\"title\":\"Suministro no encontrado\",\"instance\":\""+ruta+"\"}";
            } else cuerpo=inventario.get();
        } else if(ruta.equals("/api/v1/lecturas/corte")) {
            contextos.incrementAndGet(); cuerpo=corte.get();
            if(fallo.get().equals("corte503")) estado=503;
        } else {
            Map<String,String> parametros=new HashMap<>();
            for(String par:intercambio.getRequestURI().getRawQuery().split("&")) {
                String[] partes=par.split("=",2);
                parametros.put(partes[0],URLDecoder.decode(partes[1],StandardCharsets.UTF_8));
            }
            String desde=parametros.get("despuesDe"), hasta=parametros.get("hastaId");
            limites.add(hasta); cursores.add(desde);
            if(fallo.get().equals("paginas") || (fallo.get().equals("pagina2") && desde.equals("2"))) {
                estado=503; cuerpo="{}";
            } else {
                long superior=fallo.get().equals("superarCorte") ? Long.MAX_VALUE:Long.parseLong(hasta);
                int limite=Integer.parseInt(parametros.get("limite"));
                var todas=datos.get().stream().filter(l->Long.parseLong(l.idLectura())>Long.parseLong(desde)
                    && Long.parseLong(l.idLectura())<=superior)
                    .sorted(Comparator.comparingLong(l->Long.parseLong(l.idLectura()))).toList();
                var filas=todas.subList(0,Math.min(todas.size(),limite));
                var respuesta=json.createObjectNode();
                respuesta.put("fuente","empresa-agua");respuesta.put("codigoSuministro",CODIGO.valor());
                respuesta.put("tipoMedicion","ACUMULADA");respuesta.put("unidad","L");
                var lista=respuesta.putArray("lecturas");
                for(var l:filas) {
                    var fila=lista.addObject();fila.put("idLectura",l.idLectura());fila.put("idMedidor",l.idMedidor());
                    fila.put("fechaMedicion",l.fechaMedicion().toString());fila.put("valorLitros",l.valorLitros());
                }
                respuesta.put("siguienteDespuesDe",filas.isEmpty()?desde:filas.getLast().idLectura());
                respuesta.put("hayMas",todas.size()>limite);cuerpo=json.writeValueAsString(respuesta);
            }
        }
        intercambio.getResponseHeaders().set("Content-Type",estado==404?"application/problem+json":"application/json");
        byte[] bytes=cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.sendResponseHeaders(estado,bytes.length);
        try(var salida=intercambio.getResponseBody()){salida.write(bytes);}
    }
}
