package pe.nivora.backend.lecturas.infraestructura.persistencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.aplicacion.ImportarPaginaLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioLecturas;

@Component
@ConditionalOnProperty(name = "nivora.lecturas.almacenamiento.comprobar-al-iniciar", havingValue = "true")
public class ComprobarAlmacenamientoLecturas implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(ComprobarAlmacenamientoLecturas.class);
    private final ImportarPaginaLecturas importar;
    private final RepositorioLecturas repositorio;
    private final CodigoSuministro codigo;
    private final String cursor;
    private final int limite;
    private final String operacion;

    public ComprobarAlmacenamientoLecturas(ImportarPaginaLecturas importar, RepositorioLecturas repositorio,
            @Value("${nivora.lecturas.codigo-prueba}") String codigo,
            @Value("${nivora.lecturas.cursor-prueba}") String cursor,
            @Value("${nivora.lecturas.limite-prueba}") int limite,
            @Value("${nivora.lecturas.almacenamiento.operacion-prueba}") String operacion) {
        this.importar = importar;
        this.repositorio = repositorio;
        this.codigo = new CodigoSuministro(codigo);
        this.cursor = cursor;
        this.limite = limite;
        this.operacion = operacion;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        switch (operacion) {
            case "importar" -> {
                var resultado = importar.ejecutar(codigo, cursor, limite);
                if (resultado.isPresent()) {
                    var pagina = resultado.orElseThrow();
                    var resumen = pagina.incorporacion();
                    LOG.info("Incorporacion lecturas: suministro {}; nuevas {}; repetidas {}; rechazadas {}; cursor informado {}; hay mas {}.",
                        codigo.valor(), resumen.nuevas(), resumen.repetidas(), resumen.rechazadas(),
                        pagina.siguienteCursor(), pagina.hayMas());
                } else {
                    LOG.info("Incorporacion lecturas: suministro {} inexistente en la fuente.", codigo.valor());
                }
            }
            case "consultar" -> LOG.info("Almacenamiento lecturas: suministro {}; guardadas {}; incidencias {}.",
                codigo.valor(), repositorio.contar(codigo), repositorio.contarIncidencias(codigo));
            default -> throw new IllegalArgumentException("La operacion debe ser importar o consultar.");
        }
    }
}
