package pe.nivora.backend.notificaciones.dominio;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

public record Notificacion(Clave clave, Instant registradaEn, Instant leidaEn, Consumo consumo, Recibo recibo) {
    public enum Tipo { ALERTA_CONSUMO, RECIBO_DISPONIBLE }

    public record Clave(Tipo tipo, YearMonth periodo) {}

    public record Cursor(Instant registradaEn, Clave clave) {}

    public record Consumo(long actualLitros, long anteriorLitros) {}

    public record Recibo(String numeroRecibo, LocalDate fechaDisponibilidad, LocalDate fechaVencimiento) {}
}
