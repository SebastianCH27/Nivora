package pe.nivora.backend.tarifas.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.aplicacion.ConsultarHistorialMensual.Periodo;
import pe.nivora.backend.lecturas.dominio.ConsumoPeriodo;
import pe.nivora.backend.lecturas.dominio.Lectura;
import pe.nivora.backend.lecturas.dominio.MesConsumo;
import pe.nivora.backend.lecturas.dominio.contratos.ConsultaLecturasPeriodo;
import pe.nivora.backend.lecturas.dominio.contratos.SuministroNoRegistradoException;
import pe.nivora.backend.periodos.dominio.RepositorioPeriodosMensuales;
import pe.nivora.backend.tarifas.dominio.ComparacionCostos;
import pe.nivora.backend.tarifas.dominio.RepositorioTarifas;
import pe.nivora.backend.tarifas.dominio.TarifasParaCalculo;

public final class ConsultarComparacionCostos {
    private static final String FUENTE = "empresa-agua";
    private final ConsultaLecturasPeriodo lecturas;
    private final RepositorioTarifas tarifas;
    private final RepositorioPeriodosMensuales periodos;
    private final Clock reloj;

    public ConsultarComparacionCostos(ConsultaLecturasPeriodo lecturas, RepositorioTarifas tarifas,
            RepositorioPeriodosMensuales periodos, Clock reloj) {
        this.lecturas = lecturas;
        this.tarifas = tarifas;
        this.periodos = periodos;
        this.reloj = reloj;
    }

    public Resultado ejecutar(CodigoSuministro suministro, YearMonth base, YearMonth comparado) {
        if ((base == null) != (comparado == null)) {
            throw new IllegalArgumentException("Indica ambos periodos o utiliza la comparación predeterminada.");
        }
        if (base != null && (base.getYear() < 1000 || comparado.getYear() < 1000
                || Math.abs(ChronoUnit.MONTHS.between(base, comparado)) > 12)) {
            throw new IllegalArgumentException("Los periodos deben estar separados por un máximo de 12 meses.");
        }
        Instant ahora = reloj.instant().truncatedTo(ChronoUnit.MICROS);
        var calendario = periodos.consultar();
        YearMonth actual = calendario.actual(ahora).valor();
        if (base == null) {
            base = actual.minusMonths(1);
            comparado = actual;
        }
        if (base.isAfter(actual) || comparado.isAfter(actual)) {
            throw new IllegalArgumentException("No se pueden comparar periodos futuros.");
        }
        var mesBase = calendario.obtener(base);
        var mesComparado = calendario.obtener(comparado);
        Instant desde = mesBase.inicio().isBefore(mesComparado.inicio()) ? mesBase.inicio() : mesComparado.inicio();
        Instant hasta = mesBase.fin().isAfter(mesComparado.fin()) ? mesBase.fin() : mesComparado.fin();
        if (hasta.isAfter(ahora)) hasta = ahora;
        // Una sola vista de lecturas para ambos periodos, con rango acotado a 13 meses.
        var datos = lecturas.consultar(FUENTE, suministro, desde, hasta, ahora)
            .orElseThrow(SuministroNoRegistradoException::new);
        var historial = tarifas.buscarParaCalculo(FUENTE, suministro);
        var primero = medir(mesBase, ahora, datos.lecturas(), historial);
        var segundo = medir(mesComparado, ahora, datos.lecturas(), historial);
        var diferencia = ComparacionCostos.calcular(primero.estimacion().periodo().consumo(),
            total(primero.estimacion()), primero.periodoCompleto(), segundo.estimacion().periodo().consumo(),
            total(segundo.estimacion()), segundo.periodoCompleto());
        Boolean mismaDuracion = primero.duracionTramo() == null || segundo.duracionTramo() == null ? null
            : primero.duracionTramo().equals(segundo.duracionTramo());
        return new Resultado(FUENTE, suministro.valor(), MesConsumo.ZONA.getId(), ahora,
            historial.revisionImportada(), historial.revisionAdministrativa(), primero, segundo,
            diferencia, mismaDuracion, mismaVersion(primero.estimacion(), segundo.estimacion()));
    }

    private static Tramo medir(MesConsumo mes, Instant ahora, List<? extends Lectura> datos,
            TarifasParaCalculo tarifas) {
        boolean enCurso = ahora.isBefore(mes.fin());
        Instant hasta = enCurso ? ahora : mes.fin();
        var candidatas = ConsumoPeriodo.seleccionar(mes.inicio(), hasta, datos);
        if (enCurso && !candidatas.isEmpty()) hasta = candidatas.getLast().fechaMedicion();
        var consumo = ConsumoPeriodo.calcular(mes.inicio(), hasta, candidatas);
        var periodo = new Periodo(mes.valor().toString(), mes.inicio(), mes.fin(), consumo);
        boolean completo = !enCurso && consumo.estado() == ConsumoPeriodo.Estado.COMPLETO
            && mes.inicio().equals(consumo.desde()) && mes.fin().equals(consumo.hasta());
        Duration tramo = consumo.desde() == null || consumo.hasta() == null ? null
            : Duration.between(consumo.desde(), consumo.hasta());
        return new Tramo(ConsultarCostosConsumo.estimar(periodo, tarifas), enCurso, completo,
            Duration.between(mes.inicio(), mes.fin()), tramo);
    }

    private static java.math.BigDecimal total(ConsultarCostosConsumo.Estimacion estimacion) {
        return estimacion.costo() == null ? null : estimacion.costo().totalEstimado();
    }

    private static Boolean mismaVersion(ConsultarCostosConsumo.Estimacion base,
            ConsultarCostosConsumo.Estimacion comparado) {
        var a = base.seleccion();
        var b = comparado.seleccion();
        if (a == null || b == null || a.asignacion() == null || b.asignacion() == null) return null;
        return Objects.equals(a.fuente(), b.fuente())
            && a.asignacion().tarifa().equals(b.asignacion().tarifa());
    }

    public record Tramo(ConsultarCostosConsumo.Estimacion estimacion, boolean enCurso,
            boolean periodoCompleto, Duration duracionPeriodo, Duration duracionTramo) {}

    public record Resultado(String fuente, String codigoSuministro, String zonaHoraria,
            Instant consultadoEn, Long revisionTarifaria, Long revisionTarifariaAdministrativa,
            Tramo base, Tramo comparado, ComparacionCostos diferencia,
            Boolean mismaDuracionTramo, Boolean mismaVersionTarifaria) {}
}
