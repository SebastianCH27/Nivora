package pe.nivora.backend.acceso.presentacion;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.nivora.backend.acceso.aplicacion.GestionarAdministradores;
import pe.nivora.backend.acceso.aplicacion.SesionesAdministrativas;
import pe.nivora.backend.acceso.dominio.administradores.SesionAdministrativa;
import pe.nivora.backend.acceso.infraestructura.seguridad.PrincipalAdministrativo;

@RestController
@RequestMapping("/api/v1/acceso/administradores")
public class SesionesAdministrativasController {
    private final SesionesAdministrativas sesiones;
    private final LectorSolicitudAdministrativa lector;
    private final GestionarAdministradores gestion;

    public SesionesAdministrativasController(SesionesAdministrativas sesiones,
            LectorSolicitudAdministrativa lector, GestionarAdministradores gestion) {
        this.sesiones = sesiones;
        this.lector = lector;
        this.gestion = gestion;
    }

    @PostMapping(value = "/sesiones", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InicioRespuesta> iniciar(HttpServletRequest solicitud) throws IOException {
        var datos = lector.leer(solicitud, Set.of("usuario", "clave"), Set.of());
        char[] clave = null;
        try {
            clave = LectorSolicitudAdministrativa.texto(datos, "clave").toCharArray();
            var inicio = sesiones.iniciar(LectorSolicitudAdministrativa.texto(datos, "usuario"), clave, solicitud.getRemoteAddr());
            return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(new InicioRespuesta("Bearer", inicio.token().valor(), DatosSesion.de(inicio.sesion())));
        } finally {
            if (clave != null) Arrays.fill(clave, '\0');
        }
    }

    @GetMapping(value = "/sesion", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DatosSesion> actual(@AuthenticationPrincipal PrincipalAdministrativo principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(DatosSesion.de(principal.sesion()));
    }

    @DeleteMapping("/sesion")
    public ResponseEntity<Void> cerrar(@AuthenticationPrincipal PrincipalAdministrativo principal) {
        sesiones.cerrar(principal.token());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PutMapping(value = "/sesion/clave", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GestionAdministradoresController.ResultadoRespuesta> cambiarClave(
            @AuthenticationPrincipal PrincipalAdministrativo principal, HttpServletRequest solicitud) throws IOException {
        var datos = lector.leer(solicitud, Set.of("claveActual", "claveNueva"), Set.of());
        char[] actual = null, nueva = null;
        try {
            actual = LectorSolicitudAdministrativa.texto(datos, "claveActual").toCharArray();
            nueva = LectorSolicitudAdministrativa.texto(datos, "claveNueva").toCharArray();
            var resultado = gestion.cambiarClave(GestionAdministradoresController.actor(principal),
                actual, nueva, solicitud.getRemoteAddr());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(GestionAdministradoresController.ResultadoRespuesta.de(resultado));
        } finally {
            if (actual != null) Arrays.fill(actual, '\0');
            if (nueva != null) Arrays.fill(nueva, '\0');
        }
    }

    public record InicioRespuesta(String tipoToken, String token, DatosSesion sesion) {
        @Override public String toString() { return "InicioRespuesta[token oculto]"; }
    }
    public record DatosSesion(String idSesion, String usuario, String nombre, String rol,
            Set<String> permisos, boolean cambioClaveRequerido, Instant creadaEn,
            Instant ultimaActividad, Instant expiraEn, Instant inactivaHasta) {
        static DatosSesion de(SesionAdministrativa s) {
            return new DatosSesion(s.id(), s.cuenta().usuario().valor(), s.cuenta().nombre(), "ADMIN",
                s.permisos(), s.cuenta().cambioClaveRequerido(), s.creadaEn(), s.ultimaActividad(),
                s.expiraEn(), s.inactivaHasta());
        }
    }

}
