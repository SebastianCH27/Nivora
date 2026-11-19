package pe.nivora.backend.integracion.contratos;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Optional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.Suministro;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministros;
import pe.nivora.backend.lecturas.dominio.ContextoLecturas;
import pe.nivora.backend.lecturas.dominio.LecturaAcumulada;
import pe.nivora.backend.lecturas.dominio.LoteLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturasConCorte;
import pe.nivora.backend.lecturas.infraestructura.ContextoLecturasJson;
import pe.nivora.backend.recibos.dominio.FuenteRecibos;
import pe.nivora.backend.recibos.dominio.ReciboEmitido;
import pe.nivora.backend.recibos.dominio.RecibosEmitidos;
import pe.nivora.backend.tarifas.dominio.FuenteTarifas;
import pe.nivora.backend.tarifas.dominio.HistorialTarifario;
import pe.nivora.backend.tarifas.dominio.VersionTarifaria;

/** Adaptacion de prueba desde archivo: no utiliza el cliente ni los adaptadores HTTP. */
public final class AdaptadoresAguaArchivo {
    private final JsonNode datos;
    public AdaptadoresAguaArchivo() throws IOException {
        try (var entrada = getClass().getResourceAsStream("/contratos/empresa-agua.json")) {
            datos = new JsonMapper().readTree(entrada);
        }
    }
    private boolean conocido(CodigoSuministro codigo) {
        return datos.at("/suministro/codigoSuministro").stringValue().equals(codigo.valor());
    }
    public FuenteSuministros suministros() {
        return codigo -> conocido(codigo) ? Optional.of(new Suministro(codigo,
            datos.at("/suministro/titular").stringValue())) : Optional.empty();
    }
    public FuenteLecturasConCorte lecturas() {
        return new FuenteLecturasConCorte() {
            public Optional<ContextoLecturas> consultarContexto(CodigoSuministro codigo) {
                return conocido(codigo) ? Optional.of(ContextoLecturasJson.interpretar(
                    datos.path("corte"), datos.path("inventario"), codigo)) : Optional.empty();
            }
            public Optional<LoteLecturas> consultar(CodigoSuministro codigo, String cursor,
                    int limite, ContextoLecturas contexto) {
                if (!conocido(codigo)) return Optional.empty();
                if (!codigo.equals(contexto.suministro()) || limite < 1 || limite > 500)
                    throw new IllegalArgumentException("Contexto o limite incompatible.");
                var pendientes = new ArrayList<LecturaAcumulada>();
                for (var fila : datos.at("/pagina/lecturas")) {
                    String id = fila.path("idLectura").stringValue();
                    if (Long.parseLong(id) > Long.parseLong(cursor)
                            && Long.parseLong(id) <= Long.parseLong(contexto.hastaIdLectura()))
                        pendientes.add(new LecturaAcumulada("empresa-agua", id, codigo,
                            fila.path("idMedidor").stringValue(), Instant.parse(fila.path("fechaMedicion").stringValue()),
                            fila.path("valorLitros").longValue()));
                }
                var pagina = pendientes.stream().limit(limite).toList();
                return Optional.of(new LoteLecturas("empresa-agua", codigo, pagina,
                    pagina.isEmpty() ? cursor : pagina.getLast().idLectura(), pendientes.size() > limite));
            }
        };
    }
    public FuenteTarifas tarifas() {
        return codigo -> {
            if (!conocido(codigo)) return Optional.empty();
            var asignaciones = new ArrayList<HistorialTarifario.Asignacion>();
            for (var fila : datos.at("/tarifas/asignaciones")) {
                var tramos = new ArrayList<VersionTarifaria.Tramo>();
                for (var tramo : fila.path("tramos")) tramos.add(new VersionTarifaria.Tramo(
                    tramo.path("desdeM3").decimalValue(), null, tramo.path("precioPorM3").decimalValue(),
                    tramo.path("precioAlcantarilladoPorM3").decimalValue()));
                var tarifa = new VersionTarifaria(fila.path("codigoTarifa").stringValue(),
                    fila.path("versionTarifa").intValue(), fila.path("nombreTarifa").stringValue(),
                    LocalDate.parse(fila.path("vigenteDesde").stringValue()), fila.path("cargoFijo").decimalValue(),
                    fila.path("igvPorcentaje").decimalValue(), tramos);
                asignaciones.add(new HistorialTarifario.Asignacion(tarifa, null,
                    LocalDate.parse(fila.path("asignadaDesde").stringValue()), null));
            }
            return Optional.of(new HistorialTarifario("empresa-agua", codigo, asignaciones));
        };
    }
    public FuenteRecibos recibos() {
        return (codigo, limite) -> {
            if (!conocido(codigo)) return Optional.empty();
            var recibos = new ArrayList<ReciboEmitido>();
            for (var fila : datos.at("/recibos/recibos")) recibos.add(new ReciboEmitido(
                fila.path("numeroRecibo").stringValue(), YearMonth.from(LocalDate.parse(fila.path("periodo").stringValue())),
                fila.path("consumoLitros").decimalValue(), fila.path("cargoFijo").decimalValue(),
                fila.path("importeConsumo").decimalValue(), fila.path("importeTotal").decimalValue(),
                LocalDate.parse(fila.path("fechaDisponibilidad").stringValue()),
                LocalDate.parse(fila.path("fechaVencimiento").stringValue()),
                fila.path("codigoTarifa").stringValue(), fila.path("versionTarifa").intValue()));
            return Optional.of(new RecibosEmitidos(codigo, recibos.stream().limit(limite).toList()));
        };
    }
}
