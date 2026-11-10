package pe.nivora.backend.lecturas.infraestructura;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.ContextoLecturas;

/** Formato explicito del inventario; no deserializa clases indicadas por el emisor. */
public final class ContextoLecturasJson {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private ContextoLecturasJson() {}

    public static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo == null ? null : nodo.get(campo);
        if (valor == null || !valor.isString()) {
            throw new IllegalArgumentException("Falta un campo textual del contexto.");
        }
        return valor.stringValue();
    }

    public static ContextoLecturas interpretar(JsonNode corte, JsonNode inventario,
            CodigoSuministro codigo) {
        if (!"empresa-agua".equals(texto(corte, "fuente"))
                || !"empresa-agua".equals(texto(inventario, "fuente"))
                || !codigo.valor().equals(texto(inventario, "codigoSuministro"))
                || !"America/Lima".equals(texto(inventario, "zonaFechas"))) {
            throw new IllegalArgumentException("El contexto no corresponde a la fuente o suministro.");
        }
        String fecha = texto(corte, "confirmadoHasta");
        if (!fecha.endsWith("Z")) {
            throw new IllegalArgumentException("El corte debe expresarse en UTC.");
        }
        return crear("empresa-agua", codigo, texto(corte, "hastaIdLectura"),
            Instant.parse(fecha), inventario.get("medidores"));
    }

    private static ContextoLecturas crear(String fuente, CodigoSuministro codigo,
            String hasta, Instant fecha, JsonNode filas) {
        if (filas == null || !filas.isArray() || filas.size() < 1 || filas.size() > 1000) {
            throw new IllegalArgumentException("El inventario no es una lista utilizable.");
        }
        var medidores = new ArrayList<ContextoLecturas.Medidor>();
        for (JsonNode fila : filas) {
            JsonNode retiro = fila.get("fechaRetiro");
            if (retiro == null || (!retiro.isNull() && !retiro.isString())) {
                throw new IllegalArgumentException("Falta la fecha de retiro del medidor.");
            }
            medidores.add(new ContextoLecturas.Medidor(texto(fila, "idMedidor"),
                LocalDate.parse(texto(fila, "fechaInstalacion")),
                retiro.isNull() ? null : LocalDate.parse(retiro.stringValue())));
        }
        return new ContextoLecturas(fuente, codigo, hasta, fecha, medidores);
    }

    public static String inventario(ContextoLecturas contexto) {
        var lista = JSON.createArrayNode();
        for (var medidor : contexto.medidores()) {
            var fila = lista.addObject();
            fila.put("idMedidor", medidor.idMedidor());
            fila.put("fechaInstalacion", medidor.fechaInstalacion().toString());
            if (medidor.fechaRetiro() == null) fila.putNull("fechaRetiro");
            else fila.put("fechaRetiro", medidor.fechaRetiro().toString());
        }
        return JSON.writeValueAsString(lista);
    }

    public static ContextoLecturas restaurar(String fuente, CodigoSuministro codigo,
            String hasta, Instant fecha, String inventario) {
        return crear(fuente, codigo, hasta, fecha, JSON.reader()
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(inventario));
    }
}
