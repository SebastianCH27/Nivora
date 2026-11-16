package pe.nivora.backend.integracion.presentacion;

import java.net.URI;
import java.time.Instant;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.integracion.aplicacion.SolicitarReintentoIntegracion;
import pe.nivora.backend.integracion.dominio.RepositorioReintentosIntegracion;
import pe.nivora.backend.integracion.dominio.RepositorioReintentosIntegracion.Estado;
import pe.nivora.backend.integracion.dominio.RepositorioReintentosIntegracion.Proceso;

@RestController
public class ReintentosIntegracionController {
    private static final String SUPERVISION = "/api/v1/supervision/suministros/";
    private final SolicitarReintentoIntegracion solicitar;

    public ReintentosIntegracionController(SolicitarReintentoIntegracion solicitar) {
        this.solicitar = solicitar;
    }

    @PostMapping(value = SUPERVISION + "{codigo}/{recurso}/reintentos",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> reintentar(@PathVariable("codigo") String codigo,
            @PathVariable("recurso") String recurso) {
        var proceso = switch (recurso) {
            case "lecturas" -> Proceso.LECTURAS;
            case "recibos" -> Proceso.RECIBOS;
            default -> throw new IllegalArgumentException("Proceso invalido.");
        };
        var solicitud = solicitar.ejecutar(new CodigoSuministro(codigo), proceso);
        return switch (solicitud.estado()) {
            case PROGRAMADO -> ResponseEntity.accepted()
                .location(URI.create(SUPERVISION + codigo + "/" + recurso))
                .cacheControl(CacheControl.noStore())
                .body(new Respuesta(codigo, proceso, solicitud.estado(), solicitud.proximoIntento()));
            case SUMINISTRO_NO_REGISTRADO -> problema(HttpStatus.NOT_FOUND, "Suministro no registrado",
                "No hay un suministro registrado en Nivora con ese codigo.");
            case EN_CURSO -> problema(HttpStatus.CONFLICT, "Sincronizacion en curso",
                "El suministro ya tiene una reserva vigente para este proceso.");
            case BLOQUEADO -> problema(HttpStatus.CONFLICT, "Recorrido de lecturas bloqueado",
                "Revisa la incidencia y reinicia el recorrido de lecturas antes de reintentar.");
        };
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> parametrosInvalidos() {
        return problema(HttpStatus.BAD_REQUEST, "Solicitud invalida",
            "Usa un codigo de suministro valido y el proceso lecturas o recibos.");
    }

    @ExceptionHandler(RepositorioReintentosIntegracion.NoDisponible.class)
    ResponseEntity<ProblemDetail> reintentoNoDisponible() {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Reintento no disponible",
            "No se pudo programar el reintento en Nivora.");
    }

    private static ResponseEntity<ProblemDetail> problema(HttpStatus estado, String titulo, String detalle) {
        var cuerpo = ProblemDetail.forStatusAndDetail(estado, detalle);
        cuerpo.setTitle(titulo);
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(cuerpo);
    }

    public record Respuesta(String codigoSuministro, Proceso proceso, Estado estado, Instant proximoIntento) {}
}
