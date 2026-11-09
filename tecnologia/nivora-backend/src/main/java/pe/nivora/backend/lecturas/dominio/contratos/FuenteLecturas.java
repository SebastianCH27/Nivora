package pe.nivora.backend.lecturas.dominio.contratos;

import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.LoteLecturas;

public interface FuenteLecturas {
    /**
     * Consulta una pagina externa sin almacenar lecturas ni confirmar el cursor.
     * El adaptador interpreta el cursor y valida sus limites de paginacion.
     * @return vacio solo si la fuente confirma que el suministro no existe;
     *         una pagina sin lecturas es un lote presente
     * @throws FuenteLecturasNoDisponibleException si falla la fuente o sus datos no son utilizables
     * @throws IllegalArgumentException si el cursor o el limite solicitado son invalidos
     */
    Optional<LoteLecturas> consultar(CodigoSuministro codigo, String cursor, int limite);
}
