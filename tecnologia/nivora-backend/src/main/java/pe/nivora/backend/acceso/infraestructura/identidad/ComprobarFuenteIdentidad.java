package pe.nivora.backend.acceso.infraestructura.identidad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import pe.nivora.backend.acceso.dominio.Dni;
import pe.nivora.backend.acceso.dominio.contratos.FuenteIdentidad;

@Component
@ConditionalOnNotWebApplication
@ConditionalOnProperty(name = "nivora.identidad.comprobar-al-iniciar", havingValue = "true")
public class ComprobarFuenteIdentidad implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ComprobarFuenteIdentidad.class);
    private final FuenteIdentidad fuente;

    public ComprobarFuenteIdentidad(FuenteIdentidad fuente) {
        this.fuente = fuente;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        fuente.consultar(new Dni(System.getenv("NIVORA_IDENTIDAD_DNI")));
        LOG.info("Consulta DNI correcta desde Nivora mediante APIsPERU.");
    }
}
