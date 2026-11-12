package pe.nivora.backend.tarifas.infraestructura;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.tarifas.aplicacion.IncorporarTarifas;

@Component
@ConditionalOnProperty(name = "nivora.tarifas.comprobar-al-iniciar", havingValue = "true")
public class ComprobarTarifas implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(ComprobarTarifas.class);

    private final IncorporarTarifas incorporar;
    private final CodigoSuministro suministro;

    public ComprobarTarifas(IncorporarTarifas incorporar,
            @Value("${nivora.tarifas.codigo-prueba}") String suministro) {
        this.incorporar = incorporar;
        this.suministro = new CodigoSuministro(suministro);
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        var guardado = incorporar.ejecutar(suministro);
        if (guardado.isEmpty()) {
            LOG.info("Tarifas: suministro {} inexistente en la fuente; datos guardados conservados.",
                suministro.valor());
            return;
        }
        var resultado = guardado.orElseThrow();
        LOG.info("Tarifas: suministro {}; revision {}; asignaciones guardadas {}.",
            suministro.valor(), resultado.revision(), resultado.historial().asignaciones().size());
    }
}
