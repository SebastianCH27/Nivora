package pe.nivora.backend.acceso.infraestructura.seguridad;

import java.security.Principal;
import pe.nivora.backend.acceso.dominio.administradores.*;

public record PrincipalAdministrativo(TokenAdministrativo token, SesionAdministrativa sesion)
        implements Principal {
    @Override public String getName() { return sesion.cuenta().usuario().valor(); }
    @Override public String toString() { return "PrincipalAdministrativo[usuario=" + getName() + "]"; }
}
