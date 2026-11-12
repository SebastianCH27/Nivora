package pe.nivora.backend.notificaciones.presentacion;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.nivora.backend.acceso.infraestructura.seguridad.PrincipalAdministrativo;
import pe.nivora.backend.acceso.infraestructura.seguridad.PrincipalConsulta;
import pe.nivora.backend.acceso.presentacion.LectorSolicitudAdministrativa;
import pe.nivora.backend.notificaciones.aplicacion.ConfigurarNotificaciones;
import pe.nivora.backend.notificaciones.aplicacion.ConfigurarNotificaciones.EstadoAlertas;
import pe.nivora.backend.notificaciones.dominio.RepositorioConfiguracionNotificaciones;
import pe.nivora.backend.notificaciones.dominio.RepositorioConfiguracionNotificaciones.Permiso;
import pe.nivora.backend.notificaciones.dominio.RepositorioConfiguracionNotificaciones.Preferencia;

@RestController
public class ConfiguracionNotificacionesController {
    private static final String DISPOSITIVO = "/api/v1/acceso/consulta/notificaciones/dispositivos/{id}";
    private static final String ALERTAS = "/api/v1/administracion/notificaciones/alertas-consumo";
    private final ConfigurarNotificaciones configurar;
    private final LectorSolicitudAdministrativa lector;

    public ConfiguracionNotificacionesController(ConfigurarNotificaciones configurar, LectorSolicitudAdministrativa lector) {
        this.configurar = configurar;
        this.lector = lector;
    }

    @GetMapping(value = DISPOSITIVO, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> consultarDispositivo(@AuthenticationPrincipal PrincipalConsulta principal,
            @PathVariable("id") String id) {
        var preferencia = configurar.consultarDispositivo(principal.sesion().codigoSuministro(), dispositivo(id));
        if (preferencia.isEmpty()) {
            return problema(HttpStatus.NOT_FOUND, "Dispositivo no registrado",
                "El dispositivo carece de preferencias para el suministro de esta sesion.");
        }
        return respuesta(preferencia.orElseThrow());
    }

    @PutMapping(value = DISPOSITIVO, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Preferencia> configurarDispositivo(@AuthenticationPrincipal PrincipalConsulta principal,
            @PathVariable("id") String id, HttpServletRequest solicitud) throws IOException {
        var datos = lector.leer(solicitud, Set.of("permiso", "habilitadas"), Set.of());
        var permiso = Permiso.valueOf(LectorSolicitudAdministrativa.texto(datos, "permiso"));
        boolean habilitadas = LectorSolicitudAdministrativa.booleano(datos, "habilitadas");
        return respuesta(configurar.configurarDispositivo(principal.sesion().codigoSuministro(),
            dispositivo(id), permiso, habilitadas));
    }

    @GetMapping(value = ALERTAS, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EstadoAlertas> consultarAlertas() {
        return respuesta(configurar.consultarAlertas());
    }

    @PutMapping(value = ALERTAS, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EstadoAlertas> configurarAlertas(@AuthenticationPrincipal PrincipalAdministrativo principal,
            HttpServletRequest solicitud) throws IOException {
        var datos = lector.leer(solicitud, Set.of("habilitada"), Set.of());
        return respuesta(configurar.configurarAlertas(LectorSolicitudAdministrativa.booleano(datos, "habilitada"),
            principal.sesion().cuenta().usuario()));
    }

    private static UUID dispositivo(String id) {
        var uuid = UUID.fromString(id);
        if (!uuid.toString().equals(id)) throw new IllegalArgumentException("Usa un UUID canonico.");
        return uuid;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> parametrosInvalidos() {
        return problema(HttpStatus.BAD_REQUEST, "Parametros invalidos",
            "Usa un UUID canonico y activa las notificaciones solo con permiso CONCEDIDO.");
    }

    @ExceptionHandler(LectorSolicitudAdministrativa.FormatoSolicitudException.class)
    ResponseEntity<ProblemDetail> formatoInvalido(LectorSolicitudAdministrativa.FormatoSolicitudException error) {
        return problema(HttpStatusCode.valueOf(error.estado()), "Solicitud invalida",
            "Envia los campos indicados para esta ruta en un objeto JSON UTF-8 de hasta 4096 bytes.");
    }

    @ExceptionHandler(RepositorioConfiguracionNotificaciones.NoDisponible.class)
    ResponseEntity<ProblemDetail> configuracionNoDisponible() {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Configuracion no disponible",
            "No se pudo acceder a la configuracion guardada.");
    }

    private static <T> ResponseEntity<T> respuesta(T cuerpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);
    }

    private static ResponseEntity<ProblemDetail> problema(HttpStatusCode estado, String titulo, String detalle) {
        var cuerpo = ProblemDetail.forStatusAndDetail(estado, detalle);
        cuerpo.setTitle(titulo);
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(cuerpo);
    }
}
