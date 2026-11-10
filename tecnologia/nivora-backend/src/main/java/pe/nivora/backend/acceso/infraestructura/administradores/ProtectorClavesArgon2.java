package pe.nivora.backend.acceso.infraestructura.administradores;

import java.nio.CharBuffer;
import java.util.regex.Pattern;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import pe.nivora.backend.acceso.dominio.contratos.ProtectorClavesAdministrativas;

public final class ProtectorClavesArgon2 implements ProtectorClavesAdministrativas {
    private final int longitudMinima;
    // Argon2id: 19 MiB, dos pasadas, una via; sal aleatoria 16 B y salida 32 B.
    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    private static final Pattern FORMATO = Pattern.compile(
        "\\$argon2id\\$v=19\\$m=19456,t=2,p=1\\$[A-Za-z0-9+/]{22}\\$[A-Za-z0-9+/]{43}");

    public ProtectorClavesArgon2() { this(false); }

    public ProtectorClavesArgon2(boolean permitirClaveCortaInicial) {
        longitudMinima = permitirClaveCortaInicial ? 6 : 15;
    }

    @Override
    public String proteger(char[] clave) {
        if (!claveValida(clave, longitudMinima)) {
            throw new IllegalArgumentException(
                "La clave administrativa debe tener entre " + longitudMinima
                    + " y 128 caracteres Unicode, sin caracteres de control.");
        }
        return encoder.encode(CharBuffer.wrap(clave));
    }

    @Override
    public boolean coincide(char[] clave, String hashGuardado) {
        // No interpretar parametros arbitrarios de memoria/CPU procedentes de un hash.
        // La politica de altas nuevas no invalida claves ya guardadas.
        if (!claveValida(clave, 1) || hashGuardado == null || !FORMATO.matcher(hashGuardado).matches()) {
            return false;
        }
        try {
            return encoder.matches(CharBuffer.wrap(clave), hashGuardado);
        } catch (IllegalArgumentException error) { return false; }
    }

    private static boolean claveValida(char[] clave, int minimo) {
        if (clave == null || clave.length < minimo || clave.length > 256) return false;
        int cantidad = Character.codePointCount(clave, 0, clave.length);
        if (cantidad < minimo || cantidad > 128) return false;
        for (int i = 0; i < clave.length;) {
            int punto = Character.codePointAt(clave, i);
            if (Character.isISOControl(punto) || (punto >= 0xD800 && punto <= 0xDFFF)) return false;
            i += Character.charCount(punto);
        }
        return true;
    }
}
