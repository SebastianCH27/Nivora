package pe.nivora.backend.acceso.infraestructura.empresaagua;

import java.io.IOException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

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

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.Suministro;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministros;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministrosNoDisponibleException;

/** Traduce el contrato HTTP de la Empresa de Agua al contrato del dominio. */
public final class FuenteSuministrosHttp implements FuenteSuministros {

    private static final String RUTA = "/api/v1/suministros/";
    private static final int MAXIMO_RESPUESTA_BYTES = 64 * 1024;

    private final RestClient cliente;
    private final ObjectReader lectorJson;

    public FuenteSuministrosHttp(String urlBase, int conexionMs, int lecturaMs,
                                JsonMapper json) {
        if (conexionMs <= 0 || lecturaMs <= 0) {
            throw new IllegalArgumentException("Los tiempos HTTP deben ser positivos.");
        }

        lectorJson = Objects.requireNonNull(json).reader()
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

        cliente = RestClient.builder()
            .baseUrl(validarUrlBase(urlBase))
            .requestFactory(conexiones)
            .build();
    }

    @Override
    public Optional<Suministro> consultar(CodigoSuministro codigo) {
        Objects.requireNonNull(codigo);

        try {
            return cliente.get()
                .uri(RUTA + "{codigo}", codigo.valor())
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
                .exchange((solicitud, respuesta) -> interpretar(respuesta, codigo));
        } catch (RestClientException ex) {
            throw new FuenteSuministrosNoDisponibleException(
                "No se pudo completar la consulta a la Empresa de Agua.", ex);
        }
    }

    private Optional<Suministro> interpretar(ClientHttpResponse respuesta,
                                             CodigoSuministro codigo) throws IOException {
        int estado = respuesta.getStatusCode().value();

        if (estado == 200) {
            JsonNode datos = leerJson(respuesta, MediaType.APPLICATION_JSON);
            String codigoRecibido = texto(datos, "codigoSuministro");
            String titular = texto(datos, "titular");

            if (!codigo.valor().equals(codigoRecibido) || titular == null || titular.isBlank()) {
                throw respuestaNoUtilizable();
            }

            return Optional.of(new Suministro(codigo, titular));
        }

        if (estado == 404) {
            JsonNode problema = leerJson(respuesta, MediaType.APPLICATION_PROBLEM_JSON);
            JsonNode estadoProblema = problema.get("status");
            if (estadoProblema != null && estadoProblema.isIntegralNumber()
                    && BigInteger.valueOf(404).equals(estadoProblema.bigIntegerValue())
                    && "Suministro no encontrado".equals(texto(problema, "title"))
                    && (RUTA + codigo.valor()).equals(texto(problema, "instance"))) {
                return Optional.empty();
            }
            throw respuestaNoUtilizable();
        }

        throw new FuenteSuministrosNoDisponibleException(
            "La Empresa de Agua respondio con un estado no utilizable: " + estado + ".");
    }

    private JsonNode leerJson(ClientHttpResponse respuesta, MediaType esperado)
            throws IOException {
        MediaType recibido;
        try {
            recibido = respuesta.getHeaders().getContentType();
        } catch (IllegalArgumentException ex) {
            throw respuestaNoUtilizable();
        }
        if (recibido == null || !esperado.getType().equals(recibido.getType())
                || !esperado.getSubtype().equals(recibido.getSubtype())) {
            throw respuestaNoUtilizable();
        }

        byte[] cuerpo;
        try (var flujo = respuesta.getBody()) {
            cuerpo = flujo.readNBytes(MAXIMO_RESPUESTA_BYTES + 1);
        }
        if (cuerpo.length == 0 || cuerpo.length > MAXIMO_RESPUESTA_BYTES) {
            throw respuestaNoUtilizable();
        }

        try {
            JsonNode datos = lectorJson.readTree(cuerpo);
            if (datos == null || !datos.isObject()) {
                throw respuestaNoUtilizable();
            }
            return datos;
        } catch (JacksonException ex) {
            // No incorporar el cuerpo externo ni sus datos personales al mensaje.
            throw respuestaNoUtilizable();
        }
    }

    private static String texto(JsonNode datos, String campo) {
        JsonNode valor = datos.get(campo);
        return valor != null && valor.isString() ? valor.stringValue() : null;
    }

    private static FuenteSuministrosNoDisponibleException respuestaNoUtilizable() {
        return new FuenteSuministrosNoDisponibleException(
            "La Empresa de Agua devolvio una respuesta no utilizable.");
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
}
