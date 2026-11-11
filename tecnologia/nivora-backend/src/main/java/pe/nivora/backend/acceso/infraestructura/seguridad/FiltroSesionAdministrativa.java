package pe.nivora.backend.acceso.infraestructura.seguridad;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.nivora.backend.acceso.aplicacion.SesionesAdministrativas;
import pe.nivora.backend.acceso.dominio.administradores.TokenAdministrativo;
import pe.nivora.backend.acceso.dominio.contratos.SesionesAdministrativasNoDisponiblesException;

public final class FiltroSesionAdministrativa extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(FiltroSesionAdministrativa.class);
    private final SesionesAdministrativas sesiones;
    private final RespuestaSeguridad errores;
    public FiltroSesionAdministrativa(SesionesAdministrativas sesiones, RespuestaSeguridad errores) {
        this.sesiones = sesiones;
        this.errores = errores;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest solicitud) {
        return ("POST".equals(solicitud.getMethod())
                && "/api/v1/acceso/administradores/sesiones".equals(solicitud.getServletPath()))
            || solicitud.getServletPath().equals("/actuator/health")
            || solicitud.getServletPath().startsWith("/actuator/health/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta,
            FilterChain cadena) throws IOException, ServletException {
        var encabezados = Collections.list(solicitud.getHeaders("Authorization"));
        if (encabezados.isEmpty()) { cadena.doFilter(solicitud, respuesta); return; }
        try {
            String encabezado = encabezados.getFirst();
            if (encabezados.size() != 1 || !encabezado.regionMatches(true, 0, "Bearer ", 0, 7)) {
                throw new IllegalArgumentException("Encabezado de autenticacion invalido.");
            }
            var token = TokenAdministrativo.interpretar(encabezado.substring(7));
            var encontrada = sesiones.autenticar(token);
            if (encontrada.isEmpty()) {
                errores.escribir(solicitud, respuesta, 401, "Sesion no valida", "Inicia sesion administrativa nuevamente.");
                return;
            }
            var permisos = new ArrayList<SimpleGrantedAuthority>();
            permisos.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            encontrada.get().permisos().forEach(p -> permisos.add(new SimpleGrantedAuthority(p)));
            var contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new PrincipalAdministrativo(token, encontrada.get()), null, permisos));
            SecurityContextHolder.setContext(contexto);
        } catch (IllegalArgumentException error) {
            errores.escribir(solicitud, respuesta, 401, "Sesion no valida", "Inicia sesion administrativa nuevamente.");
            return;
        } catch (SesionesAdministrativasNoDisponiblesException error) {
            LOG.warn("No se pudo comprobar una sesion administrativa guardada.");
            LOG.debug("Detalle del fallo de comprobacion de sesion", error);
            errores.escribir(solicitud, respuesta, 503, "Acceso no disponible", "No se pudo comprobar la sesion guardada.");
            return;
        }
        cadena.doFilter(solicitud, respuesta);
    }
}
