package pe.nivora.empresaagua.simulacion;

import java.time.LocalDate;
import java.time.ZoneId;

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
    name = "empresa.lecturas.automatica.habilitada",
    havingValue = "true"
)
public class LecturasAutomaticas {

    private static final Logger log =
        LoggerFactory.getLogger(LecturasAutomaticas.class);

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final GeneradorLecturasDiarias generador;

    public LecturasAutomaticas(GeneradorLecturasDiarias generador) {
        this.generador = generador;
    }

    @Scheduled(
        initialDelayString =
            "${empresa.lecturas.automatica.espera-inicial-ms:10000}",
        fixedDelayString =
            "${empresa.lecturas.automatica.intervalo-ms:60000}"
    )
    public void actualizar() {
        LocalDate hoy = LocalDate.now(LIMA);

        try {
            int dias = generador.generarHasta(hoy);

            if (dias > 0) {
                log.info(
                    "Actualizacion automatica completada: "
                        + "{} dias nuevos. Fecha: {}",
                    dias, hoy);
            }
        } catch (RuntimeException error) {
            log.error(
                "No se pudieron completar las lecturas. "
                    + "Se reintentara en la proxima revision.",
                error);
        }
    }
}