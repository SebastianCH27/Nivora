package pe.nivora.backend.acceso.dominio.administradores;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class TokenAdministrativo {
    private static final SecureRandom AZAR = new SecureRandom();
    private final String valor;

    private TokenAdministrativo(String valor) { this.valor = valor; }

    public static TokenAdministrativo crear() {
        byte[] bytes = new byte[32];
        AZAR.nextBytes(bytes);
        return new TokenAdministrativo(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
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
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    @Override public String toString() { return "TokenAdministrativo[oculto]"; }
}
