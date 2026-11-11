package pe.nivora.backend.acceso.infraestructura.seguridad;

import java.security.Principal;

import pe.nivora.backend.acceso.dominio.consulta.SesionConsulta;
import pe.nivora.backend.acceso.dominio.consulta.TokenConsulta;

public record PrincipalConsulta(TokenConsulta token, SesionConsulta sesion) implements Principal {

    @Override
    public String getName() {
        return sesion.id();
    }
}
