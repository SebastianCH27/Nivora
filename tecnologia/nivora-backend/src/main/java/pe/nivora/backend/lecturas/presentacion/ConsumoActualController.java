package pe.nivora.backend.lecturas.presentacion;

import java.time.Instant;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.nivora.backend.acceso.infraestructura.seguridad.PrincipalConsulta;
import pe.nivora.backend.lecturas.aplicacion.ConsultarConsumoActual;
import pe.nivora.backend.lecturas.dominio.ConsumoPeriodo;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioLecturasNoDisponibleException;
import pe.nivora.backend.lecturas.dominio.contratos.SuministroNoRegistradoException;

@RestController
public class ConsumoActualController {
    private final ConsultarConsumoActual consultar;

    public ConsumoActualController(ConsultarConsumoActual consultar) {
        this.consultar = consultar;
    }

    @GetMapping(value = "/api/v1/acceso/consulta/consumo-actual",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Respuesta> actual(@AuthenticationPrincipal PrincipalConsulta principal) {
        var resultado = consultar.ejecutar(principal.sesion().codigoSuministro());
        var consumo = resultado.consumo();
        var detalle = new Detalle(consumo.estado(), consumo.motivo(),
            consumo.litros() == null ? null : consumo.litros().toString(),
            consumo.metrosCubicos() == null ? null : consumo.metrosCubicos().toPlainString(),
            consumo.desde(), consumo.hasta(), consumo.idMedidor(),
            consumo.idLecturaInicial(), consumo.idLecturaFinal());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new Respuesta(resultado.fuente(), resultado.codigoSuministro(), resultado.periodo(),
                resultado.zonaHoraria(), resultado.inicioMes(), resultado.finMesExclusivo(),
                resultado.consultadoEn(), detalle, resultado.actualizacion()));
    }

    @ExceptionHandler(SuministroNoRegistradoException.class)
    ResponseEntity<ProblemDetail> suministroAusente() {
        return problema(HttpStatus.NOT_FOUND, "El suministro de la sesion no esta registrado.");
    }

    @ExceptionHandler(RepositorioLecturasNoDisponibleException.class)
    ResponseEntity<ProblemDetail> datosNoDisponibles() {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "No se pudieron consultar las lecturas guardadas.");
    }

    private static ResponseEntity<ProblemDetail> problema(HttpStatus estado, String detalle) {
        var cuerpo = ProblemDetail.forStatusAndDetail(estado, detalle);
        cuerpo.setTitle("Consumo no disponible");
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(cuerpo);
    }

    public record Detalle(ConsumoPeriodo.Estado estado, ConsumoPeriodo.Motivo motivo,
            String litros, String metrosCubicos, Instant desde, Instant hasta,
            String idMedidor, String idLecturaInicial, String idLecturaFinal) {}

    public record Respuesta(String fuente, String codigoSuministro, String periodo,
            String zonaHoraria, Instant inicioMes, Instant finMesExclusivo, Instant consultadoEn,
            Detalle consumo, ConsultarConsumoActual.Actualizacion actualizacion) {}
}
