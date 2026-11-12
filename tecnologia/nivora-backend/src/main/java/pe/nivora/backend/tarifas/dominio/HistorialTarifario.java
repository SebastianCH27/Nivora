package pe.nivora.backend.tarifas.dominio;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import pe.nivora.backend.acceso.dominio.CodigoSuministro;

public record HistorialTarifario(String fuente, CodigoSuministro suministro, List<Asignacion> asignaciones) {
    public HistorialTarifario {
        VersionTarifaria.texto(fuente, 100);
        Objects.requireNonNull(suministro);
        asignaciones = List.copyOf(asignaciones);
        var versiones = new HashMap<ClaveVersion, Asignacion>();
        Asignacion anterior = null;
        for (Asignacion actual : asignaciones) {
            if (anterior != null && (anterior.aplicableHasta() == null
                    || actual.aplicableDesde().isBefore(anterior.aplicableHasta()))) {
                throw new IllegalArgumentException("Las vigencias deben estar ordenadas y no superponerse.");
            }
            var clave = new ClaveVersion(actual.tarifa().codigo(), actual.tarifa().version());
            var conocida = versiones.putIfAbsent(clave, actual);
            if (conocida != null && (!conocida.tarifa().equals(actual.tarifa())
                    || !Objects.equals(conocida.vigenteHasta(), actual.vigenteHasta()))) {
                throw new IllegalArgumentException("Una version tiene mas de una definicion.");
            }
            anterior = actual;
        }
    }

    /** Busca una asignacion que cubra todo el intervalo [desde, hasta). */
    public Optional<Asignacion> aplicable(LocalDate desde, LocalDate hasta) {
        VersionTarifaria.fecha(desde);
        VersionTarifaria.fecha(hasta);
        if (!desde.isBefore(hasta)) {
            throw new IllegalArgumentException("El intervalo de consumo debe tener una duracion positiva.");
        }
        return asignaciones.stream().filter(a -> !desde.isBefore(a.aplicableDesde())
                && (a.aplicableHasta() == null || !hasta.isAfter(a.aplicableHasta())))
            .findFirst();
    }

    private record ClaveVersion(String codigo, int version) {}

    public record Asignacion(VersionTarifaria tarifa, LocalDate vigenteHasta, LocalDate asignadaDesde,
            LocalDate asignadaHasta) {
        public Asignacion {
            Objects.requireNonNull(tarifa);
            VersionTarifaria.fecha(asignadaDesde);
            LocalDate fin = minimo(vigenteHasta, asignadaHasta);
            if (vigenteHasta != null) VersionTarifaria.fecha(vigenteHasta);
            if (asignadaHasta != null) VersionTarifaria.fecha(asignadaHasta);
            if (fin != null && !fin.isAfter(inicio(tarifa, asignadaDesde))) {
                throw new IllegalArgumentException("La asignacion no tiene un intervalo aplicable.");
            }
        }

        public LocalDate aplicableDesde() {
            return inicio(tarifa, asignadaDesde);
        }

        public LocalDate aplicableHasta() {
            return minimo(vigenteHasta, asignadaHasta);
        }

        private static LocalDate inicio(VersionTarifaria tarifa, LocalDate asignadaDesde) {
            return tarifa.vigenteDesde().isAfter(asignadaDesde) ? tarifa.vigenteDesde() : asignadaDesde;
        }

        private static LocalDate minimo(LocalDate a, LocalDate b) {
            if (a == null) return b;
            if (b == null) return a;
            return a.isBefore(b) ? a : b;
        }
    }
}
