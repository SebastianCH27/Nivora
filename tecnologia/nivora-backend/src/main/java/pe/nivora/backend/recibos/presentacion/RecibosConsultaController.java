package pe.nivora.backend.recibos.presentacion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import pe.nivora.backend.acceso.infraestructura.seguridad.PrincipalConsulta;
import pe.nivora.backend.lecturas.dominio.MesConsumo;
import pe.nivora.backend.recibos.aplicacion.ConsultarRecibos;
import pe.nivora.backend.recibos.dominio.RepositorioRecibos;

@RestController
public class RecibosConsultaController {
    private final ConsultarRecibos consulta;

    public RecibosConsultaController(ConsultarRecibos consulta) {
        this.consulta = consulta;
    }

    @GetMapping(value = "/api/v1/acceso/consulta/recibos", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Respuesta> consultar(@AuthenticationPrincipal PrincipalConsulta principal,
            @RequestParam(name = "limite", defaultValue = "12") int limite,
            @RequestParam(name = "antesDe", required = false) String antesDe) {
        var suministro = principal.sesion().codigoSuministro();
        var pagina = consulta.ejecutar(suministro, interpretarPeriodo(antesDe), limite);
        var recibos = pagina.recibos().stream().map(RecibosConsultaController::recibo).toList();
        var siguiente = pagina.siguienteAntesDe();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Respuesta(
            suministro.valor(), "PEN", MesConsumo.ZONA.getId(), recibos,
            siguiente != null, siguiente == null ? null : siguiente.toString()));
    }

    private static YearMonth interpretarPeriodo(String periodo) {
        if (periodo == null) return null;
        if (!periodo.matches("[1-9][0-9]{3}-(0[1-9]|1[0-2])")) {
            throw new IllegalArgumentException("El periodo debe tener el formato AAAA-MM.");
        }
        return YearMonth.parse(periodo);
    }

    private static Recibo recibo(RepositorioRecibos.Guardado guardado) {
        var emitido = guardado.recibo();
        return new Recibo(emitido.numeroRecibo(), emitido.periodo().toString(),
            emitido.consumoLitros().toPlainString(), emitido.cargoFijo().toPlainString(),
            emitido.importeConsumo().toPlainString(), emitido.importeTotal().toPlainString(),
            emitido.fechaDisponibilidad(), emitido.fechaVencimiento(), emitido.codigoTarifa(),
            emitido.versionTarifa(), guardado.recibidoEn());
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> parametrosInvalidos() {
        return problema(HttpStatus.BAD_REQUEST, "Parametros invalidos",
            "Usa un limite de 1 a 60 y un periodo antesDe en formato AAAA-MM.");
    }

    @ExceptionHandler(RepositorioRecibos.NoDisponible.class)
    ResponseEntity<ProblemDetail> recibosNoDisponibles() {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Recibos no disponibles",
            "No se pudieron consultar los recibos guardados.");
    }

    private static ResponseEntity<ProblemDetail> problema(HttpStatus estado, String titulo, String detalle) {
        var cuerpo = ProblemDetail.forStatusAndDetail(estado, detalle);
        cuerpo.setTitle(titulo);
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(cuerpo);
    }

    public record Respuesta(String codigoSuministro, String moneda, String zonaFechas, List<Recibo> recibos,
            boolean hayMas, String siguienteAntesDe) {}

    public record Recibo(String numeroRecibo, String periodo, String consumoLitros, String cargoFijo,
            String importeConsumo, String importeTotal, LocalDate fechaDisponibilidad, LocalDate fechaVencimiento,
            String codigoTarifa, int versionTarifa, Instant recibidoEn) {}
}
