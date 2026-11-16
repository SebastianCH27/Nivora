package pe.nivora.backend.acceso.dominio.contratos;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;
import pe.nivora.backend.acceso.dominio.SuministroVerificado;

public interface ConsultaRegistrosAcceso {
    List<SuministroVerificado> suministros(CodigoSuministro despuesDe, int limite);

    Optional<List<Acceso>> accesos(CodigoSuministro suministro, Cursor antesDe, int limite);

    record Cursor(Instant creadaEn, UUID id) {}

    record Acceso(Cursor cursor, String dniEnmascarado, Instant expiraEn, Instant revocadaEn) {}

    class NoDisponible extends RuntimeException {
        public NoDisponible(Throwable causa) {
            super("No se pudieron consultar los registros guardados.", causa);
        }
    }
}
