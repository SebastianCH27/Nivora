package pe.nivora.backend.lecturas.presentacion;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import pe.nivora.backend.acceso.infraestructura.seguridad.PrincipalConsulta;
import pe.nivora.backend.lecturas.aplicacion.ConsultarConsumoActual;
import pe.nivora.backend.lecturas.aplicacion.ConsultarHistorialMensual;
import pe.nivora.backend.lecturas.dominio.ConsumoPeriodo;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioLecturasNoDisponibleException;
import pe.nivora.backend.lecturas.dominio.contratos.SuministroNoRegistradoException;

@RestController
public class ConsumoActualController {
    private final ConsultarConsumoActual consultar;
    private final ConsultarHistorialMensual historial;

    public ConsumoActualController(ConsultarConsumoActual consultar, ConsultarHistorialMensual historial) {
        this.consultar = consultar;
        this.historial = historial;
    }

    @GetMapping(value = "/api/v1/acceso/consulta/consumo-actual",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Respuesta> actual(@AuthenticationPrincipal PrincipalConsulta principal) {
        var resultado = consultar.ejecutar(principal.sesion().codigoSuministro());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new Respuesta(resultado.fuente(), resultado.codigoSuministro(), resultado.periodo(),
                resultado.zonaHoraria(), resultado.inicioMes(), resultado.finMesExclusivo(),
                resultado.consultadoEn(), detalle(resultado.consumo()), resultado.actualizacion()));
    }

    @GetMapping(value = "/api/v1/acceso/consulta/historial", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RespuestaHistorial> historial(@AuthenticationPrincipal PrincipalConsulta principal,
            @RequestParam(name = "meses", defaultValue = "12") int meses,
            @RequestParam(name = "hasta", required = false) String hasta) {
        if (hasta != null && !hasta.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
            throw new IllegalArgumentException("El ultimo mes debe tener formato AAAA-MM.");
        }
        YearMonth ultimo = hasta == null ? null : YearMonth.parse(hasta);
        var resultado = historial.ejecutar(principal.sesion().codigoSuministro(), meses, ultimo);
        var periodos = resultado.periodos().stream()
            .map(p -> new PeriodoHistorial(p.periodo(), p.inicioMes(), p.finMesExclusivo(), detalle(p.consumo())))
            .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new RespuestaHistorial(resultado.fuente(), resultado.codigoSuministro(),
                resultado.zonaHoraria(), resultado.consultadoEn(), periodos));
    }

    private static Detalle detalle(ConsumoPeriodo consumo) {
        return new Detalle(consumo.estado(), consumo.motivo(),
            consumo.litros() == null ? null : consumo.litros().toString(),
            consumo.metrosCubicos() == null ? null : consumo.metrosCubicos().toPlainString(),
            consumo.desde(), consumo.hasta(), consumo.idMedidor(),
            consumo.idLecturaInicial(), consumo.idLecturaFinal());
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> rangoInvalido() {
        return problema(HttpStatus.BAD_REQUEST, "Rango de historial invalido",
            "Usa de 1 a 12 meses y un ultimo mes anterior al actual en formato AAAA-MM.");
    }

    @ExceptionHandler(SuministroNoRegistradoException.class)
    ResponseEntity<ProblemDetail> suministroAusente() {
        return problema(HttpStatus.NOT_FOUND, "Consumo no disponible", "El suministro de la sesion no esta registrado.");
    }

    @ExceptionHandler(RepositorioLecturasNoDisponibleException.class)
    ResponseEntity<ProblemDetail> datosNoDisponibles() {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Consumo no disponible", "No se pudieron consultar las lecturas guardadas.");
    }

    private static ResponseEntity<ProblemDetail> problema(HttpStatus estado, String titulo, String detalle) {
        var cuerpo = ProblemDetail.forStatusAndDetail(estado, detalle);
        cuerpo.setTitle(titulo);
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(cuerpo);
    }

    public record Detalle(ConsumoPeriodo.Estado estado, ConsumoPeriodo.Motivo motivo,
            String litros, String metrosCubicos, Instant desde, Instant hasta,
            String idMedidor, String idLecturaInicial, String idLecturaFinal) {}

    public record Respuesta(String fuente, String codigoSuministro, String periodo,
            String zonaHoraria, Instant inicioMes, Instant finMesExclusivo, Instant consultadoEn,
            Detalle consumo, ConsultarConsumoActual.Actualizacion actualizacion) {}

    public record PeriodoHistorial(String periodo, Instant inicioMes, Instant finMesExclusivo,
            Detalle consumo) {}

    public record RespuestaHistorial(String fuente, String codigoSuministro, String zonaHoraria,
            Instant consultadoEn, List<PeriodoHistorial> periodos) {}
}
