package pe.nivora.backend.lecturas.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.ConsumoPeriodo;
import pe.nivora.backend.lecturas.dominio.Lectura;
import pe.nivora.backend.lecturas.dominio.LecturaIntervalo;
import pe.nivora.backend.lecturas.dominio.MesConsumo;
import pe.nivora.backend.lecturas.dominio.contratos.ConsultaLecturasPeriodo;
import pe.nivora.backend.lecturas.dominio.contratos.SuministroNoRegistradoException;
import pe.nivora.backend.periodos.dominio.CalendarioMensual;
import pe.nivora.backend.periodos.dominio.RepositorioPeriodosMensuales;

public class ConsultarHistorialMensual {
    private static final String FUENTE = "empresa-agua";
    private static final int MAXIMO_MESES = 12;

    private final ConsultaLecturasPeriodo consulta;
    private final RepositorioPeriodosMensuales periodos;
    private final Clock reloj;

    public ConsultarHistorialMensual(ConsultaLecturasPeriodo consulta, RepositorioPeriodosMensuales periodos, Clock reloj) {
        this.consulta = consulta;
        this.periodos = periodos;
        this.reloj = reloj;
    }

    public Resultado ejecutar(CodigoSuministro suministro, int meses, YearMonth hasta) {
        validarMeses(meses);
        Instant ahora = reloj.instant().truncatedTo(ChronoUnit.MICROS);
        var calendario = periodos.consultar();
        YearMonth actual = calendario.actual(ahora).valor();
        YearMonth ultimo = hasta == null ? actual.minusMonths(1) : hasta;
        YearMonth primero = ultimo.minusMonths(meses - 1L);
        if (!ultimo.isBefore(actual) || primero.getYear() < 1000) {
            throw new IllegalArgumentException("El rango debe corresponder a meses anteriores.");
        }

        var lecturas = consulta.consultar(FUENTE, suministro,
                calendario.obtener(primero).inicio(), calendario.obtener(ultimo).fin(), ahora)
            .orElseThrow(SuministroNoRegistradoException::new)
            .lecturas();
        return new Resultado(FUENTE, suministro.valor(), MesConsumo.ZONA.getId(), ahora,
            calcularPeriodos(calendario, primero, meses, lecturas));
    }

    static void validarMeses(int meses) {
        if (meses < 1 || meses > MAXIMO_MESES) {
            throw new IllegalArgumentException("Se admiten de 1 a 12 meses por consulta.");
        }
    }

    static List<Periodo> calcularPeriodos(CalendarioMensual calendario, YearMonth primero,
            int meses, List<? extends Lectura> lecturas) {
        var periodos = new ArrayList<Periodo>(meses);
        boolean intervalos = lecturas.stream().anyMatch(l -> l instanceof LecturaIntervalo);
        int inicioLecturas = 0;
        int finLecturas = 0;
        for (int i = 0; i < meses; i++) {
            var mes = calendario.obtener(primero.plusMonths(i));
            Instant inicio = mes.inicio();
            Instant fin = mes.fin();
            while (finLecturas < lecturas.size()
                    && !lecturas.get(finLecturas).fechaMedicion().isAfter(fin)) {
                finLecturas++;
            }
            var consumo = ConsumoPeriodo.calcular(inicio, fin,
                intervalos ? ConsumoPeriodo.seleccionar(inicio, fin, lecturas)
                    : lecturas.subList(inicioLecturas, finLecturas));
            periodos.add(new Periodo(mes.valor().toString(), inicio, fin, consumo));

            // Las mediciones exactas del limite cierran este mes y abren el siguiente.
            inicioLecturas = finLecturas;
            while (inicioLecturas > 0 && lecturas.get(inicioLecturas - 1).fechaMedicion().equals(fin)) {
                inicioLecturas--;
            }
        }

        return List.copyOf(periodos.reversed());
    }

    public record Periodo(String periodo, Instant inicioMes, Instant finMesExclusivo,
            ConsumoPeriodo consumo) {}

    public record Resultado(String fuente, String codigoSuministro, String zonaHoraria,
            Instant consultadoEn, List<Periodo> periodos) {}
}
