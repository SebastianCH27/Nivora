package pe.nivora.backend.notificaciones.infraestructura;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

import nl.martijndwars.webpush.AbstractPushService;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;
import tools.jackson.databind.json.JsonMapper;

import pe.nivora.backend.acceso.dominio.TokenOpaco;
import pe.nivora.backend.notificaciones.dominio.EmisorWebPush;
import pe.nivora.backend.notificaciones.dominio.RepositorioWebPush.Reserva;
import pe.nivora.backend.notificaciones.dominio.SuscripcionWebPush;

public class EmisorWebPushHttp implements EmisorWebPush {
    private final String publica;
    private final Cifrado cifrado;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build();

    public EmisorWebPushHttp(String publica, String privada, String contacto, JsonMapper json) {
        if (!contacto.matches("mailto:[^@\\s]+@[^@\\s]+")) {
            throw new IllegalArgumentException("Configura un contacto VAPID con mailto:.");
        }
        SuscripcionWebPush.validarClavePublica(publica);
        SuscripcionWebPush.decodificar(privada, 32);
        if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider());
        try {
            cifrado = new Cifrado(publica, privada, contacto);
            if (!Utils.verifyKeyPair(cifrado.getPrivateKey(), cifrado.getPublicKey())) {
                throw new IllegalArgumentException("Las claves VAPID deben pertenecer al mismo par.");
            }
        } catch (GeneralSecurityException error) {
            throw new IllegalArgumentException("Las claves VAPID no son validas.");
        }
        this.publica = publica;
        this.json = json;
    }

    @Override
    public String clavePublica() {
        return publica;
    }

    @Override
    public Respuesta enviar(Reserva reserva, Instant ahora) throws InterruptedException {
        try {
            var respuesta = http.send(preparar(reserva), HttpResponse.BodyHandlers.ofInputStream());
            try (var cuerpo = respuesta.body()) {
                return interpretar(respuesta.statusCode(), respuesta.headers().firstValue("Retry-After").orElse(""), ahora);
            }
        } catch (IOException error) {
            return new Respuesta(Resultado.TEMPORAL, null);
        } catch (GeneralSecurityException | JoseException error) {
            return new Respuesta(Resultado.RECHAZADA, null);
        }
    }

    HttpRequest preparar(Reserva reserva) throws GeneralSecurityException, IOException, JoseException {
        var suscripcion = reserva.suscripcion();
        var tipo = reserva.aviso().tipo();
        var periodo = reserva.aviso().periodo().toString();
        String id = tipo.name() + "-" + periodo;
        String mensaje = switch (tipo) {
            case ALERTA_CONSUMO -> "El consumo de " + periodo + " superó el total del mes anterior.";
            case RECIBO_DISPONIBLE -> "Recibo de " + periodo + " disponible. Vence el " + reserva.vencimiento() + ".";
        };
        var aviso = Notification.builder().endpoint(suscripcion.endpoint()).userPublicKey(suscripcion.p256dh())
            .userAuth(suscripcion.auth()).ttl(86400).topic(TokenOpaco.huellaDe(id).substring(0, 32))
            .payload(json.writeValueAsBytes(Map.of("id", id, "titulo", "Nivora", "mensaje", mensaje))).build();
        var preparado = cifrado.cifrar(aviso);
        var peticion = HttpRequest.newBuilder(SuscripcionWebPush.destino(preparado.getUrl()))
            .timeout(Duration.ofSeconds(8)).POST(HttpRequest.BodyPublishers.ofByteArray(preparado.getBody()));
        preparado.getHeaders().forEach(peticion::header);
        return peticion.build();
    }

    static Respuesta interpretar(int codigo, String retryAfter, Instant ahora) {
        if (codigo >= 200 && codigo < 300) return new Respuesta(Resultado.ACEPTADA, null);
        if (codigo == 404 || codigo == 410) return new Respuesta(Resultado.EXPIRADA, null);
        if (codigo == 408 || codigo == 429 || codigo >= 500) {
            return new Respuesta(Resultado.TEMPORAL, reintentarDesde(retryAfter, ahora));
        }
        return new Respuesta(Resultado.RECHAZADA, null);
    }

    private static Instant reintentarDesde(String valor, Instant ahora) {
        if (valor.isBlank()) return null;
        try {
            long segundos = Long.parseLong(valor);
            return segundos >= 0 ? ahora.plusSeconds(Math.min(segundos, 86400)) : null;
        } catch (NumberFormatException error) {
            try {
                var fecha = ZonedDateTime.parse(valor, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                var limite = ahora.plusSeconds(86400);
                return fecha.isAfter(limite) ? limite : fecha;
            } catch (DateTimeParseException invalida) {
                return null;
            }
        }
    }

    private static class Cifrado extends AbstractPushService<Cifrado> {
        Cifrado(String publica, String privada, String contacto) throws GeneralSecurityException {
            super(publica, privada, contacto);
        }

        nl.martijndwars.webpush.HttpRequest cifrar(Notification aviso)
                throws GeneralSecurityException, IOException, JoseException {
            return prepareRequest(aviso, Encoding.AES128GCM);
        }
    }
}
