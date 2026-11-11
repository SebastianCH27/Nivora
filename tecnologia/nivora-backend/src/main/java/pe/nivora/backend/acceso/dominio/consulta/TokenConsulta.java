package pe.nivora.backend.acceso.dominio.consulta;

import pe.nivora.backend.acceso.dominio.TokenOpaco;

public record TokenConsulta(String valor) {

    public TokenConsulta {
        if (valor == null || !valor.matches("nc_[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("Token de consulta invalido.");
        }
    }

    public static TokenConsulta crear() {
        return new TokenConsulta("nc_" + TokenOpaco.aleatorio());
    }

    public String huella() {
        return TokenOpaco.huellaDe(valor);
    }

    @Override
    public String toString() {
        return "TokenConsulta[oculto]";
    }
}
