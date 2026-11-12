package pe.nivora.backend.recibos.infraestructura;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.recibos.dominio.FuenteRecibos;

@Component
@ConditionalOnProperty(name = "nivora.recibos.comprobar-al-iniciar", havingValue = "true")
public class ComprobarRecibos implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(ComprobarRecibos.class);
    private final FuenteRecibos fuente;
    private final CodigoSuministro suministro;

    public ComprobarRecibos(FuenteRecibos fuente,
            @Value("${nivora.recibos.codigo-prueba}") String suministro) {
        this.fuente = fuente;
        this.suministro = new CodigoSuministro(suministro);
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        var consulta = fuente.consultar(suministro, 1);
        if (consulta.isEmpty()) {
            LOG.info("Recibos: suministro {} inexistente en Empresa de Agua.", suministro.valor());
            return;
        }
        var recibos = consulta.orElseThrow().recibos();
        if (recibos.isEmpty()) {
            LOG.info("Recibos: suministro {} registrado, sin recibos disponibles.", suministro.valor());
            return;
        }
        var reciente = recibos.getFirst();
        LOG.info("Recibo emitido: suministro {}; periodo {}; importe total PEN {}; disponible {}; vence {}.",
            suministro.valor(), reciente.periodo(), reciente.importeTotal().toPlainString(),
            reciente.fechaDisponibilidad(), reciente.fechaVencimiento());
    }
}
