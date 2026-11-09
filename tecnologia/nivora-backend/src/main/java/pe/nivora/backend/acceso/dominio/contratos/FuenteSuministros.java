package pe.nivora.backend.acceso.dominio.contratos;

import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.Suministro;

/** Contrato utilizado para comprobar el suministro durante un nuevo acceso. */
public interface FuenteSuministros {

    /**
     * Consulta la existencia del suministro en la fuente externa.
     *
     * @param codigo identificador valido del suministro
     * @return el suministro encontrado, o vacio si la fuente confirma que no existe
     * @throws FuenteSuministrosNoDisponibleException si la consulta no puede
     *         completarse o la fuente devuelve una respuesta no utilizable
     */
    Optional<Suministro> consultar(CodigoSuministro codigo);
}
