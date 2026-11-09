package pe.nivora.backend.lecturas.aplicacion;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.ResultadoIncorporacion;
import pe.nivora.backend.lecturas.dominio.contratos.FuenteLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioLecturas;

public final class ImportarPaginaLecturas {
    private final FuenteLecturas fuente;
    private final RepositorioLecturas repositorio;
    private final Clock reloj;

    public ImportarPaginaLecturas(FuenteLecturas fuente, RepositorioLecturas repositorio, Clock reloj) {
        this.fuente = Objects.requireNonNull(fuente);
        this.repositorio = Objects.requireNonNull(repositorio);
        this.reloj = Objects.requireNonNull(reloj);
    }

    public Optional<Resultado> ejecutar(CodigoSuministro codigo, String cursor, int limite) {
        // La llamada HTTP queda fuera de la transaccion de almacenamiento.
        return fuente.consultar(codigo, cursor, limite).map(lote -> {
            if (!codigo.equals(lote.suministro())) {
                throw new IllegalArgumentException("El lote no corresponde al suministro solicitado.");
            }
            return new Resultado(repositorio.incorporar(lote, reloj.instant()),
                lote.siguienteCursor(), lote.hayMas());
        });
    }

    /** El cursor es informativo; no es un punto de sincronizacion confirmado. */
    public record Resultado(ResultadoIncorporacion incorporacion,
                            String siguienteCursor, boolean hayMas) {}
}
