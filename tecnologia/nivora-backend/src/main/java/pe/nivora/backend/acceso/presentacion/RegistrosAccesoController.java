package pe.nivora.backend.acceso.presentacion;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import pe.nivora.backend.acceso.aplicacion.ConsultarRegistrosAcceso;
import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.contratos.ConsultaRegistrosAcceso;
import pe.nivora.backend.acceso.dominio.contratos.ConsultaRegistrosAcceso.Cursor;

@RestController
@RequestMapping("/api/v1/administracion/suministros")
public class RegistrosAccesoController {
    private static final Pattern FECHA_CURSOR = Pattern.compile(
        "[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,6})?Z");
    private final ConsultarRegistrosAcceso consultar;

    public RegistrosAccesoController(ConsultarRegistrosAcceso consultar) {
        this.consultar = consultar;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ListadoSuministros> suministros(
            @RequestParam(name = "despuesDe", required = false) String despuesDe,
            @RequestParam(name = "limite", defaultValue = "20") int limite) {
        var pagina = consultar.suministros(despuesDe == null ? null : new CodigoSuministro(despuesDe), limite);
        var suministros = pagina.elementos().stream().map(registro -> new SuministroListado(
            registro.suministro().codigo().valor(), registro.suministro().titular(), registro.fechaVerificacion()))
            .toList();
        var siguiente = pagina.siguiente();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
            new ListadoSuministros(suministros, siguiente != null, siguiente == null ? null : siguiente.valor()));
    }

    @GetMapping(value = "/{codigo}/accesos", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> accesos(@PathVariable("codigo") String codigo,
            @RequestParam(name = "antesDe", required = false) String antesDe,
            @RequestParam(name = "limite", defaultValue = "20") int limite) {
        var pagina = consultar.accesos(new CodigoSuministro(codigo), cursor(antesDe), limite);
        if (pagina.isEmpty()) return problema(HttpStatus.NOT_FOUND, "Suministro no reconocido",
            "El codigo indicado no corresponde a un suministro almacenado.");
        var contenido = pagina.orElseThrow();
        var accesos = contenido.elementos().stream().map(acceso -> new AccesoListado(
            acceso.dniEnmascarado(), acceso.cursor().creadaEn(), acceso.expiraEn(), acceso.revocadaEn())).toList();
        var siguiente = contenido.siguiente();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
            new ListadoAccesos(codigo, accesos, siguiente != null,
                siguiente == null ? null : siguiente.creadaEn() + "~" + siguiente.id()));
    }

    private static Cursor cursor(String valor) {
        if (valor == null) return null;
        var partes = valor.split("~", 2);
        if (partes.length != 2 || !FECHA_CURSOR.matcher(partes[0]).matches() || partes[1].length() != 36) {
            throw new IllegalArgumentException("Cursor de accesos invalido.");
        }
        var id = UUID.fromString(partes[1]);
        if (!id.toString().equals(partes[1])) throw new IllegalArgumentException("Cursor de accesos invalido.");
        return new Cursor(Instant.parse(partes[0]), id);
    }

    @ExceptionHandler({IllegalArgumentException.class, DateTimeException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> parametrosInvalidos() {
        return problema(HttpStatus.BAD_REQUEST, "Parametros invalidos",
            "Usa codigos de suministro validos, un limite de 1 a 100 y el cursor de la pagina anterior.");
    }

    @ExceptionHandler(ConsultaRegistrosAcceso.NoDisponible.class)
    ResponseEntity<ProblemDetail> registrosNoDisponibles() {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Registros no disponibles",
            "No se pudieron consultar los registros guardados.");
    }

    private static ResponseEntity<ProblemDetail> problema(HttpStatus estado, String titulo, String detalle) {
        var cuerpo = ProblemDetail.forStatusAndDetail(estado, detalle);
        cuerpo.setTitle(titulo);
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(cuerpo);
    }

    public record SuministroListado(String codigoSuministro, String titular, Instant verificadoEn) {}

    public record ListadoSuministros(List<SuministroListado> suministros, boolean hayMas, String siguienteDespuesDe) {}

    public record AccesoListado(String dniEnmascarado, Instant creadaEn, Instant expiraEn, Instant revocadaEn) {}

    public record ListadoAccesos(String codigoSuministro, List<AccesoListado> accesos, boolean hayMas,
            String siguienteAntesDe) {}
}
