package pe.nivora.backend.compartido.infraestructura.seguridad;

import java.net.URI;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** El perfil seguro no admite desactivar TLS ni volver al proveedor por HTTP. */
@Configuration(proxyBeanMethods = false)
@Profile("https")
public class TransporteSeguroConfiguracion {
    public TransporteSeguroConfiguracion(Environment entorno) {
        String fuente = entorno.getRequiredProperty("nivora.empresa-agua.url-base");
        if (!entorno.getProperty("server.ssl.enabled", Boolean.class, false)
                || !"https".equals(URI.create(fuente).getScheme())) {
            throw new IllegalStateException("El perfil https requiere TLS en Nivora y en la Empresa de Agua.");
        }
    }
}
