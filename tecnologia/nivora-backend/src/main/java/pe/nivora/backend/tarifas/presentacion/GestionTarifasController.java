package pe.nivora.backend.tarifas.presentacion;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.infraestructura.seguridad.PrincipalAdministrativo;
import pe.nivora.backend.acceso.presentacion.LectorSolicitudAdministrativa;
import pe.nivora.backend.tarifas.aplicacion.GestionarTarifas;
import pe.nivora.backend.tarifas.dominio.HistorialTarifario.Asignacion;
import pe.nivora.backend.tarifas.dominio.RepositorioTarifas;
import pe.nivora.backend.tarifas.dominio.VersionTarifaria;

import static pe.nivora.backend.acceso.presentacion.LectorSolicitudAdministrativa.texto;
import static pe.nivora.backend.acceso.presentacion.LectorSolicitudAdministrativa.validarCampos;

@RestController
@RequestMapping(value = "/api/v1/administracion/suministros/{codigo}/tarifas",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class GestionTarifasController {
    private final GestionarTarifas gestion;
    private final LectorSolicitudAdministrativa lector;

    public GestionTarifasController(GestionarTarifas gestion, LectorSolicitudAdministrativa lector) {
        this.gestion = gestion;
        this.lector = lector;
    }

    @GetMapping
    public ResponseEntity<Vista> consultar(@PathVariable String codigo) {
        var datos = gestion.consultar(new CodigoSuministro(codigo));
        var importada = datos.importado() == null ? null : historial(datos.importado());
        var publicada = datos.publicada() == null ? null : publicacion(datos.publicada());
        return ok(new Vista(codigo, "PEN", "America/Lima",
            publicada == null ? "0" : publicada.historial().revision(), importada, publicada));
    }

    @GetMapping("/revisiones/{revision}")
    public ResponseEntity<Publicacion> consultar(@PathVariable String codigo, @PathVariable String revision) {
        return ok(publicacion(gestion.consultar(new CodigoSuministro(codigo), revision(revision, 1))));
    }

    @PutMapping
    public ResponseEntity<Publicacion> publicar(@PathVariable String codigo,
            @AuthenticationPrincipal PrincipalAdministrativo principal, HttpServletRequest solicitud) throws IOException {
        var datos = lector.leer(solicitud, Set.of("revisionEsperada", "codigoTarifa", "versionTarifa",
            "nombreTarifa", "vigenteDesde", "asignadaDesde", "cargoFijo", "tramos"),
            Set.of("asignadaHasta", "igvPorcentaje"));
        long revision = revision(texto(datos, "revisionEsperada"), 0);
        var numero = datos.path("versionTarifa");
        if (!numero.isIntegralNumber() || !numero.canConvertToInt()) {
            throw new IllegalArgumentException("La version debe ser un entero positivo.");
        }
        var vigenteDesde = LocalDate.parse(texto(datos, "vigenteDesde"));
        var asignadaDesde = LocalDate.parse(texto(datos, "asignadaDesde"));
        var asignadaHasta = datos.hasNonNull("asignadaHasta")
            ? LocalDate.parse(texto(datos, "asignadaHasta")) : null;
        var tarifa = new VersionTarifaria(texto(datos, "codigoTarifa"), numero.intValue(),
            texto(datos, "nombreTarifa"), vigenteDesde, decimal(datos, "cargoFijo", false),
            decimal(datos, "igvPorcentaje", true), tramos(datos.path("tramos")));
        var asignacion = new Asignacion(tarifa, null, asignadaDesde, asignadaHasta);
        var actor = principal.sesion().cuenta().usuario();
        return ok(publicacion(gestion.publicar(new CodigoSuministro(codigo), revision, asignacion, actor)));
    }

    private static List<VersionTarifaria.Tramo> tramos(JsonNode datos) {
        if (!datos.isArray()) throw new IllegalArgumentException("Los tramos deben enviarse en una lista.");
        var tramos = new ArrayList<VersionTarifaria.Tramo>();
        for (var dato : datos) {
            validarCampos(dato, Set.of("desdeM3", "precioAguaPorM3"),
                Set.of("hastaM3", "precioAlcantarilladoPorM3"));
            tramos.add(new VersionTarifaria.Tramo(decimal(dato, "desdeM3", false), decimal(dato, "hastaM3", true),
                decimal(dato, "precioAguaPorM3", false), decimal(dato, "precioAlcantarilladoPorM3", true)));
        }
        return tramos;
    }

    private static BigDecimal decimal(JsonNode datos, String campo, boolean opcional) {
        var valor = datos.path(campo);
        if (opcional && (valor.isMissingNode() || valor.isNull())) return null;
        if (!valor.isNumber()) throw new IllegalArgumentException("Los importes y los limites deben ser numericos.");
        return valor.decimalValue();
    }

    private static long revision(String valor, long minimo) {
        if (valor.matches("[0-9]{1,19}")) {
            long numero = Long.parseLong(valor);
            if (numero >= minimo) return numero;
        }
        throw new IllegalArgumentException("La revision indicada no es valida.");
    }

    private static Publicacion publicacion(RepositorioTarifas.Publicacion datos) {
        return new Publicacion(historial(datos.guardado()), datos.publicadoPor());
    }

    private static Historial historial(RepositorioTarifas.Guardado datos) {
        var asignaciones = datos.historial().asignaciones().stream().map(asignacion -> {
            var tarifa = asignacion.tarifa();
            return new Vigencia(tarifa.codigo(), tarifa.version(), tarifa.nombre(), tarifa.vigenteDesde(),
                asignacion.vigenteHasta(), asignacion.asignadaDesde(), asignacion.asignadaHasta(),
                asignacion.aplicableDesde(), asignacion.aplicableHasta(), tarifa.cargoFijo(), tarifa.igvPorcentaje(),
                tarifa.tramos());
        }).toList();
        return new Historial(datos.historial().fuente(), datos.historial().suministro().valor(),
            Long.toString(datos.revision()), datos.consultadoEn(), asignaciones);
    }

    private static <T> ResponseEntity<T> ok(T respuesta) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(respuesta);
    }

    @ExceptionHandler({IllegalArgumentException.class, DateTimeException.class})
    ResponseEntity<ProblemDetail> invalida() {
        return problema(400, "Solicitud invalida", "Revisa el codigo, revision, fechas, importes y tramos tarifarios.");
    }

    @ExceptionHandler(LectorSolicitudAdministrativa.FormatoSolicitudException.class)
    ResponseEntity<ProblemDetail> formato(LectorSolicitudAdministrativa.FormatoSolicitudException error) {
        return problema(error.estado(), "Solicitud invalida", "Usa un objeto JSON UTF-8 valido de hasta 4096 bytes.");
    }

    @ExceptionHandler(RepositorioTarifas.Conflicto.class)
    ResponseEntity<ProblemDetail> conflicto(RepositorioTarifas.Conflicto error) {
        return problema(409, "Publicacion en conflicto", error.getMessage());
    }

    @ExceptionHandler(RepositorioTarifas.SuministroNoEncontrado.class)
    ResponseEntity<ProblemDetail> suministroAusente() {
        return problema(404, "Suministro no encontrado", "El suministro debe estar registrado en Nivora.");
    }

    @ExceptionHandler(RepositorioTarifas.RevisionNoEncontrada.class)
    ResponseEntity<ProblemDetail> revisionAusente() {
        return problema(404, "Revision no encontrada", "No hay una publicacion con esa revision para el suministro.");
    }

    @ExceptionHandler({DataAccessException.class, IllegalStateException.class})
    ResponseEntity<ProblemDetail> noDisponible() {
        return problema(503, "Gestion tarifaria no disponible", "No se pudo completar la operacion tarifaria.");
    }

    private static ResponseEntity<ProblemDetail> problema(int estado, String titulo, String detalle) {
        var cuerpo = ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(estado), detalle);
        cuerpo.setTitle(titulo);
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(cuerpo);
    }

    public record Vista(String codigoSuministro, String moneda, String zonaFechas, String revisionAdministrativa,
            Historial importada, Publicacion publicada) {}

    public record Publicacion(Historial historial, String publicadoPor) {}

    public record Historial(String fuente, String codigoSuministro, String revision, Instant registradoEn,
            List<Vigencia> asignaciones) {}

    public record Vigencia(String codigoTarifa, int versionTarifa, String nombreTarifa, LocalDate vigenteDesde,
            LocalDate vigenteHasta, LocalDate asignadaDesde, LocalDate asignadaHasta, LocalDate aplicableDesde,
            LocalDate aplicableHasta, BigDecimal cargoFijo, BigDecimal igvPorcentaje,
            List<VersionTarifaria.Tramo> tramos) {}
}
