package pe.nivora.backend.notificaciones.presentacion;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import pe.nivora.backend.acceso.infraestructura.seguridad.PrincipalConsulta;
import pe.nivora.backend.notificaciones.aplicacion.BandejaNotificaciones;
import pe.nivora.backend.notificaciones.dominio.Notificacion;
import pe.nivora.backend.notificaciones.dominio.RepositorioBandejaNotificaciones;

@RestController
@RequestMapping("/api/v1/acceso/consulta/notificaciones")
public class NotificacionesConsultaController {
    private static final Pattern ID = Pattern.compile("(ALERTA_CONSUMO|RECIBO_DISPONIBLE)-([1-9][0-9]{3}-(?:0[1-9]|1[0-2]))");
    private static final Pattern FECHA_CURSOR = Pattern.compile(
        "[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,6})?Z");
    private final BandejaNotificaciones bandeja;

    public NotificacionesConsultaController(BandejaNotificaciones bandeja) {
        this.bandeja = bandeja;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Respuesta> consultar(@AuthenticationPrincipal PrincipalConsulta principal,
            @RequestParam(name = "limite", defaultValue = "20") int limite,
            @RequestParam(name = "soloPendientes", defaultValue = "false") boolean soloPendientes,
            @RequestParam(name = "antesDe", required = false) String antesDe) {
        var suministro = principal.sesion().codigoSuministro();
        var pagina = bandeja.consultar(suministro, cursor(antesDe), soloPendientes, limite);
        var siguiente = pagina.siguienteAntesDe();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Respuesta(suministro.valor(),
            pagina.notificaciones().stream().map(NotificacionesConsultaController::aviso).toList(),
            siguiente != null, siguiente == null ? null : siguiente.registradaEn() + "~" + id(siguiente.clave())));
    }

    @PutMapping("/{id}/leida")
    public ResponseEntity<?> marcarLeida(@AuthenticationPrincipal PrincipalConsulta principal,
            @PathVariable("id") String id) {
        if (!bandeja.marcarLeida(principal.sesion().codigoSuministro(), clave(id))) {
            return problema(HttpStatus.NOT_FOUND, "Notificacion no encontrada",
                "La notificacion no esta disponible para el suministro de esta sesion.");
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static Aviso aviso(Notificacion notificacion) {
        var clave = notificacion.clave();
        String titulo = switch (clave.tipo()) {
            case ALERTA_CONSUMO -> "Superaste el consumo del mes anterior";
            case RECIBO_DISPONIBLE -> "Tu recibo está disponible";
        };
        var consumo = notificacion.consumo();
        var volumenes = consumo == null ? null : new Consumo(Long.toString(consumo.actualLitros()),
            Long.toString(consumo.anteriorLitros()));
        return new Aviso(id(clave), clave.tipo(), clave.periodo().toString(), titulo, notificacion.registradaEn(),
            notificacion.leidaEn() != null, notificacion.leidaEn(), volumenes, notificacion.recibo());
    }

    private static String id(Notificacion.Clave clave) {
        return clave.tipo().name() + "-" + clave.periodo();
    }

    private static Notificacion.Clave clave(String valor) {
        var coincidencia = ID.matcher(valor);
        if (!coincidencia.matches()) throw new IllegalArgumentException("Identificador de notificacion invalido.");
        return new Notificacion.Clave(Notificacion.Tipo.valueOf(coincidencia.group(1)),
            YearMonth.parse(coincidencia.group(2)));
    }

    private static Notificacion.Cursor cursor(String valor) {
        if (valor == null) return null;
        var partes = valor.split("~", 2);
        if (partes.length != 2 || !FECHA_CURSOR.matcher(partes[0]).matches()) {
            throw new IllegalArgumentException("Cursor de notificaciones invalido.");
        }
        return new Notificacion.Cursor(Instant.parse(partes[0]), clave(partes[1]));
    }

    @ExceptionHandler({IllegalArgumentException.class, DateTimeException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> parametrosInvalidos() {
        return problema(HttpStatus.BAD_REQUEST, "Parametros invalidos",
            "Usa un limite de 1 a 60, un filtro valido y los identificadores que devuelve la bandeja.");
    }

    @ExceptionHandler(RepositorioBandejaNotificaciones.NoDisponible.class)
    ResponseEntity<ProblemDetail> notificacionesNoDisponibles() {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Notificaciones no disponibles",
            "No se pudo acceder a las notificaciones guardadas.");
    }

    private static ResponseEntity<ProblemDetail> problema(HttpStatus estado, String titulo, String detalle) {
        var cuerpo = ProblemDetail.forStatusAndDetail(estado, detalle);
        cuerpo.setTitle(titulo);
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(cuerpo);
    }

    public record Respuesta(String codigoSuministro, List<Aviso> notificaciones, boolean hayMas, String siguienteAntesDe) {}

    public record Aviso(String id, Notificacion.Tipo tipo, String periodo, String titulo, Instant registradaEn,
        boolean leida, Instant leidaEn, Consumo consumo, Notificacion.Recibo recibo) {}

    public record Consumo(String actualLitros, String anteriorLitros) {}
}
