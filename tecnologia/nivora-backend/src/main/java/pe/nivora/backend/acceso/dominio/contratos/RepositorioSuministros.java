package pe.nivora.backend.acceso.dominio.contratos;

import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.SuministroVerificado;

/** Almacenamiento propio de suministros aceptados por Nivora. */
public interface RepositorioSuministros {

    /**
     * Guarda una verificacion sin duplicar el codigo. Una verificacion anterior
     * a la almacenada no debe reemplazarla. A igual fecha se acepta la escritura.
     * @throws RepositorioSuministrosNoDisponibleException si falla la escritura
     */
    void guardar(SuministroVerificado verificado);

    /**
     * Consulta solamente el almacenamiento propio, sin recurrir a la fuente externa.
     * Vacio significa que no hay datos guardados, no que la fuente confirme ausencia.
     * @throws RepositorioSuministrosNoDisponibleException si falla la consulta
     */
    Optional<SuministroVerificado> buscar(CodigoSuministro codigo);
}
