package pe.nivora.backend.integracion.dominio;

import java.time.Instant;

public record IncidenciaIntegracion(Origen origen, String fuente, String referencia, String idLectura,
        String motivo, Estado estado, Instant primeraRecepcion, Instant ultimaRecepcion, long repeticiones,
        Instant ultimaResolucion) {
    public enum Origen { RECHAZO_LECTURA, RECUPERACION_LECTURAS, SINCRONIZACION_RECIBOS }

    public enum Estado { POR_REVISAR, ABIERTA, RESUELTA }

    public Cursor cursor() {
        return new Cursor(primeraRecepcion, origen, fuente, referencia);
    }

    public record Cursor(Instant primeraRecepcion, Origen origen, String fuente, String referencia) {}
}
