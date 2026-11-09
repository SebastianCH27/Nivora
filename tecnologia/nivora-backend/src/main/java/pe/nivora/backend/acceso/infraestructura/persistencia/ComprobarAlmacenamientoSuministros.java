package pe.nivora.backend.acceso.infraestructura.persistencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import pe.nivora.backend.acceso.aplicacion.ActualizarSuministro;
import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.contratos.RepositorioSuministros;

/** Comprobacion explicita por consola, desactivada en el arranque habitual. */
@Component
@ConditionalOnProperty(name = "nivora.suministros.comprobar-al-iniciar", havingValue = "true")
public class ComprobarAlmacenamientoSuministros implements ApplicationRunner {

    private static final Logger LOG =
        LoggerFactory.getLogger(ComprobarAlmacenamientoSuministros.class);

    private final ActualizarSuministro actualizar;
    private final RepositorioSuministros repositorio;
    private final CodigoSuministro codigo;
    private final String operacion;

    public ComprobarAlmacenamientoSuministros(ActualizarSuministro actualizar,
            RepositorioSuministros repositorio,
            @Value("${nivora.suministros.codigo-prueba}") String codigo,
            @Value("${nivora.suministros.operacion-prueba}") String operacion) {
        this.actualizar = actualizar;
        this.repositorio = repositorio;
        this.codigo = new CodigoSuministro(codigo);
        this.operacion = operacion;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        switch (operacion) {
            case "actualizar" -> {
                if (actualizar.ejecutar(codigo).isPresent()) {
                    LOG.info("Almacenamiento Nivora: suministro {} verificado; registro propio disponible.",
                        codigo.valor());
                } else {
                    LOG.info("Almacenamiento Nivora: suministro {} inexistente en la fuente; datos guardados conservados.",
                        codigo.valor());
                }
            }
            case "consultar" -> {
                var guardado = repositorio.buscar(codigo);
                if (guardado.isPresent()) {
                    LOG.info("Almacenamiento Nivora: suministro {} encontrado en la base propia. Verificado en {}.",
                        codigo.valor(), guardado.orElseThrow().fechaVerificacion());
                } else {
                    LOG.info("Almacenamiento Nivora: suministro {} sin datos guardados.", codigo.valor());
                }
            }
            default -> throw new IllegalArgumentException(
                "La operacion de prueba debe ser actualizar o consultar.");
        }
    }
}
