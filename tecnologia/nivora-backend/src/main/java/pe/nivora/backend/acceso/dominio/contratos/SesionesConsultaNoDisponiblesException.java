package pe.nivora.backend.acceso.dominio.contratos;

public final class SesionesConsultaNoDisponiblesException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SesionesConsultaNoDisponiblesException() {
        super("No se pudo completar la operacion de sesion de consulta.");
    }
}
