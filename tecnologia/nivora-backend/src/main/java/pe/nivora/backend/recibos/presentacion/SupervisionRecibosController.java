package pe.nivora.backend.recibos.presentacion;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import pe.nivora.backend.recibos.aplicacion.ConsultarSupervisionRecibos;
import pe.nivora.backend.recibos.dominio.ConsultaSupervisionRecibos;
import pe.nivora.backend.recibos.dominio.SupervisionRecibos.Programacion;

@RestController
public class SupervisionRecibosController {
    private final ConsultarSupervisionRecibos consultar;
    private final boolean automaticaHabilitada;

    public SupervisionRecibosController(ConsultarSupervisionRecibos consultar,
            @Value("${nivora.recibos.sincronizacion.habilitada:false}") boolean automaticaHabilitada) {
        this.consultar = consultar;
        this.automaticaHabilitada = automaticaHabilitada;
    }

    @GetMapping(value = "/api/v1/supervision/suministros/{codigo}/recibos", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> consultar(@PathVariable("codigo") String codigo) {
        var supervision = consultar.ejecutar(codigo);
        if (supervision.isEmpty()) {
            return problema(HttpStatus.NOT_FOUND, "Suministro no registrado",
                "No hay un suministro registrado en Nivora con ese codigo.");
        }
        var estado = supervision.orElseThrow();
        var guardados = estado.almacenamiento();
        var periodo = guardados.ultimoPeriodo();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Respuesta(
            "empresa-agua", estado.suministro().valor(), estado.consultadoEn(), automaticaHabilitada,
            new RecibosGuardados(guardados.recibosGuardados(), periodo == null ? null : periodo.toString(),
                guardados.ultimaRecepcion()), estado.programacion()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> codigoInvalido() {
        return problema(HttpStatus.BAD_REQUEST, "Solicitud invalida",
            "El codigo debe contener entre 1 y 20 digitos.");
    }

    @ExceptionHandler(ConsultaSupervisionRecibos.NoDisponible.class)
    ResponseEntity<ProblemDetail> datosNoDisponibles() {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Supervision no disponible",
            "No se pudo consultar el estado guardado de los recibos.");
    }

    private static ResponseEntity<ProblemDetail> problema(HttpStatus estado, String titulo, String detalle) {
        var cuerpo = ProblemDetail.forStatusAndDetail(estado, detalle);
        cuerpo.setTitle(titulo);
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(cuerpo);
    }

    public record Respuesta(String fuente, String codigoSuministro, Instant consultadoEn,
            boolean sincronizacionAutomaticaHabilitada, RecibosGuardados almacenamiento, Programacion programacion) {}

    public record RecibosGuardados(long recibosGuardados, String ultimoPeriodo, Instant ultimaRecepcion) {}
}
