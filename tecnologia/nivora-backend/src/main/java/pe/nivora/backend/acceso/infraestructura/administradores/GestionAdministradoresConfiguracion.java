package pe.nivora.backend.acceso.infraestructura.administradores;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.nivora.backend.acceso.aplicacion.GestionarAdministradores;
import pe.nivora.backend.acceso.dominio.administradores.PoliticaSesionAdministrativa;
import pe.nivora.backend.acceso.dominio.contratos.*;

@Configuration(proxyBeanMethods = false)
public class GestionAdministradoresConfiguracion {
    @Bean
    GestionarAdministradores gestionarAdministradores(RepositorioGestionAdministradores gestion,
            RepositorioSesionesAdministrativas sesiones, ProtectorClavesAdministrativas claves,
            Clock reloj, PoliticaSesionAdministrativa politica,
            @Value("${nivora.administradores.confirmaciones-por-usuario}") int usuario,
            @Value("${nivora.administradores.confirmaciones-por-origen}") int origen) {
        var limites = new PoliticaSesionAdministrativa(politica.inactividad(), politica.duracionMaxima(),
            politica.ventanaIntentos(), usuario, origen);
        return new GestionarAdministradores(gestion, sesiones, claves, reloj, limites);
    }
}
