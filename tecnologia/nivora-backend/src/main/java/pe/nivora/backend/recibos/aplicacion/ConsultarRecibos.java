package pe.nivora.backend.recibos.aplicacion;

import java.time.YearMonth;
import java.util.List;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.lecturas.dominio.MesConsumo;
import pe.nivora.backend.periodos.dominio.RepositorioPeriodosMensuales;
import pe.nivora.backend.recibos.dominio.RepositorioRecibos;

public class ConsultarRecibos {
    private final RepositorioRecibos repositorio;
    private final RepositorioPeriodosMensuales periodos;

    public ConsultarRecibos(RepositorioRecibos repositorio, RepositorioPeriodosMensuales periodos) {
        this.repositorio = repositorio;
        this.periodos = periodos;
    }

    public Resultado ejecutar(CodigoSuministro suministro, YearMonth antesDe, int limite) {
        var pagina = repositorio.buscarPagina(suministro, antesDe, limite);
        var calendario = periodos.consultar();
        var recibos = pagina.recibos().stream().map(guardado -> {
            var mes = guardado.recibo().periodo();
            return new Detalle(guardado, new MesConsumo(mes), calendario.obtener(mes));
        }).toList();
        return new Resultado(recibos, pagina.siguienteAntesDe());
    }

    public record Detalle(RepositorioRecibos.Guardado guardado, MesConsumo facturado, MesConsumo analisis) {}

    public record Resultado(List<Detalle> recibos, YearMonth siguienteAntesDe) {}
}
