package pe.nivora.backend.acceso.dominio.contratos;

/** La consulta no pudo confirmar los datos del documento. */
public final class FuenteIdentidadNoDisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Motivo {
        SIN_CONFIGURACION,
        CREDENCIAL_RECHAZADA,
        LIMITE_CONSULTAS,
        RESULTADO_NO_CONCLUYENTE,
        RESPUESTA_INCOMPATIBLE,
        FUENTE_NO_DISPONIBLE
    }

    private final Motivo motivo;

    public FuenteIdentidadNoDisponibleException(Motivo motivo) {
        super("No se pudo confirmar la consulta de identidad. Motivo " + motivo + ".");
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
