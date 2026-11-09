package pe.nivora.backend.acceso.infraestructura.empresaagua;

import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.Suministro;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministros;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministrosNoDisponibleException;
import pe.nivora.backend.compartido.infraestructura.empresaagua.ClienteEmpresaAgua;

/** Traduce el contrato HTTP de la Empresa de Agua al contrato del dominio. */
public final class FuenteSuministrosHttp implements FuenteSuministros {

    private static final String RUTA = "/api/v1/suministros/";
    private static final int MAXIMO_RESPUESTA_BYTES = 64 * 1024;
    private final ClienteEmpresaAgua cliente;

    public FuenteSuministrosHttp(ClienteEmpresaAgua cliente) {
        this.cliente = Objects.requireNonNull(cliente);
    }

    public FuenteSuministrosHttp(String urlBase, int conexionMs, int lecturaMs, JsonMapper json) {
        this(new ClienteEmpresaAgua(urlBase, conexionMs, lecturaMs, json));
    }

    @Override
    public Optional<Suministro> consultar(CodigoSuministro codigo) {
        Objects.requireNonNull(codigo);
        ClienteEmpresaAgua.Respuesta respuesta;
        try {
            respuesta = cliente.consultar(RUTA + codigo.valor(), Map.of(), MAXIMO_RESPUESTA_BYTES);
        } catch (ClienteEmpresaAgua.ConsultaFallidaException ex) {
            throw new FuenteSuministrosNoDisponibleException(
                "No se pudo completar la consulta a la Empresa de Agua.", ex);
        }
        JsonNode datos = respuesta.cuerpo();
        if (respuesta.estado() == 200) {
            String codigoRecibido = texto(datos, "codigoSuministro");
            String titular = texto(datos, "titular");
            if (!codigo.valor().equals(codigoRecibido) || titular == null || titular.isBlank()) {
                throw respuestaNoUtilizable();
            }
            return Optional.of(new Suministro(codigo, titular));
        }
        if (respuesta.estado() == 404) {
            JsonNode estado = datos.get("status");
            if (estado != null && estado.isIntegralNumber()
                    && BigInteger.valueOf(404).equals(estado.bigIntegerValue())
                    && "Suministro no encontrado".equals(texto(datos, "title"))
                    && (RUTA + codigo.valor()).equals(texto(datos, "instance"))) {
                return Optional.empty();
            }
            throw respuestaNoUtilizable();
        }
        throw new FuenteSuministrosNoDisponibleException(
            "La Empresa de Agua respondio con un estado no utilizable: " + respuesta.estado() + ".");
    }

    private static String texto(JsonNode datos, String campo) {
        JsonNode valor = datos.get(campo);
        return valor != null && valor.isString() ? valor.stringValue() : null;
    }

    private static FuenteSuministrosNoDisponibleException respuestaNoUtilizable() {
        return new FuenteSuministrosNoDisponibleException(
            "La Empresa de Agua devolvio una respuesta no utilizable.");
    }
}
