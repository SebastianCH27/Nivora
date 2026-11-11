package pe.nivora.backend.acceso.dominio.contratos;

import pe.nivora.backend.acceso.dominio.Dni;
import pe.nivora.backend.acceso.dominio.IdentidadConsultada;

/** Consulta los datos asociados al DNI en una fuente externa. */
public interface FuenteIdentidad {

    /**
     * @return los datos encontrados para el mismo DNI solicitado
     * @throws FuenteIdentidadNoDisponibleException si la consulta no puede confirmarse
     */
    IdentidadConsultada consultar(Dni dni);
}
