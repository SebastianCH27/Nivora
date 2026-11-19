package pe.nivora.backend.lecturas.dominio;

import java.time.Instant;
import java.util.Objects;
import pe.nivora.backend.acceso.dominio.CodigoSuministro;

/** Consumo del tramo [inicioIntervalo, fechaMedicion), sin prorratear sus litros. */
public record LecturaIntervalo(String fuente, String idLectura, CodigoSuministro suministro,
        String idMedidor, Instant inicioIntervalo, Instant fechaMedicion, long valorLitros) implements Lectura {
    public LecturaIntervalo {
        LecturaAcumulada.validarIdentificador(fuente);
        LecturaAcumulada.validarIdentificador(idLectura);
        LecturaAcumulada.validarIdentificador(idMedidor);
        Objects.requireNonNull(suministro, "El suministro es obligatorio.");
        Objects.requireNonNull(inicioIntervalo, "El inicio del intervalo es obligatorio.");
        Objects.requireNonNull(fechaMedicion, "El fin del intervalo es obligatorio.");
        if (!inicioIntervalo.isBefore(fechaMedicion) || valorLitros < 0) {
            throw new IllegalArgumentException("El intervalo debe tener duración positiva y consumo no negativo.");
        }
    }

    @Override public Tipo tipoMedicion() { return Tipo.INTERVALO; }
}
