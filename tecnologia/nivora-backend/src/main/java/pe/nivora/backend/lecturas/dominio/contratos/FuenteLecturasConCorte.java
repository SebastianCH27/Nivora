package pe.nivora.backend.lecturas.dominio.contratos;

import java.util.Optional;
import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.ContextoLecturas;
import pe.nivora.backend.lecturas.dominio.LoteLecturas;

public interface FuenteLecturasConCorte {
    Optional<ContextoLecturas> consultarContexto(CodigoSuministro codigo);
    Optional<LoteLecturas> consultar(CodigoSuministro codigo, String cursor,
        int limite, ContextoLecturas contexto);
}
