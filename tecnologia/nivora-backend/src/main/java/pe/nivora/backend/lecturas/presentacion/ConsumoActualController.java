package pe.nivora.backend.lecturas.presentacion;

import java.math.BigDecimal;
import java.time.Duration;
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
import pe.nivora.backend.lecturas.aplicacion.ConsultarEstadisticasConsumo;
import pe.nivora.backend.lecturas.aplicacion.ConsultarHistorialMensual;
import pe.nivora.backend.lecturas.aplicacion.ConsultarHistorialMensual.Periodo;
import pe.nivora.backend.lecturas.dominio.ConsumoPeriodo;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioLecturasNoDisponibleException;
import pe.nivora.backend.lecturas.dominio.contratos.SuministroNoRegistradoException;

@RestController
public class ConsumoActualController {
    private final ConsultarConsumoActual consultar;
    private final ConsultarHistorialMensual historial;
    private final ConsultarEstadisticasConsumo estadisticas;

    public ConsumoActualController(ConsultarConsumoActual consultar, ConsultarHistorialMensual historial,
            ConsultarEstadisticasConsumo estadisticas) {
        this.consultar = consultar;
        this.historial = historial;
        this.estadisticas = estadisticas;
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
        var periodos = resultado.periodos().stream().map(ConsumoActualController::periodo).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new RespuestaHistorial(resultado.fuente(), resultado.codigoSuministro(),
                resultado.zonaHoraria(), resultado.consultadoEn(), periodos));
    }

    @GetMapping(value = "/api/v1/acceso/consulta/estadisticas", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RespuestaEstadisticas> estadisticas(
            @AuthenticationPrincipal PrincipalConsulta principal,
            @RequestParam(name = "meses", defaultValue = "12") int meses) {
        var resultado = estadisticas.ejecutar(principal.sesion().codigoSuministro(), meses);
        var comparacion = resultado.comparacion();
        var comparada = new Comparacion(tramo(comparacion.actual()), tramo(comparacion.anterior()),
            cantidad(decimal(comparacion.diferenciaLitros())));
        var serie = resultado.serieMensual().stream().map(ConsumoActualController::periodo).toList();
        var resumen = resultado.resumen();
        var estadistica = new Resumen(resumen.mesesCompletos(), resumen.mesesParciales(),
            resumen.mesesSinDatos(), resumen.mesesIncompatibles(),
            cantidad(resumen.totalLitros()), cantidad(resumen.promedioLitros()),
            cantidad(decimal(resumen.minimoLitros())), cantidad(decimal(resumen.maximoLitros())));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new RespuestaEstadisticas(resultado.fuente(), resultado.codigoSuministro(),
                resultado.zonaHoraria(), resultado.consultadoEn(), comparada, serie, estadistica));
    }

    private static PeriodoHistorial periodo(Periodo periodo) {
        return new PeriodoHistorial(periodo.periodo(), periodo.inicioMes(), periodo.finMesExclusivo(),
            detalle(periodo.consumo()));
    }

    private static TramoComparado tramo(Periodo periodo) {
        var consumo = periodo.consumo();
        BigDecimal segundos = null;
        if (consumo.desde() != null && consumo.hasta() != null) {
            var duracion = Duration.between(consumo.desde(), consumo.hasta());
            segundos = BigDecimal.valueOf(duracion.toSeconds())
                .add(BigDecimal.valueOf(duracion.getNano(), 9)).stripTrailingZeros();
        }
        return new TramoComparado(periodo.periodo(), detalle(consumo),
            segundos == null ? null : segundos.toPlainString());
    }

    private static BigDecimal decimal(Long litros) {
        return litros == null ? null : BigDecimal.valueOf(litros);
    }

    private static Cantidad cantidad(BigDecimal litros) {
        return litros == null ? null : new Cantidad(litros.toPlainString(), litros.movePointLeft(3).toPlainString());
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
            "Usa de 1 a 12 meses. Si indicas un ultimo mes, debe ser anterior al actual y tener formato AAAA-MM.");
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

    public record Cantidad(String litros, String metrosCubicos) {}

    public record TramoComparado(String periodo, Detalle consumo, String duracionSegundos) {}

    public record Comparacion(TramoComparado actual, TramoComparado anterior, Cantidad diferencia) {}

    public record Resumen(int mesesCompletos, int mesesParciales, int mesesSinDatos,
            int mesesIncompatibles, Cantidad total, Cantidad promedio, Cantidad minimo, Cantidad maximo) {}

    public record RespuestaEstadisticas(String fuente, String codigoSuministro, String zonaHoraria,
            Instant consultadoEn, Comparacion comparacion, List<PeriodoHistorial> serieMensual, Resumen resumen) {}
}
