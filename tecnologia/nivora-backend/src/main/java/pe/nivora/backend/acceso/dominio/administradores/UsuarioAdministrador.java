package pe.nivora.backend.acceso.dominio.administradores;

import java.util.Locale;

public record UsuarioAdministrador(String valor) {
    public UsuarioAdministrador {
        if (valor == null || !valor.matches("[A-Za-z][A-Za-z0-9._-]{2,49}")) {
            throw new IllegalArgumentException(
                "El usuario administrativo debe tener de 3 a 50 caracteres, comenzar con una letra y usar letras ASCII, numeros, punto, guion o guion bajo.");
        }
        valor = valor.toLowerCase(Locale.ROOT);
    }
}
