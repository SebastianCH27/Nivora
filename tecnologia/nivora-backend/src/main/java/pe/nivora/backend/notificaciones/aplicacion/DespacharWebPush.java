package pe.nivora.backend.notificaciones.aplicacion;

import java.time.Clock;
import java.time.Duration;

import pe.nivora.backend.compartido.dominio.ReintentoExponencial;
import pe.nivora.backend.notificaciones.dominio.EmisorWebPush;
import pe.nivora.backend.notificaciones.dominio.RepositorioWebPush;
import pe.nivora.backend.notificaciones.dominio.RepositorioWebPush.Estado;

public class DespacharWebPush {
    private static final int MAXIMO_POR_CICLO = 4;
    private static final int MAXIMO_INTENTOS = 6;
    private final RepositorioWebPush repositorio;
    private final EmisorWebPush emisor;
    private final Clock reloj;

    public DespacharWebPush(RepositorioWebPush repositorio, EmisorWebPush emisor, Clock reloj) {
        this.repositorio = repositorio;
        this.emisor = emisor;
        this.reloj = reloj;
    }

    public void ejecutar() throws InterruptedException {
        repositorio.encolar(reloj.instant());
        for (int i = 0; i < MAXIMO_POR_CICLO && !Thread.currentThread().isInterrupted(); i++) {
            var siguiente = repositorio.reservar(reloj.instant());
            if (siguiente.isEmpty()) return;
            var reserva = siguiente.orElseThrow();
            if (repositorio.vigente(reserva, reloj.instant())) enviar(reserva);
        }
    }

    private void enviar(RepositorioWebPush.Reserva reserva) throws InterruptedException {
        var respuesta = emisor.enviar(reserva, reloj.instant());
        var ahora = reloj.instant();
        Estado estado = switch (respuesta.resultado()) {
            case ACEPTADA -> Estado.ACEPTADA;
            case EXPIRADA -> Estado.EXPIRADA;
            case RECHAZADA -> Estado.FALLIDA;
            case TEMPORAL -> reserva.intentos() + 1 < MAXIMO_INTENTOS ? Estado.PENDIENTE : Estado.FALLIDA;
        };
        var siguiente = ahora;
        if (estado == Estado.PENDIENTE) {
            siguiente = ReintentoExponencial.calcular(reserva.intentos(), ahora,
                Duration.ofSeconds(30), Duration.ofHours(1)).proximoIntento();
            var desde = respuesta.reintentarDesde();
            if (desde != null && desde.isAfter(siguiente)) siguiente = desde;
        }
        repositorio.finalizar(reserva, estado, siguiente, ahora);
    }
}
