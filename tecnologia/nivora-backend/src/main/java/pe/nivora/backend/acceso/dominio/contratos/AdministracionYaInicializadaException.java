package pe.nivora.backend.acceso.dominio.contratos;

public final class AdministracionYaInicializadaException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public AdministracionYaInicializadaException() {
        super("La administracion ya fue inicializada; no se modifica ninguna cuenta.");
    }
}
