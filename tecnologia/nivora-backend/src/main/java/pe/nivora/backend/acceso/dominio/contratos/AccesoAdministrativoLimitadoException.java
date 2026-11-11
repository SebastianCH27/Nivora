package pe.nivora.backend.acceso.dominio.contratos;

public final class AccesoAdministrativoLimitadoException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final long segundos;
    public AccesoAdministrativoLimitadoException(long segundos) {
        super("Se alcanzo el limite de intentos administrativos.");
        this.segundos = Math.max(1, segundos);
    }
    public long segundos() { return segundos; }
}
