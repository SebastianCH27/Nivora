package pe.nivora.backend.integracion.aplicacion;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.integracion.dominio.RepositorioReintentosIntegracion;
import pe.nivora.backend.integracion.dominio.RepositorioReintentosIntegracion.Proceso;
import pe.nivora.backend.integracion.dominio.RepositorioReintentosIntegracion.Solicitud;

public class SolicitarReintentoIntegracion {
    private final RepositorioReintentosIntegracion reintentos;
    private final Clock reloj;

    public SolicitarReintentoIntegracion(RepositorioReintentosIntegracion reintentos, Clock reloj) {
        this.reintentos = reintentos;
        this.reloj = reloj;
    }

    public Solicitud ejecutar(CodigoSuministro suministro, Proceso proceso) {
        return reintentos.programar(suministro, proceso, reloj.instant().truncatedTo(ChronoUnit.MICROS));
    }
}
