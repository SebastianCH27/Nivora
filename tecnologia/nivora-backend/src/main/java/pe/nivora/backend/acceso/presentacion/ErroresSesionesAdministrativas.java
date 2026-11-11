package pe.nivora.backend.acceso.presentacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pe.nivora.backend.acceso.dominio.contratos.*;

@RestControllerAdvice(assignableTypes = SesionesAdministrativasController.class)
public class ErroresSesionesAdministrativas {
    private static final Logger LOG = LoggerFactory.getLogger(ErroresSesionesAdministrativas.class);
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> formato() { return problema(400, "Solicitud invalida", "Revisa el usuario y el formato de la clave."); }

    @ExceptionHandler(SesionesAdministrativasController.FormatoAccesoException.class)
    public ResponseEntity<ProblemDetail> formatoHttp(SesionesAdministrativasController.FormatoAccesoException error) {
        return problema(error.estado(), "Solicitud invalida", "Envie un objeto JSON UTF-8 con usuario y clave, de hasta 4096 bytes.");
    }

    @ExceptionHandler(CredencialesAdministrativasInvalidasException.class)
    public ResponseEntity<ProblemDetail> credenciales() {
        return problema(401, "Acceso no valido", "Credenciales administrativas incorrectas.");
    }

    @ExceptionHandler(AccesoAdministrativoLimitadoException.class)
    public ResponseEntity<ProblemDetail> limite(AccesoAdministrativoLimitadoException error) {
        return ResponseEntity.status(429).cacheControl(CacheControl.noStore())
            .header("Retry-After", Long.toString(error.segundos()))
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(detalle(429, "Demasiados intentos", "Espera antes de intentar acceder nuevamente."));
    }

    @ExceptionHandler(SesionesAdministrativasNoDisponiblesException.class)
    public ResponseEntity<ProblemDetail> datos(SesionesAdministrativasNoDisponiblesException error) {
        LOG.warn("No se pudo completar una operacion de acceso administrativo.");
        LOG.debug("Detalle del fallo de acceso administrativo", error);
        return problema(503, "Acceso no disponible", "No se pudo completar la operacion de acceso.");
    }

    private static ResponseEntity<ProblemDetail> problema(int estado, String titulo, String mensaje) {
        var respuesta = ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (estado == 401) respuesta.header("WWW-Authenticate", "Bearer");
        return respuesta.body(detalle(estado, titulo, mensaje));
    }

    private static ProblemDetail detalle(int estado, String titulo, String mensaje) {
        var cuerpo = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(estado), mensaje);
        cuerpo.setTitle(titulo);
        return cuerpo;
    }
}
