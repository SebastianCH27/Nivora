package pe.nivora.backend.recibos.dominio;

import java.time.Instant;
import java.util.List;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;

public interface RepositorioRecibos {
    /** Guarda el lote de forma atomica. Los recibos identicos conservan su fecha de recepcion. */
    Incorporacion guardar(RecibosEmitidos lote, Instant recibidoEn);

    List<Guardado> buscarRecientes(CodigoSuministro suministro, int limite);

    record Incorporacion(int nuevos, int repetidos) {}

    record Guardado(ReciboEmitido recibo, Instant recibidoEn) {}

    class Conflicto extends IllegalStateException {
        public Conflicto() {
            super("El periodo o el numero de recibo corresponde a un recibo diferente ya guardado.");
        }
    }

    class SuministroNoRegistrado extends IllegalStateException {
        public SuministroNoRegistrado() {
            super("Verifica el suministro en Nivora antes de guardar sus recibos.");
        }
    }
}
