package pe.nivora.backend.acceso.dominio.administradores;

public record CredencialAdministrativa(Administrador cuenta, String hashClave) {
    @Override public String toString() {
        return "CredencialAdministrativa[usuario=" + cuenta.usuario().valor() + ", hash oculto]";
    }
}
