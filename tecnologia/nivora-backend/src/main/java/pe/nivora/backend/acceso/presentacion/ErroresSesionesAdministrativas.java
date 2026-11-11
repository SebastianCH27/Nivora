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

@RestControllerAdvice(assignableTypes = {SesionesAdministrativasController.class, GestionAdministradoresController.class})
public class ErroresSesionesAdministrativas {
    private static final Logger LOG = LoggerFactory.getLogger(ErroresSesionesAdministrativas.class);
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> formato() { return problema(400, "Solicitud invalida", "Revisa los campos: usuario de 3 a 50 caracteres; claves nuevas de 15 a 128; nombre de 1 a 120; limite de 1 a 100 y cursor valido."); }

    @ExceptionHandler(LectorSolicitudAdministrativa.FormatoSolicitudException.class)
    public ResponseEntity<ProblemDetail> formatoHttp(LectorSolicitudAdministrativa.FormatoSolicitudException error) {
        return problema(error.estado(), "Solicitud invalida", "Envie los campos requeridos en un objeto JSON UTF-8 de hasta 4096 bytes, sin campos adicionales ni duplicados.");
    }

    @ExceptionHandler(PermisoGestionAdministrativaException.class)
    public ResponseEntity<ProblemDetail> permiso() { return problema(403, "Permiso insuficiente", "La cuenta no tiene permiso para gestionar administradores."); }

    @ExceptionHandler(AdministradorNoEncontradoException.class)
    public ResponseEntity<ProblemDetail> ausente() { return problema(404, "Administrador no encontrado", "No existe una cuenta administrativa con ese usuario."); }

    @ExceptionHandler(ConflictoGestionAdministrativaException.class)
    public ResponseEntity<ProblemDetail> conflicto(ConflictoGestionAdministrativaException error) {
        return problema(409, "Operacion en conflicto", error.getMessage());
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

    @ExceptionHandler({SesionesAdministrativasNoDisponiblesException.class, RepositorioAdministradoresNoDisponibleException.class})
    public ResponseEntity<ProblemDetail> datos(RuntimeException error) {
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
