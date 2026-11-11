package pe.nivora.backend.acceso.presentacion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;
import pe.nivora.backend.acceso.aplicacion.SesionesAdministrativas;
import pe.nivora.backend.acceso.dominio.administradores.SesionAdministrativa;
import pe.nivora.backend.acceso.infraestructura.seguridad.PrincipalAdministrativo;

@RestController
@RequestMapping("/api/v1/acceso/administradores")
public class SesionesAdministrativasController {
    private final SesionesAdministrativas sesiones;
    private final ObjectReader lector;

    public SesionesAdministrativasController(SesionesAdministrativas sesiones, JsonMapper json) {
        this.sesiones = sesiones;
        lector = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
            DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    }

    @PostMapping(value = "/sesiones", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InicioRespuesta> iniciar(HttpServletRequest solicitud) throws IOException {
        MediaType tipo;
        try { tipo = MediaType.parseMediaType(solicitud.getContentType() == null ? "" : solicitud.getContentType()); }
        catch (IllegalArgumentException error) { throw new FormatoAccesoException(415); }
        if (!"application".equals(tipo.getType()) || !"json".equals(tipo.getSubtype())
                || (tipo.getCharset() != null && !StandardCharsets.UTF_8.equals(tipo.getCharset()))) {
            throw new FormatoAccesoException(415);
        }
        byte[] cuerpo = solicitud.getInputStream().readNBytes(4097);
        char[] clave = null;
        try {
            if (cuerpo.length > 4096) throw new FormatoAccesoException(413);
            var datos = lector.readTree(cuerpo);
            if (datos == null || !datos.isObject() || datos.size() != 2
                    || !datos.has("usuario") || !datos.get("usuario").isString()
                    || !datos.has("clave") || !datos.get("clave").isString()) {
                throw new FormatoAccesoException(400);
            }
            clave = datos.get("clave").stringValue().toCharArray();
            var inicio = sesiones.iniciar(datos.get("usuario").stringValue(), clave, solicitud.getRemoteAddr());
            return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(new InicioRespuesta("Bearer", inicio.token().valor(), DatosSesion.de(inicio.sesion())));
        } catch (JacksonException error) { throw new FormatoAccesoException(400); }
        finally {
            Arrays.fill(cuerpo, (byte) 0);
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

    public static final class FormatoAccesoException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int estado;
        FormatoAccesoException(int estado) { super("Formato de acceso invalido."); this.estado = estado; }
        public int estado() { return estado; }
    }
}
