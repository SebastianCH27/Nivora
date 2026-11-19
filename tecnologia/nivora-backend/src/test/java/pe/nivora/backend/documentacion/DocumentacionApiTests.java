package pe.nivora.backend.documentacion;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

/** Contrasta el contrato versionado con las rutas y DTO reales del backend. */
@SpringBootTest
@ActiveProfiles("test")
class DocumentacionApiTests {
    private static Map<?, ?> documento;
    private static Map<?, ?> esquemas;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @BeforeAll
    static void leerContrato() throws Exception {
        documento = new JsonMapper().readValue(Path.of("documentacion/openapi.json").toFile(), Map.class);
        esquemas = mapa(mapa(documento.get("components")).get("schemas"));
        assertEquals("3.1.0", documento.get("openapi"));
    }

    @Test
    void documentaTodasLasOperacionesRegistradasSinInventarRutas() {
        Set<String> reales = rutas().stream().map(Ruta::clave).collect(Collectors.toCollection(TreeSet::new));
        Set<String> documentadas = new TreeSet<>();
        Set<String> identificadores = new TreeSet<>();
        for (var ruta : mapa(documento.get("paths")).entrySet()) {
            if (!ruta.getKey().toString().startsWith("/api/v1/")) continue;
            for (var operacion : mapa(ruta.getValue()).entrySet()) {
                if (!Set.of("get", "post", "put", "patch", "delete", "head", "options").contains(operacion.getKey())) continue;
                var datos = mapa(operacion.getValue());
                documentadas.add(operacion.getKey().toString().toUpperCase(java.util.Locale.ROOT) + " " + ruta.getKey());
                assertTrue(identificadores.add((String) datos.get("operationId")), "operationId duplicado");
            }
        }
        assertEquals(reales, documentadas, "Actualizar OpenAPI cuando se agrega, retira o cambia una ruta/metodo.");
        for (var ruta : rutas()) {
            var operacion = operacion(ruta);
            assertEquals(ruta.manejador().getBeanType().getName() + "#" + ruta.manejador().getMethod().getName(),
                operacion.get("x-java-handler"), ruta.clave());
            var respuestas = mapa(operacion.get("responses"));
            assertTrue(respuestas.keySet().stream().anyMatch(k -> k.toString().matches("2[0-9]{2}")), ruta.clave());
            boolean inicio = ruta.metodo().equals("POST") && Set.of(
                "/api/v1/acceso/consulta/sesiones", "/api/v1/acceso/administradores/sesiones").contains(ruta.ruta());
            var seguridad = lista(operacion.get("security"));
            if (inicio) {
                assertTrue(seguridad.isEmpty(), ruta.clave());
            } else {
                String esquema = ruta.ruta().startsWith("/api/v1/acceso/consulta/") ? "sesionConsulta" : "sesionAdministrativa";
                assertEquals(1, seguridad.size(), ruta.clave());
                assertEquals(Set.of(esquema), mapa(seguridad.getFirst()).keySet(), ruta.clave());
                assertTrue(respuestas.containsKey("401"), ruta.clave());
            }
        }
    }

    @Test
    void documentaParametrosRealesConObligatoriedadYValoresPredeterminados() {
        for (var ruta : rutas()) {
            var declarados = listaOpcional(operacion(ruta), "parameters");
            Set<String> esperados = new TreeSet<>();
            for (var parametro : ruta.manejador().getMethodParameters()) {
                var query = parametro.getParameterAnnotation(RequestParam.class);
                var path = parametro.getParameterAnnotation(PathVariable.class);
                if (query == null && path == null) continue;
                String nombre = query != null ? nombre(query.name(), query.value(), parametro.getParameter().getName())
                    : nombre(path.name(), path.value(), parametro.getParameter().getName());
                String lugar = query != null ? "query" : "path";
                esperados.add(lugar + ":" + nombre);
                var encontrados = declarados.stream().map(DocumentacionApiTests::mapa)
                    .filter(p -> lugar.equals(p.get("in")) && nombre.equals(p.get("name"))).toList();
                assertEquals(1, encontrados.size(), ruta.clave() + " " + nombre);
                var p = encontrados.getFirst();
                boolean tieneDefault = query != null && !query.defaultValue().equals(ValueConstants.DEFAULT_NONE);
                boolean obligatorio = path != null || (query.required() && !tieneDefault);
                assertEquals(obligatorio, p.get("required"), ruta.clave() + " " + nombre);
                comprobarTipo(parametro.getGenericParameterType(), mapa(p.get("schema")), ruta.clave() + " " + nombre);
                if (tieneDefault) assertEquals(query.defaultValue(), String.valueOf(mapa(p.get("schema")).get("default")), ruta.clave() + " " + nombre);
            }
            Set<String> presentes = declarados.stream().map(DocumentacionApiTests::mapa)
                .map(p -> p.get("in") + ":" + p.get("name")).collect(Collectors.toCollection(TreeSet::new));
            assertEquals(esperados, presentes, ruta.clave());
        }
    }

