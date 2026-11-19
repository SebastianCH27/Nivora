package pe.nivora.backend.integracion.contratos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DependenciasArquitecturaTests {
    @Test void dominioYAplicacionNoDependenDeInfraestructuraPresentacionNiTransportes() throws Exception {
        var prohibida = Pattern.compile("(?:pe\\.nivora\\.backend\\.[\\w.]*\\.(?:infraestructura|presentacion)\\.|"
            + "org\\.springframework\\.|jakarta\\.|java\\.sql\\.|javax\\.sql\\.|java\\.net\\.(?!URI(?:SyntaxException)?\\b)|java\\.io\\.|java\\.nio\\.file\\.|"
            + "(?:tools|com\\.fasterxml)\\.jackson\\.|org\\.apache\\.http\\.)");
        var violaciones = new ArrayList<String>();
        int archivos = 0;
        try (var fuentes = Files.walk(Path.of("src/main/java/pe/nivora/backend"))) {
            for (var fuente : fuentes.filter(p -> p.toString().endsWith(".java")).toList()) {
                String ruta = fuente.toString().replace('\\', '/');
                if (!ruta.contains("/dominio/") && !ruta.contains("/aplicacion/")) continue;
                archivos++;
                // Se revisa el cuerpo completo, incluidas referencias de tipos con nombre completo.
                String codigo = Files.readString(fuente).replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*", "");
                var encontrada = prohibida.matcher(codigo);
                while (encontrada.find()) violaciones.add(ruta + ": " + encontrada.group());
            }
        }
        assertTrue(archivos > 100, "La revision debe cubrir todas las fuentes del nucleo.");
        assertTrue(violaciones.isEmpty(), String.join("\n", violaciones));
    }
}
