package pe.nivora.backend.notificaciones.dominio;

import java.time.Instant;

public interface EmisorWebPush {
    String clavePublica();

    Respuesta enviar(RepositorioWebPush.Reserva reserva, Instant ahora) throws InterruptedException;

    enum Resultado { ACEPTADA, EXPIRADA, TEMPORAL, RECHAZADA }

    record Respuesta(Resultado resultado, Instant reintentarDesde) {}
}
