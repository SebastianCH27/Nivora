package pe.nivora.backend.lecturas.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.ConsumoPeriodo;
import pe.nivora.backend.lecturas.dominio.EstadoRecuperacion;
import pe.nivora.backend.lecturas.dominio.MesConsumo;
import pe.nivora.backend.lecturas.dominio.contratos.ConsultaLecturasPeriodo;
import pe.nivora.backend.lecturas.dominio.contratos.SuministroNoRegistradoException;

public class ConsultarConsumoActual {
    private static final String FUENTE = "empresa-agua";

    private final ConsultaLecturasPeriodo consulta;
    private final Clock reloj;

    public ConsultarConsumoActual(ConsultaLecturasPeriodo consulta, Clock reloj) {
        this.consulta = consulta;
        this.reloj = reloj;
    }

    public Resultado ejecutar(CodigoSuministro suministro) {
        Instant ahora = reloj.instant().truncatedTo(ChronoUnit.MICROS);
        var mes = MesConsumo.de(ahora);
        Instant inicio = mes.inicio();
        var datos = consulta.consultar(FUENTE, suministro, inicio, ahora)
            .orElseThrow(SuministroNoRegistradoException::new);
        Instant ultima = datos.lecturas().isEmpty() ? datos.ultimaAnterior()
            : datos.lecturas().getLast().fechaMedicion();
        Instant corte = datos.corteConfirmado();
        if (datos.recuperacion() != EstadoRecuperacion.RECORRIDO_COMPLETO
                || (corte != null && corte.isAfter(ahora))) {
            corte = null;
        }

        var consumo = ConsumoPeriodo.calcular(inicio, ultima, datos.lecturas());
        var actualizacion = new Actualizacion(estado(datos, ultima, corte), ultima, corte);
        return new Resultado(FUENTE, suministro.valor(), mes.valor().toString(), MesConsumo.ZONA.getId(),
            inicio, mes.fin(), ahora, consumo, actualizacion);
    }

    private static EstadoActualizacion estado(ConsultaLecturasPeriodo.Datos datos,
            Instant ultima, Instant corte) {
        if (datos.recuperacion() == EstadoRecuperacion.ERROR_FUENTE
                || datos.recuperacion() == EstadoRecuperacion.BLOQUEADO
                || datos.recuperacion() == EstadoRecuperacion.SUMINISTRO_NO_ENCONTRADO) {
            return EstadoActualizacion.CON_INCIDENCIAS;
        }
        if (datos.sincronizacion() != null) {
            switch (datos.sincronizacion()) {
                case ERROR_FUENTE, SUMINISTRO_NO_ENCONTRADO, BLOQUEADO, CONFLICTO,
                        ERROR_ALMACENAMIENTO, ERROR_INTERNO -> {
                    return EstadoActualizacion.CON_INCIDENCIAS;
                }
                default -> { }
            }
        }
        if (ultima == null) {
            return EstadoActualizacion.SIN_LECTURAS;
        }
        return corte != null && !ultima.isAfter(corte)
            ? EstadoActualizacion.CORTE_CONFIRMADO : EstadoActualizacion.PENDIENTE;
    }

    public enum EstadoActualizacion { SIN_LECTURAS, PENDIENTE, CORTE_CONFIRMADO, CON_INCIDENCIAS }

    public record Actualizacion(EstadoActualizacion estado, Instant ultimaLectura,
            Instant confirmadoHasta) {}

    public record Resultado(String fuente, String codigoSuministro, String periodo,
            String zonaHoraria, Instant inicioMes, Instant finMesExclusivo, Instant consultadoEn,
            ConsumoPeriodo consumo, Actualizacion actualizacion) {}
}
