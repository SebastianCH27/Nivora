package pe.nivora.backend.acceso.presentacion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class LectorSolicitudAdministrativa {
    private final ObjectReader lector;

    public LectorSolicitudAdministrativa(JsonMapper json) {
        lector = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
            DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    public JsonNode leer(HttpServletRequest solicitud, Set<String> requeridos,
            Set<String> opcionales) throws IOException {
        MediaType tipo;
        try { tipo = MediaType.parseMediaType(solicitud.getContentType() == null ? "" : solicitud.getContentType()); }
        catch (IllegalArgumentException error) { throw new FormatoSolicitudException(415); }
        if (!"application".equals(tipo.getType()) || !"json".equals(tipo.getSubtype())
                || (tipo.getCharset() != null && !StandardCharsets.UTF_8.equals(tipo.getCharset()))) {
            throw new FormatoSolicitudException(415);
        }
        byte[] cuerpo = solicitud.getInputStream().readNBytes(4097);
        try {
            if (cuerpo.length > 4096) throw new FormatoSolicitudException(413);
            var datos = lector.readTree(cuerpo);
            validarCampos(datos, requeridos, opcionales);
            return datos;
        } catch (JacksonException error) { throw new FormatoSolicitudException(400); }
        finally { Arrays.fill(cuerpo, (byte) 0); }
    }

    public static void validarCampos(JsonNode datos, Set<String> requeridos, Set<String> opcionales) {
        if (datos == null || !datos.isObject()) throw new FormatoSolicitudException(400);
        for (String campo : requeridos) if (!datos.has(campo)) throw new FormatoSolicitudException(400);
        long presentes = opcionales.stream().filter(datos::has).count();
        if (datos.size() != requeridos.size() + presentes) throw new FormatoSolicitudException(400);
    }

    public static String texto(JsonNode datos, String campo) {
        if (!datos.has(campo) || !datos.get(campo).isString()) throw new FormatoSolicitudException(400);
        return datos.get(campo).stringValue();
    }

    public static boolean booleano(JsonNode datos, String campo) {
        if (!datos.has(campo) || !datos.get(campo).isBoolean()) throw new FormatoSolicitudException(400);
        return datos.get(campo).booleanValue();
    }

    public static final class FormatoSolicitudException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int estado;
        FormatoSolicitudException(int estado) { super("Formato de solicitud invalido."); this.estado = estado; }
        public int estado() { return estado; }
    }
}
