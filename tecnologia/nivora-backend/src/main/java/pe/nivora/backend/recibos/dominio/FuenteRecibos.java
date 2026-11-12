package pe.nivora.backend.recibos.dominio;

import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;

public interface FuenteRecibos {
    /** Vacio solo cuando la fuente confirma que el suministro no existe. */
    Optional<RecibosEmitidos> consultar(CodigoSuministro suministro, int limite);
}
