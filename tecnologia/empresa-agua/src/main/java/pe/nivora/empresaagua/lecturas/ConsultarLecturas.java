package pe.nivora.empresaagua.lecturas;

import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

@Service
public class ConsultarLecturas {

    private final LecturasRepositorio repositorio;

    public ConsultarLecturas(LecturasRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public PaginaLecturas consultar(String codigo, long despuesDe, int limite) {
        return consultar(codigo, despuesDe, limite, null);
    }

    public PaginaLecturas consultar(String codigo, long despuesDe, int limite, Long hastaId) {
        if (codigo == null || !codigo.matches("[0-9]{1,20}")) {
            throw new IllegalArgumentException("El codigo debe contener entre 1 y 20 digitos.");
        }
        if (despuesDe < 0) {
            throw new IllegalArgumentException("El cursor despuesDe no puede ser negativo.");
        }
        if (limite < 1 || limite > 500) {
            throw new IllegalArgumentException("El limite debe estar entre 1 y 500.");
        }
        if (hastaId != null && (hastaId < 0 || hastaId < despuesDe)) {
            throw new IllegalArgumentException(
                "El limite hastaId debe ser mayor o igual que despuesDe.");
        }

        long suministro = repositorio.buscarIdSuministro(codigo).orElseThrow(
            () -> new NoSuchElementException("No existe un suministro con ese codigo."));
        var encontradas = repositorio.buscar(suministro, despuesDe, limite + 1, hastaId);
        boolean hayMas = encontradas.size() > limite;
        var pagina = encontradas.subList(0, Math.min(encontradas.size(), limite));
        String siguiente = pagina.isEmpty() ? Long.toString(despuesDe)
            : pagina.getLast().idLectura();

        return new PaginaLecturas("empresa-agua", codigo, "ACUMULADA", "L",
            pagina, siguiente, hayMas);
    }
}
