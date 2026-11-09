package pe.nivora.backend.lecturas.dominio.contratos;

public class SuministroNoRegistradoException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SuministroNoRegistradoException() {
        super("El suministro debe estar registrado en Nivora antes de incorporar lecturas.");
    }
}
