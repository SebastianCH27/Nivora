package pe.nivora.backend.lecturas.infraestructura.empresaagua;

import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import tools.jackson.databind.JsonNode;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.compartido.infraestructura.empresaagua.ClienteEmpresaAgua;
import pe.nivora.backend.lecturas.dominio.LecturaAcumulada;
import pe.nivora.backend.lecturas.dominio.LoteLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturasNoDisponibleException;

public final class FuenteLecturasHttp implements FuenteLecturas {

    private static final String FUENTE = "empresa-agua";
    private static final int MAXIMO_RESPUESTA_BYTES = 512 * 1024;
    private final ClienteEmpresaAgua cliente;

    public FuenteLecturasHttp(ClienteEmpresaAgua cliente) {
        this.cliente = Objects.requireNonNull(cliente);
    }

    @Override
    public Optional<LoteLecturas> consultar(CodigoSuministro codigo, String cursor, int limite) {
        return consultar(codigo, cursor, limite, null);
    }

    public Optional<LoteLecturas> consultar(CodigoSuministro codigo, String cursor,
            int limite, String hastaId) {
        Objects.requireNonNull(codigo);
        long desde = identificador(cursor, true);
        Long hasta = hastaId == null ? null : identificador(hastaId, true);
        if (hasta != null && desde > hasta) {
            throw new IllegalArgumentException("El cursor supera el corte solicitado.");
        }
        if (limite < 1 || limite > 500) {
            throw new IllegalArgumentException("El limite debe estar entre 1 y 500.");
        }

        String ruta = "/api/v1/suministros/" + codigo.valor() + "/lecturas";
        ClienteEmpresaAgua.Respuesta respuesta;
        var parametros = new HashMap<String, Object>(Map.of("despuesDe", cursor, "limite", limite));
        if (hastaId != null) parametros.put("hastaId", hastaId);
        try {
            respuesta = cliente.consultar(ruta, parametros,
                MAXIMO_RESPUESTA_BYTES);
        } catch (ClienteEmpresaAgua.ConsultaFallidaException ex) {
            throw new FuenteLecturasNoDisponibleException(
                "No se pudo completar la consulta de lecturas a la Empresa de Agua.", ex);
        }

        if (respuesta.estado() == 200) {
            try {
                return Optional.of(interpretar(respuesta.cuerpo(), codigo, desde, limite, hasta));
            } catch (IllegalArgumentException | ArithmeticException | DateTimeException ex) {
                // No adjuntar valores ni el cuerpo del proveedor a la excepcion de dominio.
                throw respuestaNoUtilizable();
            }
        }
        if (respuesta.estado() == 404 && esAusenciaConfirmada(respuesta.cuerpo(), ruta)) {
            return Optional.empty();
        }
        throw new FuenteLecturasNoDisponibleException(
            "La consulta de lecturas recibio un estado no utilizable: " + respuesta.estado() + ".");
    }

    private LoteLecturas interpretar(JsonNode datos, CodigoSuministro codigo,
                                    long desde, int limite, Long hasta) {
        exigir(FUENTE.equals(texto(datos, "fuente"))
            && codigo.valor().equals(texto(datos, "codigoSuministro"))
            && "ACUMULADA".equals(texto(datos, "tipoMedicion"))
            && "L".equals(texto(datos, "unidad")));

        JsonNode filas = datos.get("lecturas");
        JsonNode mas = datos.get("hayMas");
        exigir(filas != null && filas.isArray() && filas.size() <= limite
            && mas != null && mas.isBoolean());
        boolean hayMas = mas.booleanValue();
        exigir(!hayMas || filas.size() == limite);

        var lecturas = new ArrayList<LecturaAcumulada>();
        long ultimo = desde;
        for (JsonNode fila : filas) {
            exigir(fila.isObject());
            String id = texto(fila, "idLectura");
            long numero = identificador(id, false);
            exigir(numero > ultimo && (hasta == null || numero <= hasta));
            String medidor = texto(fila, "idMedidor");
            identificador(medidor, false);
            String fecha = texto(fila, "fechaMedicion");
            exigir(fecha != null && fecha.endsWith("Z"));
            JsonNode valor = fila.get("valorLitros");
            exigir(valor != null && valor.isIntegralNumber());
            long litros = valor.bigIntegerValue().longValueExact();

            lecturas.add(new LecturaAcumulada(FUENTE, id, codigo, medidor,
                Instant.parse(fecha), litros));
            ultimo = numero;
        }

        String siguiente = texto(datos, "siguienteDespuesDe");
        exigir(identificador(siguiente, true) == ultimo);
        exigir(hasta == null || !hayMas || ultimo < hasta);
        return new LoteLecturas(FUENTE, codigo, lecturas, siguiente, hayMas);
    }

    private static long identificador(String valor, boolean admiteCero) {
        String patron = admiteCero ? "(?:0|[1-9][0-9]{0,18})" : "[1-9][0-9]{0,18}";
        if (valor == null || !valor.matches(patron)) {
            throw new IllegalArgumentException("El cursor o identificador debe ser un entero decimal canonico valido.");
        }
        return Long.parseLong(valor);
    }

    private static boolean esAusenciaConfirmada(JsonNode datos, String ruta) {
        JsonNode estado = datos.get("status");
        return estado != null && estado.isIntegralNumber()
            && BigInteger.valueOf(404).equals(estado.bigIntegerValue())
            && "Suministro no encontrado".equals(texto(datos, "title"))
            && ruta.equals(texto(datos, "instance"));
    }

    private static String texto(JsonNode datos, String campo) {
        JsonNode valor = datos.get(campo);
        return valor != null && valor.isString() ? valor.stringValue() : null;
    }

    private static void exigir(boolean condicion) {
        if (!condicion) {
            throw new IllegalArgumentException("Datos externos incompatibles con el contrato.");
        }
    }

    private static FuenteLecturasNoDisponibleException respuestaNoUtilizable() {
        return new FuenteLecturasNoDisponibleException(
            "La Empresa de Agua devolvio una pagina de lecturas no utilizable.");
    }
}
