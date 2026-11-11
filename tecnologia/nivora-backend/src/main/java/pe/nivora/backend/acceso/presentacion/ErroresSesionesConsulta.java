package pe.nivora.backend.acceso.presentacion;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pe.nivora.backend.acceso.dominio.contratos.AccesoConsultaLimitadoException;
import pe.nivora.backend.acceso.dominio.contratos.FuenteIdentidadNoDisponibleException;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministrosNoDisponibleException;
import pe.nivora.backend.acceso.dominio.contratos.SesionesConsultaNoDisponiblesException;

@RestControllerAdvice(assignableTypes = SesionesConsultaController.class)
public class ErroresSesionesConsulta {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> formato() {
        return problema(400, "Solicitud invalida",
            "Ingresa un codigo de suministro de 1 a 20 digitos y un DNI de 8 digitos.");
    }

    @ExceptionHandler(LectorSolicitudAdministrativa.FormatoSolicitudException.class)
    public ResponseEntity<ProblemDetail> formatoHttp(LectorSolicitudAdministrativa.FormatoSolicitudException error) {
        return problema(error.estado(), "Solicitud invalida",
            "Envia codigoSuministro y dni como texto en un objeto JSON UTF-8 de hasta 4096 bytes.");
    }

    @ExceptionHandler(SesionesConsultaController.AccesoNoValido.class)
    public ResponseEntity<ProblemDetail> accesoNoValido() {
        return problema(401, "Acceso no valido", "Revisa el codigo de suministro y el DNI ingresados.");
    }

    @ExceptionHandler(FuenteSuministrosNoDisponibleException.class)
    public ResponseEntity<ProblemDetail> aguaNoDisponible() {
        return problema(503, "Validacion no disponible", "No se pudo validar el suministro.");
    }

    @ExceptionHandler(FuenteIdentidadNoDisponibleException.class)
    public ResponseEntity<ProblemDetail> identidadNoDisponible() {
        return problema(503, "Validacion no disponible", "No se pudo confirmar el DNI con la fuente de identidad.");
    }

    @ExceptionHandler(SesionesConsultaNoDisponiblesException.class)
    public ResponseEntity<ProblemDetail> datosNoDisponibles() {
        return problema(503, "Acceso no disponible", "No se pudo completar la operacion de sesion de consulta.");
    }

    @ExceptionHandler(AccesoConsultaLimitadoException.class)
    public ResponseEntity<ProblemDetail> limite(AccesoConsultaLimitadoException error) {
        return ResponseEntity.status(429).cacheControl(CacheControl.noStore())
            .header("Retry-After", Long.toString(error.segundos()))
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(detalle(429, "Demasiados intentos", "Espera antes de iniciar otra sesion de consulta."));
    }

    private static ResponseEntity<ProblemDetail> problema(int estado, String titulo, String mensaje) {
        var respuesta = ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (estado == 401) {
            respuesta.header("WWW-Authenticate", "Bearer");
        }
        return respuesta.body(detalle(estado, titulo, mensaje));
    }

    private static ProblemDetail detalle(int estado, String titulo, String mensaje) {
        var cuerpo = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(estado), mensaje);
        cuerpo.setTitle(titulo);
        return cuerpo;
    }
}
