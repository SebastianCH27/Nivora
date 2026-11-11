package pe.nivora.backend.acceso.dominio.administradores;

public record ResultadoGestionAdministrativa(Administrador cuenta, boolean modificada,
        boolean sesionActualRevocada) {}
