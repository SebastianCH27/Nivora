package pe.nivora.backend.recibos.aplicacion;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.recibos.dominio.ConsultaSupervisionRecibos;
import pe.nivora.backend.recibos.dominio.SupervisionRecibos;

public class ConsultarSupervisionRecibos {
    private final ConsultaSupervisionRecibos consulta;
    private final Clock reloj;

    public ConsultarSupervisionRecibos(ConsultaSupervisionRecibos consulta, Clock reloj) {
        this.consulta = consulta;
        this.reloj = reloj;
    }

    public Optional<SupervisionRecibos> ejecutar(String codigo) {
        return consulta.consultar(new CodigoSuministro(codigo), reloj.instant().truncatedTo(ChronoUnit.MICROS));
    }
}
