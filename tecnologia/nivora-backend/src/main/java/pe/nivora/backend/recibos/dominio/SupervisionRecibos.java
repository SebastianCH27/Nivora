package pe.nivora.backend.recibos.dominio;

import java.time.Instant;
import java.time.YearMonth;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.recibos.dominio.RepositorioSincronizacionRecibos.Estado;

public record SupervisionRecibos(CodigoSuministro suministro, Instant consultadoEn,
        Almacenamiento almacenamiento, Programacion programacion) {

    public record Almacenamiento(long recibosGuardados, YearMonth ultimoPeriodo, Instant ultimaRecepcion) {}

    public record Programacion(Estado ultimoResultado, int fallosConsecutivos,
            Instant ultimoIntento, Instant ultimoExito, Instant proximoIntento, Instant reservaHasta,
            boolean reservaVigente, boolean reservaVencida) {}
}
