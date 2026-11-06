package pe.nivora.empresaagua.simulacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import pe.nivora.empresaagua.facturacion.GeneradorRecibos;

@Component
@ConditionalOnProperty(
    name = "empresa.carga-recibos.habilitada",
    havingValue = "true"
)
public class CargaRecibos implements ApplicationRunner {

    private static final Logger log =
        LoggerFactory.getLogger(CargaRecibos.class);

    private final GeneradorRecibos generador;

    public CargaRecibos(GeneradorRecibos generador) {
        this.generador = generador;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        int cantidad = generador.generarPendientes();

        log.info(
            "Generacion de recibos terminada: {} nuevos",
            cantidad);
    }
}