package pe.nivora.backend.lecturas.infraestructura.persistencia;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.aplicacion.RecuperarLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioRecuperacionLecturas;

@Component
@ConditionalOnProperty(name = "nivora.recuperacion.comprobar-al-iniciar", havingValue = "true")
public class ComprobarRecuperacionLecturas implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(ComprobarRecuperacionLecturas.class);
    private final RecuperarLecturas recuperar;
    private final RepositorioRecuperacionLecturas progreso;
    private final RepositorioLecturas lecturas;
    private final Clock reloj;
    private final CodigoSuministro codigo;
    private final int limite;
    private final int maximoPaginas;
    private final String operacion;

    public ComprobarRecuperacionLecturas(RecuperarLecturas recuperar,
            RepositorioRecuperacionLecturas progreso, RepositorioLecturas lecturas, Clock reloj,
            @Value("${nivora.lecturas.codigo-prueba}") String codigo,
            @Value("${nivora.lecturas.limite-prueba}") int limite,
            @Value("${nivora.recuperacion.maximo-paginas}") int maximoPaginas,
            @Value("${nivora.recuperacion.operacion-prueba}") String operacion) {
        this.recuperar = recuperar;
        this.progreso = progreso;
        this.lecturas = lecturas;
        this.reloj = reloj;
        this.codigo = new CodigoSuministro(codigo);
        this.limite = limite;
        this.maximoPaginas = maximoPaginas;
        this.operacion = operacion;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        switch (operacion) {
            case "recuperar" -> {
                var resultado = recuperar.ejecutar(codigo, limite, maximoPaginas);
                var conteo = resultado.incorporacion();
                LOG.info("Recuperacion lecturas: suministro {}; paginas {}; nuevas {}; repetidas {}; rechazadas {}; cursor {}; estado {}.",
                    codigo.valor(), resultado.paginas(), conteo.nuevas(), conteo.repetidas(), conteo.rechazadas(),
                    resultado.progreso().cursor(), resultado.progreso().estado());
            }
            case "consultar" -> {
                var actual = progreso.consultar("empresa-agua", codigo);
                LOG.info("Estado recuperacion: suministro {}; cursor {}; estado {}; revision {}; guardadas {}.",
                    codigo.valor(), actual.cursor(), actual.estado(), actual.revision(), lecturas.contar(codigo));
            }
            case "reiniciar" -> {
                var actual = progreso.consultar("empresa-agua", codigo);
                var siguiente = progreso.reiniciar(actual, reloj.instant());
                LOG.info("Recorrido reiniciado: suministro {}; cursor {}; revision {}; lecturas conservadas {}.",
                    codigo.valor(), siguiente.cursor(), siguiente.revision(), lecturas.contar(codigo));
            }
            default -> throw new IllegalArgumentException("La operacion debe ser recuperar, consultar o reiniciar.");
        }
    }
}
