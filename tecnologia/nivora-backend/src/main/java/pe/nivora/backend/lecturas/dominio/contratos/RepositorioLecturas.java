package pe.nivora.backend.lecturas.dominio.contratos;

import java.time.Instant;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.LecturaAcumulada;
import pe.nivora.backend.lecturas.dominio.ContextoLecturas;
import pe.nivora.backend.lecturas.dominio.LoteLecturas;
import pe.nivora.backend.lecturas.dominio.ResultadoIncorporacion;

public interface RepositorioLecturas {
    /**
     * Incorpora el lote y sus incidencias de forma atomica, sin reemplazar lecturas aceptadas.
     * No persiste ni confirma el cursor del proveedor.
     */
    ResultadoIncorporacion incorporar(LoteLecturas lote, Instant recibidaEn);

    default ResultadoIncorporacion incorporar(LoteLecturas lote, Instant recibidaEn,
            ContextoLecturas contexto) {
        if (contexto != null) {
            throw new UnsupportedOperationException("El repositorio no admite contexto de lecturas.");
        }
        return incorporar(lote, recibidaEn);
    }

    Optional<LecturaAcumulada> buscar(String fuente, String idLectura);

    long contar(CodigoSuministro codigo);

    long contarIncidencias(CodigoSuministro codigo);
}
