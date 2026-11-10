package pe.nivora.backend.lecturas.aplicacion;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.SupervisionLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.ConsultaSupervisionLecturas;
import pe.nivora.backend.lecturas.dominio.contratos.SuministroNoRegistradoException;

public final class ConsultarSupervisionLecturas {
    private final ConsultaSupervisionLecturas consulta;
    private final Clock reloj;

    public ConsultarSupervisionLecturas(ConsultaSupervisionLecturas consulta, Clock reloj) {
        this.consulta = Objects.requireNonNull(consulta);
        this.reloj = Objects.requireNonNull(reloj);
    }

    public SupervisionLecturas ejecutar(String codigo) {
        var suministro = new CodigoSuministro(codigo);
        return consulta.consultar("empresa-agua", suministro,
                reloj.instant().truncatedTo(ChronoUnit.MICROS))
            .orElseThrow(SuministroNoRegistradoException::new);
    }
}
