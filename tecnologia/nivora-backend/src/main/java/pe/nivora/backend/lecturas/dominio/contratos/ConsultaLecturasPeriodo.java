package pe.nivora.backend.lecturas.dominio.contratos;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.EstadoRecuperacion;
import pe.nivora.backend.lecturas.dominio.EstadoSincronizacion;
import pe.nivora.backend.lecturas.dominio.Lectura;

public interface ConsultaLecturasPeriodo {

    /** Obtiene lecturas ordenadas y estado de actualizacion de la misma vista local. */
    Optional<Datos> consultar(String fuente, CodigoSuministro suministro, Instant desde, Instant hasta);

    /** Incluye intervalos que cruzan el periodo, sin usar mediciones posteriores al reloj observado. */
    default Optional<Datos> consultar(String fuente, CodigoSuministro suministro, Instant desde,
            Instant hasta, Instant observadoHasta) {
        return consultar(fuente, suministro, desde, hasta);
    }

    record Datos(List<? extends Lectura> lecturas, Instant ultimaAnterior,
            EstadoRecuperacion recuperacion, Instant corteConfirmado,
            EstadoSincronizacion sincronizacion) {
        public Datos {
            lecturas = List.copyOf(lecturas);
        }
    }
}
