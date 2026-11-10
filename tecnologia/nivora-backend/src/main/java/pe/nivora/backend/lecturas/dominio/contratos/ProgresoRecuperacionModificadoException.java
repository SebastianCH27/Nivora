package pe.nivora.backend.lecturas.dominio.contratos;

public class ProgresoRecuperacionModificadoException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ProgresoRecuperacionModificadoException() {
        super("El progreso cambio durante la consulta; vuelve a consultar su estado antes de continuar.");
    }
}
