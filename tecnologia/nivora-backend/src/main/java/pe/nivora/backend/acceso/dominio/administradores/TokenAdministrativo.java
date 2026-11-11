package pe.nivora.backend.acceso.dominio.administradores;

import pe.nivora.backend.acceso.dominio.TokenOpaco;

public final class TokenAdministrativo {
    private final String valor;

    private TokenAdministrativo(String valor) { this.valor = valor; }

    public static TokenAdministrativo crear() {
        return new TokenAdministrativo(TokenOpaco.aleatorio());
    }

    public static TokenAdministrativo interpretar(String valor) {
        if (valor == null || !valor.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("Token administrativo invalido.");
        }
        return new TokenAdministrativo(valor);
    }

    public String valor() { return valor; }
    public String huella() { return huellaDe(valor); }

    public static String huellaDe(String texto) {
        return TokenOpaco.huellaDe(texto);
    }

    @Override public String toString() { return "TokenAdministrativo[oculto]"; }
}
