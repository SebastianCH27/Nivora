package pe.nivora.backend.acceso.infraestructura.empresaagua;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.contratos.FuenteSuministros;

/** Comprobacion manual opcional, desactivada durante el arranque habitual. */
@Component
@ConditionalOnProperty(name = "nivora.empresa-agua.comprobar-al-iniciar", havingValue = "true")
public class ComprobarFuenteSuministros implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ComprobarFuenteSuministros.class);

    private final FuenteSuministros fuente;
    private final CodigoSuministro codigo;

    public ComprobarFuenteSuministros(FuenteSuministros fuente,
            @Value("${nivora.empresa-agua.codigo-prueba}") String codigo) {
        this.fuente = fuente;
        this.codigo = new CodigoSuministro(codigo);
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        if (fuente.consultar(codigo).isPresent()) {
            LOG.info("Comprobacion Empresa de Agua: suministro {} encontrado.", codigo.valor());
        } else {
            LOG.info("Comprobacion Empresa de Agua: suministro {} inexistente confirmado.",
                codigo.valor());
        }
    }
}
