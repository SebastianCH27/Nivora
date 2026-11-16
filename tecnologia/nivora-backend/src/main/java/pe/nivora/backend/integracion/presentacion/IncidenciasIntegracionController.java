package pe.nivora.backend.integracion.presentacion;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.integracion.aplicacion.ConsultarIncidenciasIntegracion;
import pe.nivora.backend.integracion.dominio.ConsultaIncidenciasIntegracion;
import pe.nivora.backend.integracion.dominio.IncidenciaIntegracion;
import pe.nivora.backend.integracion.dominio.IncidenciaIntegracion.Cursor;
import pe.nivora.backend.integracion.dominio.IncidenciaIntegracion.Origen;

@RestController
public class IncidenciasIntegracionController {
    private static final Pattern FECHA_CURSOR = Pattern.compile(
        "[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,6})?Z");
    private static final Pattern REFERENCIA_CURSOR = Pattern.compile("[A-Z_0-9]{1,40}");
    private final ConsultarIncidenciasIntegracion consultar;

    public IncidenciasIntegracionController(ConsultarIncidenciasIntegracion consultar) {
        this.consultar = consultar;
    }

    @GetMapping(value = "/api/v1/supervision/suministros/{codigo}/incidencias",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> incidencias(@PathVariable("codigo") String codigo,
            @RequestParam(name = "antesDe", required = false) String antesDe,
            @RequestParam(name = "limite", defaultValue = "20") int limite) {
        var pagina = consultar.ejecutar(new CodigoSuministro(codigo), cursor(antesDe), limite);
        if (pagina.isEmpty()) return problema(HttpStatus.NOT_FOUND, "Suministro no registrado",
            "No hay un suministro registrado en Nivora con ese codigo.");
        var contenido = pagina.orElseThrow();
        var siguiente = contenido.siguienteAntesDe();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Respuesta(codigo,
            contenido.incidencias(), siguiente != null, siguiente == null ? null : serializar(siguiente)));
    }

    private static Cursor cursor(String valor) {
        if (valor == null) return null;
        var partes = valor.split("~", 4);
        if (partes.length != 4 || !FECHA_CURSOR.matcher(partes[0]).matches()
                || !REFERENCIA_CURSOR.matcher(partes[2]).matches() || partes[3].isEmpty()
                || partes[3].length() > 100) {
            throw new IllegalArgumentException("Cursor de incidencias invalido.");
        }
        return new Cursor(Instant.parse(partes[0]), Origen.valueOf(partes[1]), partes[3], partes[2]);
    }

    private static String serializar(Cursor cursor) {
        return cursor.primeraRecepcion() + "~" + cursor.origen() + "~" + cursor.referencia() + "~" + cursor.fuente();
    }

    @ExceptionHandler({IllegalArgumentException.class, DateTimeException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> parametrosInvalidos() {
        return problema(HttpStatus.BAD_REQUEST, "Parametros invalidos",
            "Usa un codigo valido, un limite de 1 a 100 y el cursor de la pagina anterior.");
    }

    @ExceptionHandler(ConsultaIncidenciasIntegracion.NoDisponible.class)
    ResponseEntity<ProblemDetail> incidenciasNoDisponibles() {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Incidencias no disponibles",
            "No se pudieron consultar las incidencias guardadas.");
    }

    private static ResponseEntity<ProblemDetail> problema(HttpStatus estado, String titulo, String detalle) {
        var cuerpo = ProblemDetail.forStatusAndDetail(estado, detalle);
        cuerpo.setTitle(titulo);
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(cuerpo);
    }

    public record Respuesta(String codigoSuministro, List<IncidenciaIntegracion> incidencias, boolean hayMas,
            String siguienteAntesDe) {}
}
