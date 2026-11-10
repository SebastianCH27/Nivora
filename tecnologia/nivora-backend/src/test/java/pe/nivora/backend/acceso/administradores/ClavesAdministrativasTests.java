package pe.nivora.backend.acceso.administradores;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import pe.nivora.backend.acceso.dominio.administradores.UsuarioAdministrador;
import pe.nivora.backend.acceso.infraestructura.administradores.ProtectorClavesArgon2;

import static org.junit.jupiter.api.Assertions.*;

class ClavesAdministrativasTests {
    private final ProtectorClavesArgon2 claves = new ProtectorClavesArgon2();

    @Test
    void hashArgon2idTieneSalDistintaYCompruebaLaClaveSinModificarla() {
        char[] clave = "Una frase ficticia de prueba".toCharArray();
        char[] copia = clave.clone();
        String primero = claves.proteger(clave);
        String segundo = claves.proteger(clave);
        assertTrue(primero.startsWith("$argon2id$v=19$m=19456,t=2,p=1$"));
        assertNotEquals(primero, segundo);
        assertFalse(primero.contains(new String(clave)));
        assertTrue(claves.coincide(clave, primero));
        assertTrue(claves.coincide(clave, segundo));
        assertFalse(claves.coincide("Otra frase ficticia distinta".toCharArray(), primero));
        assertArrayEquals(copia, clave);
    }

    @Test
    void conservaUnicodeYEspaciosSinRecortarNiNormalizar() {
        char[] clave = "  Mi contraseña con agua 🚰  ".toCharArray();
        String hash = claves.proteger(clave);
        assertTrue(claves.coincide(clave, hash));
        assertFalse(claves.coincide(new String(clave).strip().toCharArray(), hash));
        String descompuesta = "Mi contrasen\u0303a con agua";
        assertFalse(claves.coincide(descompuesta.toCharArray(), hash));
    }

    @Test
    void admiteLosLimitesContandoPuntosUnicode() {
        char[] minima = "a".repeat(15).toCharArray();
        char[] maxima = "🚰".repeat(128).toCharArray();
        assertTrue(claves.coincide(minima, claves.proteger(minima)));
        assertTrue(claves.coincide(maxima, claves.proteger(maxima)));
    }

    @Test
    void rechazaClavesFueraDeLimitesControlesYUnicodeInvalido() {
        for (char[] clave : Arrays.asList(null, new char[0], "a".repeat(14).toCharArray(),
                "a".repeat(129).toCharArray(), "🚰".repeat(129).toCharArray(),
                "Frase privada\nprohibida".toCharArray(),
                ("a".repeat(15) + '\uD800').toCharArray())) {
            var error = assertThrows(IllegalArgumentException.class, () -> claves.proteger(clave));
            assertFalse(error.getMessage().contains("Frase privada"));
        }
    }

    @Test
    void hashesDesconocidosOConCostosArbitrariosNoSeEjecutan() {
        char[] clave = "Una frase ficticia de prueba".toCharArray();
        for (String hash : Arrays.asList(null, "", "texto-sin-hash", "$2a$10$incompatible",
                "$argon2id$v=19$m=2147483647,t=999999,p=255$sal$hash")) {
            assertFalse(claves.coincide(clave, hash));
        }
        assertFalse(claves.coincide(null, claves.proteger(clave)));
    }

    @Test
    void usuarioEsCanonicoSinDistinguirMayusculas() {
        assertEquals(new UsuarioAdministrador("ADMIN.Nivora"), new UsuarioAdministrador("admin.nivora"));
        assertEquals("admin_01", new UsuarioAdministrador("ADMIN_01").valor());
    }

    @Test
    void claveCortaNecesitaOptInYSeVerificaDespuesConLaPoliticaNormal() {
        char[] corta = "314159".toCharArray();
        assertThrows(IllegalArgumentException.class, () -> claves.proteger(corta));
        var inicial = new ProtectorClavesArgon2(true);
        assertThrows(IllegalArgumentException.class, () -> inicial.proteger("12345".toCharArray()));
        String hash = inicial.proteger(corta);
        assertTrue(claves.coincide(corta, hash));
        assertFalse(claves.coincide("271828".toCharArray(), hash));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "ab", "1admin", " admin", "admin ", "administradór", "admin/otro", "admin@correo", "administrador_demasiado_largo_para_los_cincuenta_caracteres"})
    void rechazaUsuariosAmbiguosOInvalidos(String usuario) {
        assertThrows(IllegalArgumentException.class, () -> new UsuarioAdministrador(usuario));
    }
}
