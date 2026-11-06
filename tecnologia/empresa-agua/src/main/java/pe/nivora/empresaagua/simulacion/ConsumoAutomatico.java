package pe.nivora.empresaagua.simulacion;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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
    name = "empresa.consumo-continuo.habilitado",
    havingValue = "true"
)
public class ConsumoAutomatico {

    private static final Logger log =
        LoggerFactory.getLogger(ConsumoAutomatico.class);

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final GeneradorConsumoContinuo generador;
    private boolean primerAviso = true;

    public ConsumoAutomatico(GeneradorConsumoContinuo generador) {
        this.generador = generador;
    }

    @Scheduled(
        initialDelayString =
            "${empresa.consumo-continuo.espera-inicial-ms:2000}",
        fixedDelayString =
            "${empresa.consumo-continuo.intervalo-ms:2000}"
    )
    public void actualizar() {
        try {
            Instant punto =
                generador.actualizarHasta(Instant.now());

            if (primerAviso) {
                log.info(
                    "Simulacion continua activa. "
                        + "Datos actualizados hasta: {}",
                    punto.atZone(LIMA).format(
                        DateTimeFormatter.ofPattern(
                            "uuuu-MM-dd HH:mm:ss")));

                primerAviso = false;
            }
        } catch (RuntimeException error) {
            log.error(
                "No se pudo actualizar el consumo. "
                    + "Se reintentara.",
                error);
        }
    }
}