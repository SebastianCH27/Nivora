package pe.nivora.backend.recibos.infraestructura;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.recibos.aplicacion.IncorporarRecibos;
import pe.nivora.backend.recibos.dominio.RepositorioRecibos;

@Component
@ConditionalOnProperty(name = "nivora.recibos.almacenamiento.comprobar-al-iniciar", havingValue = "true")
public class ComprobarAlmacenamientoRecibos implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(ComprobarAlmacenamientoRecibos.class);
    private final IncorporarRecibos incorporar;
    private final RepositorioRecibos repositorio;
    private final CodigoSuministro suministro;

    public ComprobarAlmacenamientoRecibos(IncorporarRecibos incorporar, RepositorioRecibos repositorio,
            @Value("${nivora.recibos.codigo-prueba}") String suministro) {
        this.incorporar = incorporar;
        this.repositorio = repositorio;
        this.suministro = new CodigoSuministro(suministro);
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        var resultado = incorporar.ejecutar(suministro, 12);
        if (resultado.isEmpty()) {
            LOG.info("Recibos: suministro {} inexistente en Empresa de Agua; datos locales conservados.",
                suministro.valor());
            return;
        }
        var incorporados = resultado.orElseThrow();
        LOG.info("Recibos incorporados: suministro {}; nuevos {}; repetidos {}.",
            suministro.valor(), incorporados.nuevos(), incorporados.repetidos());
        repositorio.buscarRecientes(suministro, 1).stream().findFirst().ifPresent(guardado -> {
            var recibo = guardado.recibo();
            LOG.info("Recibo guardado: periodo {}; importe total PEN {}; vence {}; recibido UTC {}.",
                recibo.periodo(), recibo.importeTotal().toPlainString(), recibo.fechaVencimiento(), guardado.recibidoEn());
        });
    }
}
