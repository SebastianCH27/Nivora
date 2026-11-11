package pe.nivora.backend.lecturas.dominio.contratos;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.EstadoRecuperacion;
import pe.nivora.backend.lecturas.dominio.EstadoSincronizacion;
import pe.nivora.backend.lecturas.dominio.LecturaAcumulada;

public interface ConsultaLecturasPeriodo {

    /** Obtiene lecturas ordenadas y estado de actualizacion de la misma vista local. */
    Optional<Datos> consultar(String fuente, CodigoSuministro suministro, Instant desde, Instant hasta);

    record Datos(List<LecturaAcumulada> lecturas, Instant ultimaAnterior,
            EstadoRecuperacion recuperacion, Instant corteConfirmado,
            EstadoSincronizacion sincronizacion) {
        public Datos {
            lecturas = List.copyOf(lecturas);
        }
    }
}
