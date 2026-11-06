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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import pe.nivora.empresaagua.consumo.ConsumoActual;
import pe.nivora.empresaagua.consumo.ConsultarConsumoActual;
import pe.nivora.empresaagua.consumo.ConsultarHistorialConsumo;
import pe.nivora.empresaagua.consumo.HistorialConsumo;

@RestController
@RequestMapping("/api/v1/suministros")
public class ConsumoController {

    private static final Logger log =
        LoggerFactory.getLogger(ConsumoController.class);

    private final ConsultarConsumoActual consulta;
    private final ConsultarHistorialConsumo historial;

    public ConsumoController(
            ConsultarConsumoActual consulta,
            ConsultarHistorialConsumo historial) {

        this.consulta = consulta;
        this.historial = historial;
    }

    @GetMapping("/{codigo}/consumo-actual")
    public ResponseEntity<ConsumoActual> consultar(
            @PathVariable("codigo") String codigo) {

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(consulta.consultar(codigo));
    }

    @GetMapping("/{codigo}/historial")
    public ResponseEntity<HistorialConsumo> consultarHistorial(
            @PathVariable("codigo") String codigo,
            @RequestParam(
                value = "meses",
                defaultValue = "12"
            ) int meses) {

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(historial.consultar(codigo, meses));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> solicitudInvalida(
            IllegalArgumentException error) {

        return problema(
            HttpStatus.BAD_REQUEST,
            "Solicitud invalida",
            error.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> parametroInvalido() {
        return problema(
            HttpStatus.BAD_REQUEST,
            "Parametro invalido",
            "El parametro meses debe ser un numero entero.");
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
    public ResponseEntity<ProblemDetail> consumoNoDisponible(
            IllegalStateException error) {

        return problema(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Consumo no disponible",
            error.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> falloDatos(
            DataAccessException error) {

        log.error(
            "Fallo al consultar el consumo del suministro",
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