package pe.nivora.backend.lecturas.dominio;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;

/** Corte y copia del inventario utilizados durante un recorrido. */
public record ContextoLecturas(String fuente, CodigoSuministro suministro,
        String hastaIdLectura, Instant confirmadoHasta, List<Medidor> medidores) {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    public ContextoLecturas {
        LecturaAcumulada.validarIdentificador(fuente);
        Objects.requireNonNull(suministro);
        numero(hastaIdLectura, true);
        Objects.requireNonNull(confirmadoHasta);
        int anio = confirmadoHasta.atOffset(ZoneOffset.UTC).getYear();
        if (anio < 1000 || anio > 9999
                || !confirmadoHasta.equals(confirmadoHasta.truncatedTo(ChronoUnit.MICROS))) {
            throw new IllegalArgumentException("La fecha del corte no es compatible.");
        }
        medidores = List.copyOf(medidores);
        if (medidores.isEmpty() || medidores.size() > 1000) {
            throw new IllegalArgumentException("El inventario debe contener de 1 a 1000 medidores.");
        }
        var ids = new HashSet<String>();
        for (Medidor medidor : medidores) {
            if (!ids.add(medidor.idMedidor())) {
                throw new IllegalArgumentException("El inventario contiene medidores repetidos.");
            }
        }
    }

    public static long numero(String texto, boolean cero) {
        String patron = cero ? "(?:0|[1-9][0-9]{0,18})" : "[1-9][0-9]{0,18}";
        if (texto == null || !texto.matches(patron)) {
            throw new IllegalArgumentException("El identificador no es un decimal canonico.");
        }
        return Long.parseLong(texto);
    }

    public Optional<MotivoRechazoLectura> validar(LecturaAcumulada lectura) {
        if (numero(lectura.idLectura(), false) > numero(hastaIdLectura, true)) {
            return Optional.of(MotivoRechazoLectura.ID_POSTERIOR_AL_CORTE);
        }
        if (lectura.fechaMedicion().isAfter(confirmadoHasta)) {
            return Optional.of(MotivoRechazoLectura.FECHA_POSTERIOR_AL_CORTE);
        }
        var medidor = medidores.stream()
            .filter(m -> m.idMedidor().equals(lectura.idMedidor())).findFirst();
        if (medidor.isEmpty()) {
            return Optional.of(MotivoRechazoLectura.MEDIDOR_NO_REGISTRADO);
        }
        LocalDate fecha = lectura.fechaMedicion().atZone(LIMA).toLocalDate();
        var m = medidor.orElseThrow();
        if (fecha.isBefore(m.fechaInstalacion())
                || (m.fechaRetiro() != null && fecha.isAfter(m.fechaRetiro()))) {
            return Optional.of(MotivoRechazoLectura.FECHA_FUERA_VIGENCIA_MEDIDOR);
        }
        return Optional.empty();
    }

    public record Medidor(String idMedidor, LocalDate fechaInstalacion, LocalDate fechaRetiro) {
        public Medidor {
            numero(idMedidor, false);
            Objects.requireNonNull(fechaInstalacion);
            if (fechaInstalacion.getYear() < 1000 || fechaInstalacion.getYear() > 9999
                    || (fechaRetiro != null && (fechaRetiro.isBefore(fechaInstalacion)
                        || fechaRetiro.getYear() > 9999))) {
                throw new IllegalArgumentException("Las fechas del medidor no son compatibles.");
            }
        }
    }
}
