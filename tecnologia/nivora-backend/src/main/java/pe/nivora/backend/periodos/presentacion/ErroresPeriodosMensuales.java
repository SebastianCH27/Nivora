package pe.nivora.backend.periodos.presentacion;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pe.nivora.backend.periodos.dominio.RepositorioPeriodosMensuales;

@RestControllerAdvice
public class ErroresPeriodosMensuales {
    @ExceptionHandler(RepositorioPeriodosMensuales.NoDisponible.class)
    ResponseEntity<ProblemDetail> noDisponible() {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
            "No se pudo consultar o actualizar el calendario mensual de Nivora.");
        problema.setTitle("Calendario mensual no disponible");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }
}
