package pe.nivora.backend.tarifas.dominio;

/** Un fallo de transporte o contrato no confirma la ausencia de tarifas. */
public final class FuenteTarifasNoDisponibleException extends IllegalStateException {
    public FuenteTarifasNoDisponibleException() {
        super("La Empresa de Agua no devolvio un historial tarifario utilizable.");
    }
}
