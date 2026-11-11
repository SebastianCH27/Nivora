package pe.nivora.backend.acceso.dominio.administradores;

import java.util.List;

public record PaginaAdministradores(List<Administrador> administradores,
        String siguienteDespuesDe, boolean hayMas) {
    public PaginaAdministradores { administradores = List.copyOf(administradores); }
}
