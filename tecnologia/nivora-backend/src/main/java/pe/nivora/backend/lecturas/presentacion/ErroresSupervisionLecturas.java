package pe.nivora.backend.lecturas.presentacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pe.nivora.backend.lecturas.dominio.contratos.SupervisionLecturasNoDisponibleException;
import pe.nivora.backend.lecturas.dominio.contratos.SuministroNoRegistradoException;

@RestControllerAdvice(assignableTypes = SupervisionLecturasController.class)
public class ErroresSupervisionLecturas {
    private static final Logger LOG = LoggerFactory.getLogger(ErroresSupervisionLecturas.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> codigoInvalido() {
        return problema(HttpStatus.BAD_REQUEST, "Solicitud invalida",
            "El codigo debe contener entre 1 y 20 digitos.");
    }

    @ExceptionHandler(SuministroNoRegistradoException.class)
    public ResponseEntity<ProblemDetail> suministroNoRegistrado() {
        return problema(HttpStatus.NOT_FOUND, "Suministro no registrado",
            "No hay un suministro registrado en Nivora con ese codigo.");
    }

    @ExceptionHandler(SupervisionLecturasNoDisponibleException.class)
    public ResponseEntity<ProblemDetail> datosNoDisponibles(RuntimeException error) {
        LOG.error("No se pudo consultar la supervision de lecturas", error);
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Supervision no disponible",
            "No se pudo consultar el estado guardado. Intentalo nuevamente.");
    }

    private static ResponseEntity<ProblemDetail> problema(HttpStatus estado,
            String titulo, String mensaje) {
        var detalle = ProblemDetail.forStatusAndDetail(estado, mensaje);
        detalle.setTitle(titulo);
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(detalle);
    }
}
