package pe.nivora.backend.lecturas.dominio.contratos;

import java.time.Instant;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.EstadoRecuperacion;
import pe.nivora.backend.lecturas.dominio.LoteLecturas;
import pe.nivora.backend.lecturas.dominio.ProgresoRecuperacion;
import pe.nivora.backend.lecturas.dominio.ResultadoIncorporacion;

public interface RepositorioRecuperacionLecturas {
    ProgresoRecuperacion consultar(String fuente, CodigoSuministro suministro);

    /** Incorpora lecturas, incidencias y avance dentro de una sola transaccion. */
    Paso confirmar(ProgresoRecuperacion esperado, LoteLecturas lote, Instant ahora);

    ProgresoRecuperacion registrarFallo(ProgresoRecuperacion esperado, EstadoRecuperacion fallo, Instant ahora);

    /** Reinicia solo el recorrido; conserva lecturas e incidencias. */
    ProgresoRecuperacion reiniciar(ProgresoRecuperacion esperado, Instant ahora);

    record Paso(ProgresoRecuperacion progreso, ResultadoIncorporacion incorporacion) {}
}
