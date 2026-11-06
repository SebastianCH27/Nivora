package pe.nivora.empresaagua.facturacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnWebApplication(
    type = ConditionalOnWebApplication.Type.SERVLET
)
@ConditionalOnProperty(
    name = "empresa.emision-recibos.habilitada",
    havingValue = "true"
)
public class RecibosAutomaticos {

    private static final Logger log =
        LoggerFactory.getLogger(RecibosAutomaticos.class);

    private final GeneradorRecibos generador;
    private boolean primeraRevision = true;

    public RecibosAutomaticos(GeneradorRecibos generador) {
        this.generador = generador;
    }

    @Scheduled(
        initialDelayString =
            "${empresa.emision-recibos.espera-inicial-ms:10000}",
        fixedDelayString =
            "${empresa.emision-recibos.intervalo-ms:60000}"
    )
    public void revisar() {
        try {
            int cantidad = generador.generarPendientes();

            if (primeraRevision || cantidad > 0) {
                log.info(
                    "Emision automatica de recibos activa. "
                        + "Nuevos recibos: {}",
                    cantidad);

                primeraRevision = false;
            }
        } catch (RuntimeException error) {
            log.error(
                "No se pudo completar la emision de recibos. "
                    + "Se reintentara.",
                error);
        }
    }
}