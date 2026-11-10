package pe.nivora.backend.acceso.dominio.contratos;

import java.time.Instant;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.administradores.Administrador;
import pe.nivora.backend.acceso.dominio.administradores.UsuarioAdministrador;

public interface RepositorioAdministradores {
    /** Solo admite la primera cuenta; cuenta y auditoria se confirman juntas. */
    Administrador crearPrimero(UsuarioAdministrador usuario, String nombre,
            String hashClave, Instant ahora);
    Optional<Administrador> buscar(UsuarioAdministrador usuario);
}
