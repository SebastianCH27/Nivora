package pe.nivora.backend.notificaciones.presentacion;

import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.nivora.backend.acceso.infraestructura.seguridad.PrincipalConsulta;
import pe.nivora.backend.acceso.presentacion.LectorSolicitudAdministrativa;
import pe.nivora.backend.notificaciones.dominio.EmisorWebPush;
import pe.nivora.backend.notificaciones.dominio.RepositorioWebPush;
import pe.nivora.backend.notificaciones.dominio.SuscripcionWebPush;

@RestController
@RequestMapping("/api/v1/acceso/consulta/notificaciones")
public class WebPushController {
    private static final String SUSCRIPCION = "/dispositivos/{id}/suscripcion";
    private final RepositorioWebPush repositorio;
    private final ObjectProvider<EmisorWebPush> emisor;
    private final LectorSolicitudAdministrativa lector;
    private final Clock reloj;

    public WebPushController(RepositorioWebPush repositorio, ObjectProvider<EmisorWebPush> emisor,
            LectorSolicitudAdministrativa lector, Clock reloj) {
        this.repositorio = repositorio;
        this.emisor = emisor;
        this.lector = lector;
        this.reloj = reloj;
    }

    @GetMapping(value = "/web-push/clave-publica", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> consultarClave() {
        var disponible = emisor.getIfAvailable();
        if (disponible == null) return noDisponible();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ClavePublica(disponible.clavePublica()));
    }

    @PutMapping(SUSCRIPCION)
    public ResponseEntity<?> suscribir(@AuthenticationPrincipal PrincipalConsulta principal,
            @PathVariable("id") String id, HttpServletRequest solicitud) throws IOException {
        if (emisor.getIfAvailable() == null) return noDisponible();
        var datos = lector.leer(solicitud, Set.of("endpoint", "keys"), Set.of());
        var claves = datos.get("keys");
        LectorSolicitudAdministrativa.validarCampos(claves, Set.of("p256dh", "auth"), Set.of());
        var suscripcion = new SuscripcionWebPush(LectorSolicitudAdministrativa.texto(datos, "endpoint"),
            LectorSolicitudAdministrativa.texto(claves, "p256dh"), LectorSolicitudAdministrativa.texto(claves, "auth"));
        return switch (repositorio.suscribir(principal.sesion().codigoSuministro(), dispositivo(id),
                suscripcion, reloj.instant())) {
            case GUARDADA -> sinContenido();
            case PREFERENCIA_INACTIVA -> problema(HttpStatus.CONFLICT, "Preferencia inactiva",
                "Activa las notificaciones del dispositivo con permiso concedido antes de suscribirlo.");
            case ENDPOINT_OCUPADO -> problema(HttpStatus.CONFLICT, "Suscripcion ya asociada",
                "Retira la asociacion anterior o usa una suscripcion nueva.");
        };
    }

    @DeleteMapping(SUSCRIPCION)
    public ResponseEntity<Void> eliminar(@AuthenticationPrincipal PrincipalConsulta principal,
            @PathVariable("id") String id) {
        repositorio.eliminar(principal.sesion().codigoSuministro(), dispositivo(id));
        return sinContenido();
    }

    private static UUID dispositivo(String id) {
        var uuid = UUID.fromString(id);
        if (!uuid.toString().equals(id)) throw new IllegalArgumentException("Usa un UUID canonico.");
        return uuid;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> invalida() {
        return problema(HttpStatus.BAD_REQUEST, "Suscripcion invalida",
            "Comprueba el dispositivo, las claves y el proveedor Web Push.");
    }

    @ExceptionHandler(LectorSolicitudAdministrativa.FormatoSolicitudException.class)
    ResponseEntity<ProblemDetail> formato(LectorSolicitudAdministrativa.FormatoSolicitudException error) {
        return problema(HttpStatusCode.valueOf(error.estado()), "Solicitud invalida",
            "Envia endpoint y keys con p256dh y auth en un objeto JSON UTF-8 de hasta 4096 bytes.");
    }

    @ExceptionHandler(RepositorioWebPush.NoDisponible.class)
    ResponseEntity<ProblemDetail> noDisponible() {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Web Push no disponible",
            "No se pudo acceder a la configuracion o al almacenamiento Web Push.");
    }

    private static ResponseEntity<Void> sinContenido() {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static ResponseEntity<ProblemDetail> problema(HttpStatusCode estado, String titulo, String detalle) {
        var cuerpo = ProblemDetail.forStatusAndDetail(estado, detalle);
        cuerpo.setTitle(titulo);
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(cuerpo);
    }

    public record ClavePublica(String clavePublica) {}
}
