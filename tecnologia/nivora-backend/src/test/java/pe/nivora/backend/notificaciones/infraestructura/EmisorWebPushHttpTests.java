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
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import pe.nivora.backend.notificaciones.dominio.EmisorWebPush;
import pe.nivora.backend.notificaciones.dominio.Notificacion;
import pe.nivora.backend.notificaciones.dominio.RepositorioWebPush;
import pe.nivora.backend.notificaciones.dominio.SuscripcionWebPush;

import static org.junit.jupiter.api.Assertions.*;

class EmisorWebPushHttpTests {
    @Test
    void elDestinatarioPuedeDescifrarElCuerpoYUnaClaveAuthDistintaLoRechaza() throws Exception {
        var vapid = generarPar();
        var destinatario = generarPar();
        byte[] auth = new byte[16]; new java.security.SecureRandom().nextBytes(auth);
        var suscripcion = new SuscripcionWebPush("https://fcm.googleapis.com/wp/cifrado",
            publica(destinatario), base64(auth));
        var emisor = new EmisorWebPushHttp(publica(vapid), base64(HexFormat.of().parseHex("%064x"
            .formatted(((ECPrivateKey) vapid.getPrivate()).getS()))), "mailto:prueba@example.test", new JsonMapper());
        var reserva = new RepositorioWebPush.Reserva("id", "token",
            new Notificacion.Clave(Notificacion.Tipo.RECIBO_DISPONIBLE, YearMonth.of(2026, 10)),
            0, LocalDate.of(2026, 11, 8), suscripcion);
        var preparado = emisor.preparar(reserva);
        var bytes = new java.util.concurrent.CompletableFuture<byte[]>();
        preparado.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
            final java.io.ByteArrayOutputStream salida = new java.io.ByteArrayOutputStream();
            public void onSubscribe(java.util.concurrent.Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(java.nio.ByteBuffer b) { byte[] bloque = new byte[b.remaining()]; b.get(bloque); salida.writeBytes(bloque); }
            public void onError(Throwable error) { bytes.completeExceptionally(error); }
            public void onComplete() { bytes.complete(salida.toByteArray()); }
        });
        byte[] cuerpo = bytes.get(3, java.util.concurrent.TimeUnit.SECONDS);
        byte[] claro = descifrar(cuerpo, destinatario, auth);
        assertEquals(2, claro[claro.length - 1]); // Delimitador del ultimo registro, RFC 8188.
        var aviso = new JsonMapper().readTree(java.util.Arrays.copyOf(claro, claro.length - 1));
        assertEquals("RECIBO_DISPONIBLE-2026-10", aviso.path("id").stringValue());
        assertEquals("Nivora", aviso.path("titulo").stringValue());
        assertTrue(aviso.path("mensaje").stringValue().contains("2026-11-08"));
        auth[0] ^= 1;
        assertThrows(javax.crypto.AEADBadTagException.class, () -> descifrar(cuerpo, destinatario, auth));
    }

    /** Receptor independiente de la biblioteca del emisor, segun RFC 8291 seccion 3. */
    private static byte[] descifrar(byte[] cuerpo, KeyPair receptor, byte[] auth) throws Exception {
        var encabezado = java.nio.ByteBuffer.wrap(cuerpo);
        byte[] salt = new byte[16]; encabezado.get(salt);
        int tamanoRegistro = encabezado.getInt();
        assertTrue(tamanoRegistro >= 18);
        int longitudClave = Byte.toUnsignedInt(encabezado.get()); assertEquals(65, longitudClave);
        byte[] emisor = new byte[longitudClave]; encabezado.get(emisor); assertEquals(4, emisor[0]);
        var punto = new java.security.spec.ECPoint(new java.math.BigInteger(1, java.util.Arrays.copyOfRange(emisor, 1, 33)),
            new java.math.BigInteger(1, java.util.Arrays.copyOfRange(emisor, 33, 65)));
        var publica = java.security.KeyFactory.getInstance("EC").generatePublic(new java.security.spec.ECPublicKeySpec(
            punto, ((ECPublicKey) receptor.getPublic()).getParams()));
        var acuerdo = javax.crypto.KeyAgreement.getInstance("ECDH");
        acuerdo.init(receptor.getPrivate()); acuerdo.doPhase(publica, true);
        byte[] prkClave = hmac(auth, acuerdo.generateSecret());
        var informacion = new java.io.ByteArrayOutputStream();
        informacion.writeBytes("WebPush: info\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        informacion.writeBytes(Base64.getUrlDecoder().decode(publica(receptor))); informacion.writeBytes(emisor);
        byte[] ikm = expandir(prkClave, informacion.toByteArray(), 32);
        byte[] prk = hmac(salt, ikm);
        byte[] clave = expandir(prk, "Content-Encoding: aes128gcm\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 16);
        byte[] nonce = expandir(prk, "Content-Encoding: nonce\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 12);
        var aes = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(clave, "AES"),
            new javax.crypto.spec.GCMParameterSpec(128, nonce));
        return aes.doFinal(java.util.Arrays.copyOfRange(cuerpo, encabezado.position(), cuerpo.length));
    }

    private static byte[] hmac(byte[] clave, byte[] mensaje) throws Exception {
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(clave, "HmacSHA256")); return mac.doFinal(mensaje);
    }
    private static byte[] expandir(byte[] prk, byte[] informacion, int longitud) throws Exception {
        byte[] bloque = java.util.Arrays.copyOf(informacion, informacion.length + 1); bloque[bloque.length - 1] = 1;
        return java.util.Arrays.copyOf(hmac(prk, bloque), longitud);
    }

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

    @ParameterizedTest
    @ValueSource(ints = {201, 202, 400, 401, 403, 404, 410, 413, 429, 500, 503, 302})
    void elTransporteEnviaLaPeticionCifradaYClasificaLaRespuestaRealDelCliente(int estado) throws Exception {
        var par = generarPar();
        var cliente = new ClienteControlado(estado, false);
        var emisor = new EmisorWebPushHttp(publica(par), base64(HexFormat.of().parseHex("%064x"
            .formatted(((ECPrivateKey) par.getPrivate()).getS()))), "mailto:prueba@example.test", new JsonMapper(), cliente);
        var reserva = new RepositorioWebPush.Reserva("id", "reserva", new Notificacion.Clave(Notificacion.Tipo.RECIBO_DISPONIBLE,
            YearMonth.of(2026, 10)), 0, LocalDate.of(2026, 11, 8), suscripcion("destino"));
        var ahora = Instant.now();
        var esperado = switch (estado) {
            case 201, 202 -> new EmisorWebPush.Respuesta(EmisorWebPush.Resultado.ACEPTADA, null);
            case 404, 410 -> new EmisorWebPush.Respuesta(EmisorWebPush.Resultado.EXPIRADA, null);
            case 429, 500, 503 -> new EmisorWebPush.Respuesta(EmisorWebPush.Resultado.TEMPORAL, ahora.plusSeconds(60));
            default -> new EmisorWebPush.Respuesta(EmisorWebPush.Resultado.RECHAZADA, null);
        };
        assertEquals(esperado, emisor.enviar(reserva, ahora));
        assertEquals(1, cliente.intentos);
        var peticion = cliente.peticion;
        assertEquals("https", peticion.uri().getScheme());
        assertEquals("POST", peticion.method());
        assertEquals("86400", peticion.headers().firstValue("TTL").orElseThrow());
        assertEquals("aes128gcm", peticion.headers().firstValue("Content-Encoding").orElseThrow());
        assertTrue(peticion.bodyPublisher().orElseThrow().contentLength() > 100);
        String autorizacion = peticion.headers().firstValue("Authorization").orElseThrow();
        String token = autorizacion.substring("vapid t=".length(), autorizacion.indexOf(", k="));
        var firma = new org.jose4j.jws.JsonWebSignature();
        firma.setCompactSerialization(token); firma.setKey(par.getPublic());
        assertTrue(firma.verifySignature());
        var claims = new JsonMapper().readTree(firma.getPayload());
        assertEquals("https://fcm.googleapis.com", claims.path("aud").stringValue());
        assertEquals("mailto:prueba@example.test", claims.path("sub").stringValue());
        assertTrue(claims.path("exp").longValue() > ahora.getEpochSecond());
        assertTrue(claims.path("exp").longValue() <= ahora.plusSeconds(86401).getEpochSecond());
    }

    @Test void unTimeoutDeTransporteSeReintentaYUnaInterrupcionSePropaga() throws Exception {
        var par = generarPar();
        var cliente = new ClienteControlado(201, true);
        var emisor = new EmisorWebPushHttp(publica(par), base64(HexFormat.of().parseHex("%064x"
            .formatted(((ECPrivateKey) par.getPrivate()).getS()))), "mailto:prueba@example.test", new JsonMapper(), cliente);
        var reserva = new RepositorioWebPush.Reserva("id", "reserva", new Notificacion.Clave(Notificacion.Tipo.ALERTA_CONSUMO,
            YearMonth.of(2026, 10)), 0, null, suscripcion("destino"));
        assertEquals(EmisorWebPush.Resultado.TEMPORAL, emisor.enviar(reserva, Instant.now()).resultado());
        assertThrows(InterruptedException.class, () -> emisor.enviar(reserva, Instant.now()));
    }

    private static final class ClienteControlado extends HttpClient {
        final int estado; final boolean fallar; int intentos; HttpRequest peticion;
        ClienteControlado(int estado, boolean fallar) { this.estado = estado; this.fallar = fallar; }
        @Override @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest solicitud, HttpResponse.BodyHandler<T> manejador)
                throws java.io.IOException, InterruptedException {
            peticion = solicitud; intentos++;
            if (fallar) {
                if (intentos == 1) throw new HttpTimeoutException("fallo controlado");
                throw new InterruptedException("interrupcion controlada");
            }
            return (HttpResponse<T>) new HttpResponse<InputStream>() {
                public int statusCode() { return estado; }
                public HttpRequest request() { return solicitud; }
                public java.util.Optional<HttpResponse<InputStream>> previousResponse() { return java.util.Optional.empty(); }
                public java.net.http.HttpHeaders headers() { return java.net.http.HttpHeaders.of(
                    java.util.Map.of("Retry-After", java.util.List.of("60")), (a, b) -> true); }
                public InputStream body() { return new ByteArrayInputStream(new byte[0]); }
                public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return java.util.Optional.empty(); }
                public java.net.URI uri() { return solicitud.uri(); }
                public Version version() { return Version.HTTP_1_1; }
            };
        }
        public java.util.Optional<java.net.CookieHandler> cookieHandler() { return java.util.Optional.empty(); }
        public java.util.Optional<java.time.Duration> connectTimeout() { return java.util.Optional.empty(); }
        public Redirect followRedirects() { return Redirect.NEVER; }
        public java.util.Optional<java.net.ProxySelector> proxy() { return java.util.Optional.empty(); }
        public javax.net.ssl.SSLContext sslContext() { throw new UnsupportedOperationException(); }
        public javax.net.ssl.SSLParameters sslParameters() { return new javax.net.ssl.SSLParameters(); }
        public java.util.Optional<java.net.Authenticator> authenticator() { return java.util.Optional.empty(); }
        public Version version() { return Version.HTTP_1_1; }
        public java.util.Optional<java.util.concurrent.Executor> executor() { return java.util.Optional.empty(); }
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, HttpResponse.BodyHandler<T> h) {
            throw new UnsupportedOperationException();
        }
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, HttpResponse.BodyHandler<T> h,
                HttpResponse.PushPromiseHandler<T> p) { throw new UnsupportedOperationException(); }
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
