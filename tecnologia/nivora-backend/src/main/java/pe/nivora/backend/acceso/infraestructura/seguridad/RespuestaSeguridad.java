package pe.nivora.backend.acceso.infraestructura.seguridad;

import java.io.IOException;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class RespuestaSeguridad {
    private final JsonMapper json;
    public RespuestaSeguridad(JsonMapper json) { this.json = json; }
    public void escribir(HttpServletRequest solicitud, HttpServletResponse respuesta,
            int estado, String titulo, String detalle) throws IOException {
        respuesta.setStatus(estado);
        respuesta.setContentType("application/problem+json");
        respuesta.setHeader("Cache-Control", "no-store");
        if (estado == 401) respuesta.setHeader("WWW-Authenticate", "Bearer");
        json.writeValue(respuesta.getOutputStream(), Map.of("status", estado, "title", titulo,
            "detail", detalle, "instance", solicitud.getRequestURI()));
    }
}
