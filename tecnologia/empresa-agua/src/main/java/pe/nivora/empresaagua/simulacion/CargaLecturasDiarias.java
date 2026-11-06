package pe.nivora.empresaagua.simulacion;

import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "empresa.lecturas.habilitada",
    havingValue = "true"
)
public class CargaLecturasDiarias implements ApplicationRunner {

    private static final Logger log =
        LoggerFactory.getLogger(CargaLecturasDiarias.class);

    private final GeneradorLecturasDiarias generador;
    private final String hasta;

    public CargaLecturasDiarias(
            GeneradorLecturasDiarias generador,
            @Value("${empresa.lecturas.hasta:}") String hasta) {

        this.generador = generador;
        this.hasta = hasta;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        LocalDate objetivo = hasta.isBlank()
            ? LocalDate.now(ZoneId.of("America/Lima"))
            : LocalDate.parse(hasta.trim());

        int dias = generador.generarHasta(objetivo);

        log.info(
            "Generacion terminada: {} dias nuevos. Fecha objetivo: {}",
            dias, objetivo);
    }
}