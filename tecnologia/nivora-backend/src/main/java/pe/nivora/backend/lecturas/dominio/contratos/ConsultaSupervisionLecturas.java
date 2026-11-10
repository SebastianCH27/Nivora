package pe.nivora.backend.lecturas.dominio.contratos;

import java.time.Instant;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.SupervisionLecturas;

public interface ConsultaSupervisionLecturas {
    /** Vacio solo si el suministro no esta registrado en Nivora. */
    Optional<SupervisionLecturas> consultar(String fuente, CodigoSuministro suministro,
            Instant ahora);
}
