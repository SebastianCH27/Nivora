package pe.nivora.backend.acceso.infraestructura.identidad;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Objects;

import org.springframework.http.MediaType;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

import pe.nivora.backend.acceso.dominio.Dni;
import pe.nivora.backend.acceso.dominio.IdentidadConsultada;
import pe.nivora.backend.acceso.dominio.contratos.FuenteIdentidad;
import pe.nivora.backend.acceso.dominio.contratos.FuenteIdentidadNoDisponibleException;
import pe.nivora.backend.acceso.dominio.contratos.FuenteIdentidadNoDisponibleException.Motivo;

/** Traduce la respuesta del proveedor de consultas DNI/RUC. */
public final class FuenteIdentidadApisPeru implements FuenteIdentidad {

    private static final URI ORIGEN = URI.create("https://dniruc.apisperu.com");
    private static final String RUTA = "/api/v1/dni/";
    private static final int MAXIMO_RESPUESTA_BYTES = 16 * 1024;

    private final URI origen;
    private final String autorizacion;
    private final ObjectReader lector;

    public FuenteIdentidadApisPeru(String token, JsonMapper json) {
        this(ORIGEN, token, json);
    }

    // El servidor local de pruebas se proporciona por constructor.
    FuenteIdentidadApisPeru(URI origen, String token, JsonMapper json) {
        this.origen = Objects.requireNonNull(origen);
        if (token == null || !token.matches("[A-Za-z0-9._~+/\\-]+=*")) {
            throw new IllegalArgumentException("El token de APIsPERU tiene un formato incompatible.");
        }
        autorizacion = "Bearer " + token;
        lector = Objects.requireNonNull(json).reader()
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    }

    @Override
    public IdentidadConsultada consultar(Dni dni) {
        Objects.requireNonNull(dni);
        HttpURLConnection conexion = null;
        try {
            conexion = (HttpURLConnection) origen.resolve(RUTA + dni.valor()).toURL().openConnection();
            conexion.setInstanceFollowRedirects(false);
            conexion.setUseCaches(false);
            conexion.setConnectTimeout(5000);
            conexion.setReadTimeout(10000);
            conexion.setRequestProperty("Authorization", autorizacion);
            conexion.setRequestProperty("Accept", MediaType.APPLICATION_JSON_VALUE);
            conexion.setRequestProperty("Cache-Control", "no-store");

            int estado = conexion.getResponseCode();
            if (estado != 200) {
                throw new FuenteIdentidadNoDisponibleException(motivoHttp(estado));
            }
            return interpretar(dni, leerJson(conexion));
        } catch (IOException ex) {
            // La URL de la consulta y el cuerpo pueden contener datos personales.
            throw new FuenteIdentidadNoDisponibleException(Motivo.FUENTE_NO_DISPONIBLE);
        } finally {
            if (conexion != null) {
                conexion.disconnect();
            }
        }
    }

    private JsonNode leerJson(HttpURLConnection conexion) throws IOException {
        try {
            String cabecera = conexion.getContentType();
            if (cabecera == null || !MediaType.APPLICATION_JSON.equalsTypeAndSubtype(
                    MediaType.parseMediaType(cabecera))) {
                throw respuestaIncompatible();
            }
            try (var cuerpo = conexion.getInputStream()) {
                byte[] bytes = cuerpo.readNBytes(MAXIMO_RESPUESTA_BYTES + 1);
                if (bytes.length == 0 || bytes.length > MAXIMO_RESPUESTA_BYTES) {
                    throw respuestaIncompatible();
                }
                JsonNode datos = lector.readTree(bytes);
                if (datos == null || !datos.isObject()) {
                    throw respuestaIncompatible();
                }
                return datos;
            }
        } catch (JacksonException | IllegalArgumentException ex) {
            throw respuestaIncompatible();
        }
    }

    private static IdentidadConsultada interpretar(Dni dni, JsonNode datos) {
        JsonNode exito = datos.get("success");
        if (exito != null) {
            if (!exito.isBoolean()) {
                throw respuestaIncompatible();
            }
            if (!exito.booleanValue()) {
                throw new FuenteIdentidadNoDisponibleException(Motivo.RESULTADO_NO_CONCLUYENTE);
            }
        }
        String nombres = texto(datos, "nombres");
        if (!dni.valor().equals(texto(datos, "dni")) || nombres.isBlank()) {
            throw respuestaIncompatible();
        }
        String nombreCompleto = String.join(" ", nombres, texto(datos, "apellidoPaterno"),
            texto(datos, "apellidoMaterno")).replaceAll(" +", " ").strip();
        try {
            return new IdentidadConsultada(dni, nombreCompleto);
        } catch (IllegalArgumentException ex) {
            throw respuestaIncompatible();
        }
    }

    private static String texto(JsonNode datos, String campo) {
        JsonNode valor = datos.get(campo);
        if (valor == null || valor.isNull()) {
            return "";
        }
        if (!valor.isString()) {
            throw respuestaIncompatible();
        }
        return valor.stringValue().strip();
    }

    private static Motivo motivoHttp(int estado) {
        return switch (estado) {
            case 401, 403 -> Motivo.CREDENCIAL_RECHAZADA;
            case 429 -> Motivo.LIMITE_CONSULTAS;
            case 404 -> Motivo.RESULTADO_NO_CONCLUYENTE;
            default -> Motivo.FUENTE_NO_DISPONIBLE;
        };
    }

    private static FuenteIdentidadNoDisponibleException respuestaIncompatible() {
        return new FuenteIdentidadNoDisponibleException(Motivo.RESPUESTA_INCOMPATIBLE);
    }
}
