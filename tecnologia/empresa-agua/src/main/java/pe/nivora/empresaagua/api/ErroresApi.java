package pe.nivora.empresaagua.api;

import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(
    basePackages = "pe.nivora.empresaagua.api"
)
public class ErroresApi {

    private static final Logger log =
        LoggerFactory.getLogger(ErroresApi.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> solicitudInvalida(
            IllegalArgumentException error) {

        return problema(
            HttpStatus.BAD_REQUEST,
            "Solicitud invalida",
            error.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> parametroInvalido(
            MethodArgumentTypeMismatchException error) {

        return problema(
            HttpStatus.BAD_REQUEST,
            "Parametro invalido",
            "El parametro " + error.getName()
                + " debe ser un numero entero.");
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ProblemDetail> noEncontrado(
            NoSuchElementException error) {

        return problema(
            HttpStatus.NOT_FOUND,
            "Suministro no encontrado",
            error.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> datosNoDisponibles(
            IllegalStateException error) {

        return problema(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Datos no disponibles",
            error.getMessage());
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ProblemDetail> falloDatos(
            RuntimeException error) {

        log.error(
            "Fallo al consultar los datos de la Empresa de Agua",
            error);

        return problema(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Servicio no disponible",
            "El servicio de datos no esta disponible temporalmente.");
    }

    private ResponseEntity<ProblemDetail> problema(
            HttpStatus estado,
            String titulo,
            String mensaje) {

        ProblemDetail detalle =
            ProblemDetail.forStatusAndDetail(estado, mensaje);

        detalle.setTitle(titulo);

        return ResponseEntity.status(estado)
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(detalle);
    }
}
