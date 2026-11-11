package pe.nivora.backend.acceso.dominio.contratos;

import pe.nivora.backend.acceso.dominio.administradores.*;

public interface RepositorioGestionAdministradores {
    PaginaAdministradores listar(ActorAdministrativo actor, String despuesDe, int limite);
    Administrador consultar(ActorAdministrativo actor, UsuarioAdministrador usuario);
    PaginaAuditoriaAdministrativa auditoria(ActorAdministrativo actor, UsuarioAdministrador usuario,
        long despuesDe, int limite);
    ResultadoGestionAdministrativa crear(ConfirmacionAdministrativa confirmacion,
        UsuarioAdministrador usuario, String nombre, String hashClave, boolean gestionar);
    ResultadoGestionAdministrativa cambiarEstado(ConfirmacionAdministrativa confirmacion,
        UsuarioAdministrador usuario, boolean activa);
    ResultadoGestionAdministrativa cambiarPermiso(ConfirmacionAdministrativa confirmacion,
        UsuarioAdministrador usuario, boolean gestionar);
    ResultadoGestionAdministrativa restablecerClave(ConfirmacionAdministrativa confirmacion,
        UsuarioAdministrador usuario, String hashTemporal);
    ResultadoGestionAdministrativa cambiarClavePropia(ConfirmacionAdministrativa confirmacion,
        String hashNuevo);
}
