package pe.nivora.backend.acceso.dominio.contratos;

public final class AccesoConsultaLimitadoException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final long segundos;

    public AccesoConsultaLimitadoException(long segundos) {
        super("Limite de intentos de consulta alcanzado.");
        this.segundos = segundos;
    }

    public long segundos() {
        return segundos;
    }
}
