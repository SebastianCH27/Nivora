package pe.nivora.backend.lecturas.infraestructura.empresaagua;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturas;

@Component
@ConditionalOnProperty(name = "nivora.lecturas.comprobar-al-iniciar", havingValue = "true")
public class ComprobarFuenteLecturas implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(ComprobarFuenteLecturas.class);

    private final FuenteLecturas fuente;
    private final CodigoSuministro codigo;
    private final String cursor;
    private final int limite;

    public ComprobarFuenteLecturas(FuenteLecturas fuente,
            @Value("${nivora.lecturas.codigo-prueba}") String codigo,
            @Value("${nivora.lecturas.cursor-prueba}") String cursor,
            @Value("${nivora.lecturas.limite-prueba}") int limite) {
        this.fuente = fuente;
        this.codigo = new CodigoSuministro(codigo);
        this.cursor = cursor;
        this.limite = limite;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        var resultado = fuente.consultar(codigo, cursor, limite);
        if (resultado.isPresent()) {
            var lote = resultado.orElseThrow();
            LOG.info("Comprobacion lecturas: suministro {}; {} lecturas validas; siguiente cursor {}; hay mas {}.",
                codigo.valor(), lote.lecturas().size(), lote.siguienteCursor(), lote.hayMas());
        } else {
            LOG.info("Comprobacion lecturas: suministro {} inexistente confirmado.", codigo.valor());
        }
    }
}
