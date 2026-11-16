package pe.nivora.backend.notificaciones.dominio;

import java.net.URI;
import java.util.Base64;
import java.util.Set;

import org.bouncycastle.jce.ECNamedCurveTable;

public record SuscripcionWebPush(String endpoint, String p256dh, String auth) {
    private static final Set<String> PROVEEDORES = Set.of("fcm.googleapis.com",
        "updates.push.services.mozilla.com", "push.services.mozilla.com", "web.push.apple.com");

    public SuscripcionWebPush {
        destino(endpoint);
        validarClavePublica(p256dh);
        decodificar(auth, 16);
    }

    public static URI destino(String endpoint) {
        if (endpoint == null || endpoint.length() > 2048) throw invalida();
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException error) {
            throw invalida();
        }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || !PROVEEDORES.contains(uri.getHost())
                || uri.getRawUserInfo() != null || uri.getRawFragment() != null || uri.getPort() != -1
                || !endpoint.equals(uri.toASCIIString()) || !uri.getRawPath().matches("/[A-Za-z0-9_:/%+.-]+")
                || !rutaAdmitida(uri)) {
            throw invalida();
        }
        return uri;
    }

    private static boolean rutaAdmitida(URI uri) {
        String ruta = uri.getRawPath();
        if (uri.getRawQuery() != null || ruta.contains("..") || ruta.contains("%")) return false;
        return switch (uri.getHost()) {
            case "fcm.googleapis.com" -> ruta.matches("/(?:fcm/send|wp)/[A-Za-z0-9_:+.-]+");
            case "updates.push.services.mozilla.com", "push.services.mozilla.com" ->
                ruta.matches("/wpush/v[12]/[A-Za-z0-9_.-]+");
            case "web.push.apple.com" -> ruta.matches("/[A-Za-z0-9_.-]+");
            default -> false;
        };
    }

    public static void validarClavePublica(String texto) {
        byte[] clave = decodificar(texto, 65);
        if (clave[0] != 4) throw invalida();
        try {
            ECNamedCurveTable.getParameterSpec("prime256v1").getCurve().decodePoint(clave);
        } catch (IllegalArgumentException error) {
            throw invalida();
        }
    }

    public static byte[] decodificar(String texto, int longitud) {
        if (texto == null || texto.length() != (longitud * 8 + 5) / 6) throw invalida();
        try {
            byte[] clave = Base64.getUrlDecoder().decode(texto);
            if (clave.length == longitud && Base64.getUrlEncoder().withoutPadding().encodeToString(clave).equals(texto)) {
                return clave;
            }
        } catch (IllegalArgumentException error) {
            throw invalida();
        }
        throw invalida();
    }

    private static IllegalArgumentException invalida() {
        return new IllegalArgumentException("Suscripcion Web Push invalida o proveedor no admitido.");
    }

    @Override
    public String toString() {
        return "SuscripcionWebPush[datos ocultos]";
    }
}
