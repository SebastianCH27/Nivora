package pe.nivora.backend.lecturas.dominio;

public enum MotivoRechazoLectura {
    ID_EN_CONFLICTO,
    MEDICION_CON_OTRO_ID,
    FECHA_FUERA_DE_RANGO,
    FECHA_PRECISION_NO_SOPORTADA,
    FECHA_POSTERIOR_A_RECEPCION,
    CONTADOR_INCONSISTENTE
}
