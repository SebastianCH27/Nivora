package pe.nivora.backend.notificaciones.infraestructura;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Base64;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import pe.nivora.backend.notificaciones.dominio.EmisorWebPush;
import pe.nivora.backend.notificaciones.dominio.Notificacion;
import pe.nivora.backend.notificaciones.dominio.RepositorioWebPush;
import pe.nivora.backend.notificaciones.dominio.SuscripcionWebPush;

import static org.junit.jupiter.api.Assertions.*;

class EmisorWebPushHttpTests {
    @Test
    void preparaUnaPeticionCifradaConVapidYTopicEstable() throws Exception {
        var par = generarPar();
        var emisor = new EmisorWebPushHttp(publica(par), base64(HexFormat.of().parseHex("%064x"
            .formatted(((ECPrivateKey) par.getPrivate()).getS()))), "mailto:prueba@example.test", JsonMapper.builder().build());
        var suscripcion = suscripcion("prueba");
        var periodo = YearMonth.of(2026, 10);
        var reserva = new RepositorioWebPush.Reserva("id", "token",
            new Notificacion.Clave(Notificacion.Tipo.RECIBO_DISPONIBLE, periodo), 0,
            LocalDate.of(2026, 11, 8), suscripcion);
        var preparada = emisor.preparar(reserva);
        assertEquals("https://fcm.googleapis.com/wp/prueba", preparada.uri().toString());
        assertEquals("POST", preparada.method());
        assertEquals("aes128gcm", preparada.headers().firstValue("Content-Encoding").orElseThrow());
        assertTrue(preparada.headers().firstValue("Authorization").orElseThrow().startsWith("vapid t="));
        assertTrue(preparada.headers().firstValue("Authorization").orElseThrow().endsWith(", k=" + publica(par)));
        assertTrue(preparada.bodyPublisher().orElseThrow().contentLength() > 100);
        assertEquals(preparada.headers().firstValue("Topic"), emisor.preparar(reserva).headers().firstValue("Topic"));
    }

    @Test
    void clasificaAceptacionExpiracionYReintentos() {
        Instant ahora = Instant.parse("2026-11-16T12:00:00Z");
        assertEquals(EmisorWebPush.Resultado.ACEPTADA, EmisorWebPushHttp.interpretar(201, "", ahora).resultado());
        assertEquals(EmisorWebPush.Resultado.EXPIRADA, EmisorWebPushHttp.interpretar(410, "", ahora).resultado());
        assertEquals(EmisorWebPush.Resultado.RECHAZADA, EmisorWebPushHttp.interpretar(302, "", ahora).resultado());
        assertEquals(new EmisorWebPush.Respuesta(EmisorWebPush.Resultado.TEMPORAL, ahora.plusSeconds(90)),
            EmisorWebPushHttp.interpretar(429, "90", ahora));
        assertEquals(new EmisorWebPush.Respuesta(EmisorWebPush.Resultado.TEMPORAL, ahora.plusSeconds(120)),
            EmisorWebPushHttp.interpretar(503, "Mon, 16 Nov 2026 12:02:00 GMT", ahora));
    }

    @Test
    void exigeProveedorYRutaAdmitidosYClavesValidas() throws Exception {
        var valida = suscripcion("destino");
        for (String destino : new String[]{"http://fcm.googleapis.com/wp/destino",
                "https://fcm.googleapis.com@127.0.0.1/wp/destino", "https://fcm.googleapis.com.example.test/wp/destino",
                "https://fcm.googleapis.com:8443/wp/destino", "https://fcm.googleapis.com/metadata",
                "https://fcm.googleapis.com/wp/destino?destino=otro"}) {
            assertThrows(IllegalArgumentException.class, () -> new SuscripcionWebPush(destino, valida.p256dh(), valida.auth()));
        }
        assertThrows(IllegalArgumentException.class, () -> new SuscripcionWebPush(valida.endpoint(), valida.p256dh(), "corta"));
    }

    static SuscripcionWebPush suscripcion(String id) throws Exception {
        return new SuscripcionWebPush("https://fcm.googleapis.com/fcm/send/" + id, publica(generarPar()), base64(new byte[16]));
    }

    private static KeyPair generarPar() throws Exception {
        var generador = KeyPairGenerator.getInstance("EC");
        generador.initialize(new ECGenParameterSpec("secp256r1"));
        return generador.generateKeyPair();
    }

    private static String publica(KeyPair par) {
        var punto = ((ECPublicKey) par.getPublic()).getW();
        return base64(HexFormat.of().parseHex("04%064x%064x".formatted(punto.getAffineX(), punto.getAffineY())));
    }

    private static String base64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
