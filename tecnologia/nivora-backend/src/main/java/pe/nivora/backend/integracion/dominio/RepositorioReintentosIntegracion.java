package pe.nivora.backend.integracion.dominio;

import java.time.Instant;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;

public interface RepositorioReintentosIntegracion {
    Solicitud programar(CodigoSuministro suministro, Proceso proceso, Instant ahora);

    enum Proceso { LECTURAS, RECIBOS }

    enum Estado { PROGRAMADO, EN_CURSO, BLOQUEADO, SUMINISTRO_NO_REGISTRADO }

    record Solicitud(Estado estado, Instant proximoIntento) {}

    class NoDisponible extends RuntimeException {
        public NoDisponible(Throwable causa) {
            super("No se pudo programar el reintento de integracion.", causa);
        }
    }
}
