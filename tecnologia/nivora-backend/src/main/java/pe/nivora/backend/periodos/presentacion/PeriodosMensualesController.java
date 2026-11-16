package pe.nivora.backend.periodos.presentacion;

import java.io.IOException;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.nivora.backend.acceso.infraestructura.seguridad.PrincipalAdministrativo;
import pe.nivora.backend.acceso.presentacion.LectorSolicitudAdministrativa;
import pe.nivora.backend.lecturas.dominio.MesConsumo;
import pe.nivora.backend.periodos.aplicacion.GestionarPeriodosMensuales;
import pe.nivora.backend.periodos.dominio.CalendarioMensual;
import pe.nivora.backend.periodos.dominio.CalendarioMensual.Cambio;
import pe.nivora.backend.periodos.dominio.CalendarioMensual.Publicacion;
import pe.nivora.backend.periodos.dominio.RepositorioPeriodosMensuales;

import static pe.nivora.backend.acceso.presentacion.LectorSolicitudAdministrativa.texto;

@RestController
@RequestMapping(value = "/api/v1/administracion/periodos-mensuales", produces = MediaType.APPLICATION_JSON_VALUE)
public class PeriodosMensualesController {
    private final GestionarPeriodosMensuales gestion;
    private final LectorSolicitudAdministrativa lector;

    public PeriodosMensualesController(GestionarPeriodosMensuales gestion, LectorSolicitudAdministrativa lector) {
        this.gestion = gestion;
        this.lector = lector;
    }

    @GetMapping
    public ResponseEntity<Vista> consultar() {
        var vista = gestion.consultar();
        var calendario = vista.calendario();
        var actual = vista.actual();
        return ok(new Vista(Long.toString(calendario.revision()), MesConsumo.ZONA.getId(),
            CalendarioMensual.DIA_PREDETERMINADO,
            new Periodo(actual.valor().toString(), actual.inicio(), actual.fin()),
            calendario.reglas().stream().map(PeriodosMensualesController::regla).toList()));
    }

    @PutMapping
    public ResponseEntity<Regla> publicar(@AuthenticationPrincipal PrincipalAdministrativo principal,
            HttpServletRequest solicitud) throws IOException {
        var datos = lector.leer(solicitud, Set.of("revisionEsperada", "desde", "diaInicio"), Set.of());
        String revision = texto(datos, "revisionEsperada");
        String desde = texto(datos, "desde");
        var dia = datos.path("diaInicio");
        if (!revision.matches("[0-9]{1,19}") || !desde.matches("[1-9][0-9]{3}-(0[1-9]|1[0-2])")
                || !dia.isIntegralNumber() || !dia.canConvertToInt()) {
            throw new IllegalArgumentException("Regla de periodo invalida.");
        }
        var cambio = new Cambio(YearMonth.parse(desde), dia.intValue());
        return ok(regla(gestion.publicar(Long.parseLong(revision), cambio, principal.sesion().cuenta().usuario())));
    }

    private static Regla regla(Publicacion publicada) {
        return new Regla(Long.toString(publicada.revision()), publicada.cambio().desde().toString(),
            publicada.cambio().diaInicio(), publicada.publicadaEn(), publicada.publicadaPor());
    }

    private static <T> ResponseEntity<T> ok(T respuesta) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(respuesta);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> reglaInvalida() {
        return problema(400, "Regla de periodo invalida",
            "Indica una revision valida, un mes civil futuro AAAA-MM y un dia entero de 1 a 31.");
    }

    @ExceptionHandler(LectorSolicitudAdministrativa.FormatoSolicitudException.class)
    ResponseEntity<ProblemDetail> formato(LectorSolicitudAdministrativa.FormatoSolicitudException error) {
        return problema(error.estado(), "Solicitud invalida", "Usa un objeto JSON UTF-8 valido de hasta 4096 bytes.");
    }

    @ExceptionHandler(RepositorioPeriodosMensuales.Conflicto.class)
    ResponseEntity<ProblemDetail> conflicto(RepositorioPeriodosMensuales.Conflicto error) {
        return problema(409, "Publicacion en conflicto", error.getMessage());
    }

    private static ResponseEntity<ProblemDetail> problema(int estado, String titulo, String detalle) {
        var cuerpo = ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(estado), detalle);
        cuerpo.setTitle(titulo);
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(cuerpo);
    }

    public record Vista(String revision, String zonaHoraria, int diaInicioPredeterminado,
            Periodo actual, List<Regla> reglas) {}

    public record Periodo(String periodo, Instant inicioMes, Instant finMesExclusivo) {}

    public record Regla(String revision, String desde, int diaInicio, Instant publicadaEn, String publicadaPor) {}
}
