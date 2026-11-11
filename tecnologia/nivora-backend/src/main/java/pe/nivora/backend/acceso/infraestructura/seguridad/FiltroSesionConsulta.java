package pe.nivora.backend.acceso.infraestructura.seguridad;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import pe.nivora.backend.acceso.aplicacion.SesionesConsulta;
import pe.nivora.backend.acceso.dominio.consulta.TokenConsulta;
import pe.nivora.backend.acceso.dominio.contratos.SesionesConsultaNoDisponiblesException;

public final class FiltroSesionConsulta extends OncePerRequestFilter {

    private final SesionesConsulta sesiones;
    private final RespuestaSeguridad errores;

    public FiltroSesionConsulta(SesionesConsulta sesiones, RespuestaSeguridad errores) {
        this.sesiones = sesiones;
        this.errores = errores;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest solicitud) {
        return "POST".equals(solicitud.getMethod())
            && "/api/v1/acceso/consulta/sesiones".equals(solicitud.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta,
            FilterChain cadena) throws IOException, ServletException {
        var encabezados = Collections.list(solicitud.getHeaders("Authorization"));
        if (encabezados.isEmpty()) {
            cadena.doFilter(solicitud, respuesta);
            return;
        }
        try {
            String encabezado = encabezados.getFirst();
            if (encabezados.size() != 1 || !encabezado.regionMatches(true, 0, "Bearer ", 0, 7)) {
                sesionNoValida(solicitud, respuesta);
                return;
            }
            var token = new TokenConsulta(encabezado.substring(7));
            var encontrada = sesiones.autenticar(token);
            if (encontrada.isEmpty()) {
                sesionNoValida(solicitud, respuesta);
                return;
            }
            var contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new PrincipalConsulta(token, encontrada.get()), null,
                List.of(new SimpleGrantedAuthority("ROLE_CONSULTA"))));
            SecurityContextHolder.setContext(contexto);
        } catch (IllegalArgumentException error) {
            sesionNoValida(solicitud, respuesta);
            return;
        } catch (SesionesConsultaNoDisponiblesException error) {
            errores.escribir(solicitud, respuesta, 503, "Acceso no disponible",
                "No se pudo comprobar la sesion de consulta.");
            return;
        }
        cadena.doFilter(solicitud, respuesta);
    }

    private void sesionNoValida(HttpServletRequest solicitud, HttpServletResponse respuesta)
            throws IOException {
        errores.escribir(solicitud, respuesta, 401, "Sesion no valida",
            "Inicia nuevamente la sesion de consulta.");
    }
}