    @Test
    void conservaLosCamposYTiposDeLosRegistrosYEnumsPublicados() throws Exception {
        for (var entrada : esquemas.entrySet()) {
            var esquema = mapa(entrada.getValue());
            if (esquema.containsKey("x-java-enum")) {
                Class<?> tipo = Class.forName((String) esquema.get("x-java-enum"));
                assertTrue(tipo.isEnum(), entrada.getKey().toString());
                var valores = Arrays.stream(tipo.getEnumConstants()).map(v -> ((Enum<?>) v).name()).collect(Collectors.toSet());
                assertEquals(valores, Set.copyOf(lista(esquema.get("enum"))), entrada.getKey().toString());
            }
            for (var nombre : listaOpcional(esquema, "x-java-types")) {
                Class<?> tipo = Class.forName((String) nombre);
                assertTrue(tipo.isRecord(), nombre.toString());
                var propiedades = mapa(esquema.get("properties"));
                var campos = Arrays.stream(tipo.getRecordComponents()).map(c -> c.getName()).collect(Collectors.toSet());
                assertEquals(campos, propiedades.keySet(), nombre.toString());
                assertEquals(campos, Set.copyOf(lista(esquema.get("required"))), nombre.toString());
                for (var campo : tipo.getRecordComponents()) {
                    comprobarTipo(campo.getGenericType(), mapa(propiedades.get(campo.getName())), nombre + "." + campo.getName());
                }
            }
        }
        for (var ruta : rutas()) {
            Type retorno = ruta.manejador().getMethod().getGenericReturnType();
            if (!(retorno instanceof ParameterizedType parametrizado) || parametrizado.getRawType() != ResponseEntity.class) continue;
            Type cuerpo = parametrizado.getActualTypeArguments()[0];
            if (!(cuerpo instanceof Class<?> clase) || !clase.isRecord()) continue;
            for (var respuesta : mapa(operacion(ruta).get("responses")).entrySet()) {
                if (!respuesta.getKey().toString().matches("2[0-9]{2}")) continue;
                var contenido = mapa(mapa(respuesta.getValue()).get("content"));
                comprobarTipo(cuerpo, mapa(mapa(contenido.get("application/json")).get("schema")), ruta.clave());
            }
        }
    }

    private List<Ruta> rutas() {
        var resultado = new ArrayList<Ruta>();
        mappings.getHandlerMethods().forEach((mapping, manejador) -> {
            if (!manejador.getBeanType().getPackageName().startsWith("pe.nivora.backend")) return;
            for (String patron : mapping.getPatternValues()) {
                for (var metodo : mapping.getMethodsCondition().getMethods()) {
                    resultado.add(new Ruta(metodo.name(), patron, manejador));
                }
            }
        });
        assertFalse(resultado.isEmpty(), "No se registraron controladores del backend.");
        return resultado;
    }

    private static Map<?, ?> operacion(Ruta ruta) {
        return mapa(mapa(mapa(documento.get("paths")).get(ruta.ruta())).get(ruta.metodo().toLowerCase(java.util.Locale.ROOT)));
    }

    private static Map<?, ?> resolver(Map<?, ?> esquema) {
        if (esquema.containsKey("$ref")) {
            String referencia = (String) esquema.get("$ref");
            assertTrue(referencia.startsWith("#/components/schemas/"), referencia);
            return resolver(mapa(esquemas.get(referencia.substring("#/components/schemas/".length()))));
        }
        if (esquema.containsKey("anyOf")) {
            return lista(esquema.get("anyOf")).stream().map(DocumentacionApiTests::mapa)
                .filter(s -> !"null".equals(s.get("type"))).map(DocumentacionApiTests::resolver).findFirst().orElseThrow();
        }
        return esquema;
    }

    private static void comprobarTipo(Type tipo, Map<?, ?> original, String contexto) {
        var esquema = resolver(original);
        if (tipo instanceof ParameterizedType parametrizado && parametrizado.getRawType() instanceof Class<?> raw
                && Collection.class.isAssignableFrom(raw)) {
            assertEquals("array", esquema.get("type"), contexto);
            comprobarTipo(parametrizado.getActualTypeArguments()[0], mapa(esquema.get("items")), contexto + "[]");
            return;
        }
        assertInstanceOf(Class.class, tipo, contexto);
        Class<?> clase = (Class<?>) tipo;
        String esperado;
        if (clase == boolean.class || clase == Boolean.class) esperado = "boolean";
        else if (clase == int.class || clase == long.class || clase == Integer.class || clase == Long.class) esperado = "integer";
        else if (clase == BigDecimal.class || clase == double.class || clase == Double.class) esperado = "number";
        else if (clase == String.class || clase == UUID.class || clase == Instant.class || clase == LocalDate.class
                || clase == YearMonth.class || clase.isEnum()) esperado = "string";
        else if (clase.isRecord()) esperado = "object";
        else throw new AssertionError("Tipo de contrato no contemplado: " + clase.getName() + " en " + contexto);
        assertEquals(esperado, esquema.get("type"), contexto);
        if (clase.isEnum()) assertEquals(clase.getName(), esquema.get("x-java-enum"), contexto);
        if (clase.isRecord()) assertTrue(lista(esquema.get("x-java-types")).contains(clase.getName()), contexto);
    }

    private static String nombre(String name, String value, String alternativo) {
        return !name.isEmpty() ? name : !value.isEmpty() ? value : alternativo;
    }

    private static Map<?, ?> mapa(Object valor) {
        assertInstanceOf(Map.class, valor);
        return (Map<?, ?>) valor;
    }

    private static List<?> lista(Object valor) {
        assertInstanceOf(List.class, valor);
        return (List<?>) valor;
    }

    private static List<?> listaOpcional(Map<?, ?> objeto, String campo) {
        return objeto.containsKey(campo) ? lista(objeto.get(campo)) : List.of();
    }

    private record Ruta(String metodo, String ruta, HandlerMethod manejador) {
        String clave() { return metodo + " " + ruta; }
    }
}
