package pe.nivora.backend.compartido.infraestructura.empresaagua;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

/** Transporte compartido; no interpreta resultados de negocio. */
public final class ClienteEmpresaAgua {

    private final RestClient cliente;
    private final ObjectReader lector;

    public ClienteEmpresaAgua(String urlBase, int conexionMs, int lecturaMs, JsonMapper json) {
        if (conexionMs <= 0 || lecturaMs <= 0) {
            throw new IllegalArgumentException("Los tiempos HTTP deben ser positivos.");
        }
        lector = Objects.requireNonNull(json).reader()
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        var conexiones = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection conexion, String metodo)
                    throws IOException {
                super.prepareConnection(conexion, metodo);
                conexion.setInstanceFollowRedirects(false);
            }
        };
        conexiones.setConnectTimeout(conexionMs);
        conexiones.setReadTimeout(lecturaMs);
        cliente = RestClient.builder().baseUrl(validarUrlBase(urlBase))
            .requestFactory(conexiones).build();
    }

    public Respuesta consultar(String ruta, Map<String, ?> parametros, int maximoBytes) {
        if (maximoBytes < 1 || maximoBytes == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("El limite de respuesta debe ser positivo y acotado.");
        }
        try {
            return cliente.get().uri(uri -> {
                uri.path(ruta);
                parametros.forEach((nombre, valor) -> uri.queryParam(nombre, valor));
                return uri.build();
            }).accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
                .exchange((solicitud, respuesta) -> {
                    int estado = respuesta.getStatusCode().value();
                    if (estado == 200 || estado == 404) {
                        MediaType tipo = estado == 200 ? MediaType.APPLICATION_JSON
                            : MediaType.APPLICATION_PROBLEM_JSON;
                        return new Respuesta(estado, leerJson(respuesta, tipo, maximoBytes));
                    }
                    respuesta.getBody().close();
                    return new Respuesta(estado, null);
                });
        } catch (RestClientException ex) {
            throw new ConsultaFallidaException(
                "No se pudo completar la consulta a la Empresa de Agua.", ex);
        }
    }

    private JsonNode leerJson(ClientHttpResponse respuesta, MediaType esperado, int maximoBytes)
            throws IOException {
        MediaType recibido;
        try {
            recibido = respuesta.getHeaders().getContentType();
        } catch (IllegalArgumentException ex) {
            throw respuestaNoUtilizable();
        }
        if (recibido == null || !esperado.getType().equals(recibido.getType())
                || !esperado.getSubtype().equals(recibido.getSubtype())) {
            respuesta.getBody().close();
            throw respuestaNoUtilizable();
        }
        byte[] cuerpo;
        try (var flujo = respuesta.getBody()) {
            cuerpo = flujo.readNBytes(maximoBytes + 1);
        }
        if (cuerpo.length == 0 || cuerpo.length > maximoBytes) {
            throw respuestaNoUtilizable();
        }
        try {
            JsonNode datos = lector.readTree(cuerpo);
            if (datos == null || !datos.isObject()) {
                throw respuestaNoUtilizable();
            }
            return datos;
        } catch (JacksonException ex) {
            // El mensaje no conserva el cuerpo externo ni datos personales.
            throw respuestaNoUtilizable();
        }
    }

    private static ConsultaFallidaException respuestaNoUtilizable() {
        return new ConsultaFallidaException("La Empresa de Agua devolvio una respuesta no utilizable.");
    }

    private static String validarUrlBase(String valor) {
        URI uri = URI.create(Objects.requireNonNull(valor));
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))) {
            throw new IllegalArgumentException(
                "La URL de la Empresa de Agua debe contener solo protocolo, host y puerto.");
        }
        return valor.endsWith("/") ? valor.substring(0, valor.length() - 1) : valor;
    }

    public record Respuesta(int estado, JsonNode cuerpo) {}

    public static final class ConsultaFallidaException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private ConsultaFallidaException(String mensaje) {
            super(mensaje);
        }

        private ConsultaFallidaException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
