package pe.nivora.backend.tarifas.presentacion;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.nivora.backend.acceso.infraestructura.seguridad.PrincipalConsulta;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioLecturasNoDisponibleException;
import pe.nivora.backend.lecturas.dominio.contratos.SuministroNoRegistradoException;
import pe.nivora.backend.tarifas.aplicacion.ConsultarComparacionCostos;
import pe.nivora.backend.tarifas.dominio.ComparacionCostos;

@RestController
public class ComparacionCostosController {
    private final ConsultarComparacionCostos consultar;

    public ComparacionCostosController(ConsultarComparacionCostos consultar) {
        this.consultar = consultar;
    }

    @GetMapping(value = "/api/v1/acceso/consulta/comparacion", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Respuesta> comparar(@AuthenticationPrincipal PrincipalConsulta principal,
            @RequestParam(name = "periodoBase", required = false) String periodoBase,
            @RequestParam(name = "periodoComparado", required = false) String periodoComparado) {
        var resultado = consultar.ejecutar(principal.sesion().codigoSuministro(), mes(periodoBase), mes(periodoComparado));
        var diferencia = resultado.diferencia();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Respuesta(
            resultado.fuente(), resultado.codigoSuministro(), "PEN", resultado.zonaHoraria(),
            resultado.consultadoEn(), texto(resultado.revisionTarifaria()), texto(resultado.revisionTarifariaAdministrativa()),
            tramo(resultado.base()), tramo(resultado.comparado()), diferencia.estado(),
            texto(diferencia.diferenciaLitros()), decimal(diferencia.diferenciaMetrosCubicos()),
            decimal(diferencia.diferenciaCostoEstimado()), resultado.mismaDuracionTramo(), resultado.mismaVersionTarifaria()));
    }

    private static YearMonth mes(String texto) {
        if (texto == null) return null;
        if (!texto.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
            throw new IllegalArgumentException("El periodo debe tener formato AAAA-MM.");
        }
        return YearMonth.parse(texto);
    }

    private static Tramo tramo(ConsultarComparacionCostos.Tramo t) {
        return new Tramo(CostosConsumoController.periodo(t.estimacion()), t.enCurso(), t.periodoCompleto(),
            segundos(t.duracionPeriodo()), segundos(t.duracionTramo()));
    }

    private static String segundos(Duration d) {
        return d == null ? null : BigDecimal.valueOf(d.toNanos(), 9).stripTrailingZeros().toPlainString();
    }
    private static String texto(Long n) { return n == null ? null : n.toString(); }
    private static String decimal(BigDecimal n) { return n == null ? null : n.toPlainString(); }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> periodosInvalidos() {
        return problema(HttpStatus.BAD_REQUEST, "Periodos de comparación inválidos",
            "Indica ambos periodos como AAAA-MM, sin periodos futuros y con hasta 12 meses de separación; o utiliza los valores predeterminados.");
    }

    @ExceptionHandler(SuministroNoRegistradoException.class)
    ResponseEntity<ProblemDetail> suministroAusente() {
        return problema(HttpStatus.NOT_FOUND, "Comparación no disponible", "El suministro de la sesión no está registrado.");
    }

    @ExceptionHandler({DataAccessException.class, RepositorioLecturasNoDisponibleException.class, IllegalStateException.class})
    ResponseEntity<ProblemDetail> datosNoDisponibles() {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Comparación no disponible",
            "No se pudieron consultar las lecturas, tarifas o periodos guardados.");
    }

    private static ResponseEntity<ProblemDetail> problema(HttpStatus estado, String titulo, String detalle) {
        var p = ProblemDetail.forStatusAndDetail(estado, detalle);
        p.setTitle(titulo);
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(p);
    }

    public record Tramo(CostosConsumoController.PeriodoCosto estimacion, boolean enCurso,
            boolean periodoCompleto, String duracionPeriodoSegundos, String duracionTramoSegundos) {}

    public record Respuesta(String fuente, String codigoSuministro, String moneda, String zonaHoraria,
            Instant consultadoEn, String revisionTarifaria, String revisionTarifariaAdministrativa,
            Tramo base, Tramo comparado, ComparacionCostos.Estado estado, String diferenciaLitros,
            String diferenciaMetrosCubicos, String diferenciaCostoEstimado,
            Boolean mismaDuracionTramo, Boolean mismaVersionTarifaria) {}
}
