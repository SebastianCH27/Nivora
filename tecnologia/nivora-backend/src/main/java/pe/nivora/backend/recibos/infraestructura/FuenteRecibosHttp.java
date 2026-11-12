package pe.nivora.backend.recibos.infraestructura;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.compartido.infraestructura.empresaagua.ClienteEmpresaAgua;
import pe.nivora.backend.lecturas.dominio.MesConsumo;
import pe.nivora.backend.recibos.dominio.FuenteRecibos;
import pe.nivora.backend.recibos.dominio.FuenteRecibosNoDisponibleException;
import pe.nivora.backend.recibos.dominio.ReciboEmitido;
import pe.nivora.backend.recibos.dominio.RecibosEmitidos;

@Component
public class FuenteRecibosHttp implements FuenteRecibos {
    private static final int MAXIMO_RESPUESTA_BYTES = 64 * 1024;
    private final ClienteEmpresaAgua cliente;
    private final Clock reloj;

    public FuenteRecibosHttp(ClienteEmpresaAgua cliente, Clock reloj) {
        this.cliente = cliente;
        this.reloj = reloj;
    }

    @Override
    public Optional<RecibosEmitidos> consultar(CodigoSuministro suministro, int limite) {
        if (limite < 1 || limite > 60) {
            throw new IllegalArgumentException("El limite de recibos debe estar entre 1 y 60.");
        }
        var ruta = "/api/v1/suministros/" + suministro.valor() + "/recibos";
        try {
            var respuesta = cliente.consultar(ruta, Map.of("limite", limite), MAXIMO_RESPUESTA_BYTES);
            if (respuesta.estado() == 404 && suministroAusente(respuesta.cuerpo(), ruta)) {
                return Optional.empty();
            }
            if (respuesta.estado() != 200) {
                throw new FuenteRecibosNoDisponibleException();
            }
            return Optional.of(interpretar(respuesta.cuerpo(), suministro, limite,
                LocalDate.ofInstant(reloj.instant(), MesConsumo.ZONA)));
        } catch (ClienteEmpresaAgua.ConsultaFallidaException | IllegalArgumentException
                | DateTimeParseException | ArithmeticException error) {
            throw new FuenteRecibosNoDisponibleException();
        }
    }

    private static boolean suministroAusente(JsonNode cuerpo, String ruta) {
        var estado = cuerpo.path("status");
        return estado.isIntegralNumber() && estado.canConvertToInt() && estado.intValue() == 404
            && "Suministro no encontrado".equals(cuerpo.path("title").stringValue())
            && ruta.equals(cuerpo.path("instance").stringValue());
    }

    private static RecibosEmitidos interpretar(JsonNode cuerpo, CodigoSuministro suministro,
            int limite, LocalDate hoy) {
        var filas = cuerpo.path("recibos");
        if (!suministro.valor().equals(cuerpo.path("codigoSuministro").stringValue())
                || !"PEN".equals(cuerpo.path("moneda").stringValue()) || !filas.isArray() || filas.size() > limite) {
            throw new FuenteRecibosNoDisponibleException();
        }
        var recibos = new ArrayList<ReciboEmitido>();
        for (var fila : filas) {
            var periodo = LocalDate.parse(texto(fila, "periodo"));
            var disponible = LocalDate.parse(texto(fila, "fechaDisponibilidad"));
            var version = fila.path("versionTarifa");
            if (periodo.getDayOfMonth() != 1 || disponible.isAfter(hoy)
                    || !version.isIntegralNumber() || !version.canConvertToInt()) {
                throw new FuenteRecibosNoDisponibleException();
            }
            recibos.add(new ReciboEmitido(texto(fila, "numeroRecibo"), YearMonth.from(periodo),
                decimal(fila, "consumoLitros"), decimal(fila, "cargoFijo"), decimal(fila, "importeConsumo"),
                decimal(fila, "importeTotal"), disponible, LocalDate.parse(texto(fila, "fechaVencimiento")),
                texto(fila, "codigoTarifa"), version.intValue()));
        }
        return new RecibosEmitidos(suministro, recibos);
    }

    private static String texto(JsonNode fila, String campo) {
        var valor = fila.path(campo);
        if (!valor.isString()) throw new FuenteRecibosNoDisponibleException();
        return valor.stringValue();
    }

    private static BigDecimal decimal(JsonNode fila, String campo) {
        var valor = fila.path(campo);
        if (!valor.isNumber()) throw new FuenteRecibosNoDisponibleException();
        return valor.decimalValue();
    }
}
