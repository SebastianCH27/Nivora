package pe.nivora.backend.acceso.dominio.administradores;

import java.time.Instant;
import java.util.Objects;

/** Datos de la cuenta; nunca contiene su clave ni su hash. */
public record Administrador(UsuarioAdministrador usuario, String nombre, boolean activa,
        boolean gestionarAdministradores, boolean cambioClaveRequerido,
        long versionCredenciales, Instant creadoEn, Instant actualizadoEn) {
    public Administrador {
        Objects.requireNonNull(usuario);
        nombre = normalizarNombre(nombre);
        if (versionCredenciales < 1) throw new IllegalArgumentException("Version de credenciales invalida.");
        Objects.requireNonNull(creadoEn);
        Objects.requireNonNull(actualizadoEn);
    }

    public static String normalizarNombre(String nombre) {
        if (nombre == null || nombre.isBlank() || nombre.codePointCount(0, nombre.length()) > 120
                || nombre.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("El nombre administrativo debe tener de 1 a 120 caracteres y no contener controles.");
        }
        return nombre.strip();
    }
}
