package pe.nivora.backend.notificaciones.infraestructura;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.notificaciones.aplicacion.EvaluarAlertaConsumo;

@Component
@ConditionalOnProperty(name = "nivora.alertas-consumo.comprobar-al-iniciar", havingValue = "true")
public class ComprobarAlertaConsumo implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(ComprobarAlertaConsumo.class);
    private final EvaluarAlertaConsumo evaluar;
    private final CodigoSuministro suministro;

    public ComprobarAlertaConsumo(EvaluarAlertaConsumo evaluar,
            @Value("${nivora.alertas-consumo.codigo-prueba}") String suministro) {
        this.evaluar = evaluar;
        this.suministro = new CodigoSuministro(suministro);
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        var resultado = evaluar.ejecutar(suministro);
        LOG.info("Alerta de consumo: suministro {}; periodo {}; resultado {}.",
            suministro.valor(), resultado.periodo(), resultado.estado());
    }
}
