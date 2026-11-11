package pe.nivora.backend.acceso.presentacion;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.nivora.backend.acceso.aplicacion.SesionesConsulta;
import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.Dni;
import pe.nivora.backend.acceso.dominio.consulta.SesionConsulta;
import pe.nivora.backend.acceso.infraestructura.seguridad.PrincipalConsulta;

@RestController
@RequestMapping("/api/v1/acceso/consulta")
public class SesionesConsultaController {

    private final SesionesConsulta sesiones;
    private final LectorSolicitudAdministrativa lector;

    public SesionesConsultaController(SesionesConsulta sesiones, LectorSolicitudAdministrativa lector) {
        this.sesiones = sesiones;
        this.lector = lector;
    }

    @PostMapping(value = "/sesiones", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InicioRespuesta> iniciar(HttpServletRequest solicitud) throws IOException {
        var datos = lector.leer(solicitud, Set.of("codigoSuministro", "dni"), Set.of());
        var codigo = new CodigoSuministro(LectorSolicitudAdministrativa.texto(datos, "codigoSuministro"));
        var dni = new Dni(LectorSolicitudAdministrativa.texto(datos, "dni"));
        var inicio = sesiones.iniciar(codigo, dni, solicitud.getRemoteAddr())
            .orElseThrow(AccesoNoValido::new);
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
            .body(new InicioRespuesta("Bearer", inicio.token().valor(), DatosSesion.de(inicio.sesion())));
    }

    @GetMapping(value = "/sesion", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DatosSesion> actual(@AuthenticationPrincipal PrincipalConsulta principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(DatosSesion.de(principal.sesion()));
    }

    @DeleteMapping("/sesion")
    public ResponseEntity<Void> cerrar(@AuthenticationPrincipal PrincipalConsulta principal) {
        sesiones.cerrar(principal.token());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    public record InicioRespuesta(String tipoToken, String token, DatosSesion sesion) {

        @Override
        public String toString() {
            return "InicioRespuesta[token oculto]";
        }
    }

    public record DatosSesion(String idSesion, String codigoSuministro, String nombre,
            String rol, Instant creadaEn, Instant expiraEn) {

        static DatosSesion de(SesionConsulta sesion) {
            return new DatosSesion(sesion.id(), sesion.codigoSuministro().valor(), sesion.nombre(),
                "CONSULTA", sesion.creadaEn(), sesion.expiraEn());
        }

        @Override
        public String toString() {
            return "DatosSesion[datos ocultos]";
        }
    }

    static final class AccesoNoValido extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
