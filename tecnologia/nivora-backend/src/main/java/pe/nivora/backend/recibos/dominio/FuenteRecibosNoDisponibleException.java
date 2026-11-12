package pe.nivora.backend.recibos.dominio;

public class FuenteRecibosNoDisponibleException extends RuntimeException {
    public FuenteRecibosNoDisponibleException() {
        super("La Empresa de Agua no devolvio una respuesta de recibos utilizable.");
    }
}
