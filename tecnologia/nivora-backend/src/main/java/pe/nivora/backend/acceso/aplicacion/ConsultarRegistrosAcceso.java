package pe.nivora.backend.acceso.aplicacion;

import java.util.List;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.SuministroVerificado;
import pe.nivora.backend.acceso.dominio.contratos.ConsultaRegistrosAcceso;
import pe.nivora.backend.acceso.dominio.contratos.ConsultaRegistrosAcceso.Acceso;
import pe.nivora.backend.acceso.dominio.contratos.ConsultaRegistrosAcceso.Cursor;

public class ConsultarRegistrosAcceso {
    private final ConsultaRegistrosAcceso consulta;

    public ConsultarRegistrosAcceso(ConsultaRegistrosAcceso consulta) {
        this.consulta = consulta;
    }

    public Pagina<SuministroVerificado, CodigoSuministro> suministros(CodigoSuministro despuesDe, int limite) {
        validarLimite(limite);
        var filas = consulta.suministros(despuesDe, limite + 1);
        if (filas.size() <= limite) return new Pagina<>(List.copyOf(filas), null);
        var elementos = List.copyOf(filas.subList(0, limite));
        return new Pagina<>(elementos, elementos.getLast().suministro().codigo());
    }

    public Optional<Pagina<Acceso, Cursor>> accesos(CodigoSuministro suministro, Cursor antesDe, int limite) {
        validarLimite(limite);
        return consulta.accesos(suministro, antesDe, limite + 1).map(filas -> {
            if (filas.size() <= limite) return new Pagina<>(List.copyOf(filas), null);
            var elementos = List.copyOf(filas.subList(0, limite));
            return new Pagina<>(elementos, elementos.getLast().cursor());
        });
    }

    private static void validarLimite(int limite) {
        if (limite < 1 || limite > 100) throw new IllegalArgumentException("El limite debe estar entre 1 y 100.");
    }

    public record Pagina<T, C>(List<T> elementos, C siguiente) {}
}
