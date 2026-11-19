package pe.nivora.backend.lecturas.dominio;

import java.time.Instant;
import pe.nivora.backend.acceso.dominio.CodigoSuministro;

/** Medición aceptable para una fuente y suministro; los volúmenes se expresan en litros. */
public sealed interface Lectura permits LecturaAcumulada, LecturaIntervalo {
    String fuente();
    String idLectura();
    CodigoSuministro suministro();
    String idMedidor();
    Instant fechaMedicion();
    long valorLitros();
    Tipo tipoMedicion();

    enum Tipo { ACUMULADA, INTERVALO }
}
