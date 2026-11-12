package pe.nivora.backend.tarifas.infraestructura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import tools.jackson.databind.JsonNode;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.compartido.infraestructura.empresaagua.ClienteEmpresaAgua;
import pe.nivora.backend.tarifas.dominio.FuenteTarifas;
import pe.nivora.backend.tarifas.dominio.HistorialTarifario;
import pe.nivora.backend.tarifas.dominio.VersionTarifaria;

public class FuenteTarifasHttp implements FuenteTarifas {
    static final String FUENTE = "empresa-agua";
    private final ClienteEmpresaAgua cliente;

    public FuenteTarifasHttp(ClienteEmpresaAgua cliente) {
        this.cliente = cliente;
    }

    @Override
    public Optional<HistorialTarifario> consultar(CodigoSuministro suministro) {
        String ruta = "/api/v1/suministros/" + suministro.valor() + "/tarifas";
        try {
            var respuesta = cliente.consultar(ruta, Map.of(), 256 * 1024);
            var cuerpo = respuesta.cuerpo();
            if (respuesta.estado() == 404 && cuerpo.path("status").isIntegralNumber()
                    && cuerpo.path("status").canConvertToInt() && cuerpo.path("status").intValue() == 404
                    && "Suministro no encontrado".equals(cuerpo.path("title").stringValue())
                    && ruta.equals(cuerpo.path("instance").stringValue())) {
                return Optional.empty();
            }
            if (respuesta.estado() != 200) throw noDisponible();
            return Optional.of(interpretar(cuerpo, suministro));
        } catch (ClienteEmpresaAgua.ConsultaFallidaException | IllegalArgumentException
                | DateTimeParseException | ArithmeticException error) {
            throw noDisponible();
        }
    }

    private static HistorialTarifario interpretar(JsonNode cuerpo, CodigoSuministro suministro) {
        if (!FUENTE.equals(texto(cuerpo, "fuente"))
                || !suministro.valor().equals(texto(cuerpo, "codigoSuministro"))
                || !"PEN".equals(texto(cuerpo, "moneda"))
                || !"America/Lima".equals(texto(cuerpo, "zonaFechas"))) {
            throw noDisponible();
        }
        var filas = cuerpo.path("asignaciones");
        if (!filas.isArray()) throw noDisponible();
        var asignaciones = new ArrayList<HistorialTarifario.Asignacion>();
        for (JsonNode fila : filas) {
            var tramos = new ArrayList<VersionTarifaria.Tramo>();
            var detalle = fila.path("tramos");
            if (!detalle.isArray()) throw noDisponible();
            for (JsonNode tramo : detalle) {
                tramos.add(new VersionTarifaria.Tramo(decimal(tramo, "desdeM3", false),
                    decimal(tramo, "hastaM3", true), decimal(tramo, "precioPorM3", false),
                    decimalOpcional(tramo, "precioAlcantarilladoPorM3")));
            }
            var version = fila.path("versionTarifa");
            if (!version.isIntegralNumber() || !version.canConvertToInt()) throw noDisponible();
            var tarifa = new VersionTarifaria(texto(fila, "codigoTarifa"), version.intValue(),
                texto(fila, "nombreTarifa"), fecha(fila, "vigenteDesde", false),
                decimal(fila, "cargoFijo", false), decimalOpcional(fila, "igvPorcentaje"), tramos);
            var asignacion = new HistorialTarifario.Asignacion(tarifa, fecha(fila, "vigenteHasta", true),
                fecha(fila, "asignadaDesde", false), fecha(fila, "asignadaHasta", true));
            if (!asignacion.aplicableDesde().equals(fecha(fila, "aplicableDesde", false))
                    || !Objects.equals(asignacion.aplicableHasta(), fecha(fila, "aplicableHasta", true))) {
                throw noDisponible();
            }
            asignaciones.add(asignacion);
        }
        return new HistorialTarifario(FUENTE, suministro, asignaciones);
    }

    private static String texto(JsonNode objeto, String campo) {
        var valor = objeto.path(campo);
        if (!valor.isString()) throw noDisponible();
        return valor.stringValue();
    }

    private static LocalDate fecha(JsonNode objeto, String campo, boolean nullable) {
        if (nullable && objeto.path(campo).isNull()) return null;
        return LocalDate.parse(texto(objeto, campo));
    }

    private static BigDecimal decimal(JsonNode objeto, String campo, boolean nullable) {
        var valor = objeto.path(campo);
        if (nullable && valor.isNull()) return null;
        if (!valor.isNumber()) throw noDisponible();
        return valor.decimalValue();
    }

    private static BigDecimal decimalOpcional(JsonNode objeto, String campo) {
        return objeto.has(campo) ? decimal(objeto, campo, true) : null;
    }

    private static IllegalStateException noDisponible() {
        return new IllegalStateException("La Empresa de Agua no devolvio un historial tarifario utilizable.");
    }
}
