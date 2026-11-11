package pe.nivora.backend.acceso.presentacion;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.nivora.backend.acceso.aplicacion.GestionarAdministradores;
import pe.nivora.backend.acceso.dominio.administradores.*;
import pe.nivora.backend.acceso.infraestructura.seguridad.PrincipalAdministrativo;
import static pe.nivora.backend.acceso.presentacion.LectorSolicitudAdministrativa.*;

@RestController
@RequestMapping(value = "/api/v1/administracion/administradores", produces = MediaType.APPLICATION_JSON_VALUE)
public class GestionAdministradoresController {
    private final GestionarAdministradores gestion;
    private final LectorSolicitudAdministrativa lector;

    public GestionAdministradoresController(GestionarAdministradores gestion, LectorSolicitudAdministrativa lector) {
        this.gestion = gestion;
        this.lector = lector;
    }

    @GetMapping
    public ResponseEntity<PaginaRespuesta> listar(@AuthenticationPrincipal PrincipalAdministrativo principal,
            @RequestParam(defaultValue = "") String despuesDe, @RequestParam(defaultValue = "20") int limite) {
        var pagina = gestion.listar(actor(principal), despuesDe, limite);
        return ok(new PaginaRespuesta(pagina.administradores().stream().map(CuentaRespuesta::de).toList(),
            pagina.siguienteDespuesDe(), pagina.hayMas()));
    }

    @GetMapping("/{usuario}")
    public ResponseEntity<CuentaRespuesta> consultar(@AuthenticationPrincipal PrincipalAdministrativo principal,
            @PathVariable String usuario) {
        return ok(CuentaRespuesta.de(gestion.consultar(actor(principal), usuario)));
    }

    @GetMapping("/{usuario}/auditoria")
    public ResponseEntity<PaginaAuditoriaAdministrativa> auditoria(@AuthenticationPrincipal PrincipalAdministrativo principal,
            @PathVariable String usuario, @RequestParam(defaultValue = "0") String despuesDe,
            @RequestParam(defaultValue = "20") int limite) {
        return ok(gestion.auditoria(actor(principal), usuario, despuesDe, limite));
    }

    @PostMapping
    public ResponseEntity<ResultadoRespuesta> crear(@AuthenticationPrincipal PrincipalAdministrativo principal,
            HttpServletRequest solicitud) throws IOException {
        var datos = lector.leer(solicitud, Set.of("usuario", "nombre", "claveInicial", "claveActual"),
            Set.of("gestionarAdministradores"));
        char[] actual = null, inicial = null;
        try {
            actual = texto(datos, "claveActual").toCharArray();
            inicial = texto(datos, "claveInicial").toCharArray();
            var resultado = gestion.crear(actor(principal), actual, texto(datos, "usuario"), texto(datos, "nombre"),
                inicial, datos.has("gestionarAdministradores") && booleano(datos, "gestionarAdministradores"), solicitud.getRemoteAddr());
            return ResponseEntity.created(URI.create("/api/v1/administracion/administradores/" + resultado.cuenta().usuario().valor()))
                .cacheControl(CacheControl.noStore()).body(ResultadoRespuesta.de(resultado));
        } finally {
            if (actual != null) Arrays.fill(actual, '\0');
            if (inicial != null) Arrays.fill(inicial, '\0');
        }
    }

    @PutMapping("/{usuario}/estado")
    public ResponseEntity<ResultadoRespuesta> estado(@AuthenticationPrincipal PrincipalAdministrativo principal,
            @PathVariable String usuario, HttpServletRequest solicitud) throws IOException {
        var datos = lector.leer(solicitud, Set.of("activa", "claveActual"), Set.of());
        char[] actual = texto(datos, "claveActual").toCharArray();
        try { return ok(ResultadoRespuesta.de(gestion.estado(actor(principal), actual, usuario,
            booleano(datos, "activa"), solicitud.getRemoteAddr()))); }
        finally { Arrays.fill(actual, '\0'); }
    }

    @PutMapping("/{usuario}/permisos")
    public ResponseEntity<ResultadoRespuesta> permiso(@AuthenticationPrincipal PrincipalAdministrativo principal,
            @PathVariable String usuario, HttpServletRequest solicitud) throws IOException {
        var datos = lector.leer(solicitud, Set.of("gestionarAdministradores", "claveActual"), Set.of());
        char[] actual = texto(datos, "claveActual").toCharArray();
        try { return ok(ResultadoRespuesta.de(gestion.permiso(actor(principal), actual, usuario,
            booleano(datos, "gestionarAdministradores"), solicitud.getRemoteAddr()))); }
        finally { Arrays.fill(actual, '\0'); }
    }

    @PutMapping("/{usuario}/clave")
    public ResponseEntity<ResultadoRespuesta> restablecer(@AuthenticationPrincipal PrincipalAdministrativo principal,
            @PathVariable String usuario, HttpServletRequest solicitud) throws IOException {
        var datos = lector.leer(solicitud, Set.of("claveTemporal", "claveActual"), Set.of());
        char[] actual = null, temporal = null;
        try {
            actual = texto(datos, "claveActual").toCharArray();
            temporal = texto(datos, "claveTemporal").toCharArray();
            return ok(ResultadoRespuesta.de(gestion.restablecer(actor(principal), actual, usuario,
                temporal, solicitud.getRemoteAddr())));
        } finally {
            if (actual != null) Arrays.fill(actual, '\0');
            if (temporal != null) Arrays.fill(temporal, '\0');
        }
    }

    static ActorAdministrativo actor(PrincipalAdministrativo principal) {
        return new ActorAdministrativo(principal.token(), principal.sesion().cuenta().usuario());
    }
    private static <T> ResponseEntity<T> ok(T cuerpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);
    }
    public record CuentaRespuesta(String usuario, String nombre, boolean activa, boolean gestionarAdministradores,
            boolean cambioClaveRequerido, String versionCredenciales, Instant creadoEn, Instant actualizadoEn) {
        static CuentaRespuesta de(Administrador a) {
            return new CuentaRespuesta(a.usuario().valor(), a.nombre(), a.activa(), a.gestionarAdministradores(),
                a.cambioClaveRequerido(), Long.toString(a.versionCredenciales()), a.creadoEn(), a.actualizadoEn());
        }
    }
    public record ResultadoRespuesta(CuentaRespuesta cuenta, boolean modificada, boolean sesionActualRevocada) {
        static ResultadoRespuesta de(ResultadoGestionAdministrativa r) {
            return new ResultadoRespuesta(CuentaRespuesta.de(r.cuenta()), r.modificada(), r.sesionActualRevocada());
        }
    }
    public record PaginaRespuesta(List<CuentaRespuesta> administradores, String siguienteDespuesDe, boolean hayMas) {}
}
