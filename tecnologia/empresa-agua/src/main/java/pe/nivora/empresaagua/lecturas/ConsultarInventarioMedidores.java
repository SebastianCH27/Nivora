package pe.nivora.empresaagua.lecturas;

import java.time.LocalDate;
import java.util.NoSuchElementException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConsultarInventarioMedidores {

    private final JdbcTemplate jdbc;
    private final LecturasRepositorio suministros;

    public ConsultarInventarioMedidores(JdbcTemplate jdbc,
            LecturasRepositorio suministros) {
        this.jdbc = jdbc;
        this.suministros = suministros;
    }

    public InventarioMedidores consultar(String codigo) {
        if (codigo == null || !codigo.matches("[0-9]{1,20}")) {
            throw new IllegalArgumentException(
                "El codigo debe contener entre 1 y 20 digitos.");
        }
        long suministro = suministros.buscarIdSuministro(codigo).orElseThrow(
            () -> new NoSuchElementException("No existe un suministro con ese codigo."));
        var medidores = jdbc.query("""
            SELECT id_medidor, fecha_instalacion, fecha_retiro
            FROM medidores WHERE id_suministro = ?
            ORDER BY fecha_instalacion, id_medidor LIMIT 1001
            """, (fila, numero) -> {
                long id = fila.getLong("id_medidor");
                LocalDate instalada = fila.getObject("fecha_instalacion", LocalDate.class);
                LocalDate retirada = fila.getObject("fecha_retiro", LocalDate.class);
                if (id <= 0 || instalada == null
                        || (retirada != null && retirada.isBefore(instalada))) {
                    throw new IllegalStateException(
                        "El inventario de medidores contiene datos no validos.");
                }
                return new InventarioMedidores.Medidor(
                    Long.toString(id), instalada, retirada);
            }, suministro);
        if (medidores.isEmpty() || medidores.size() > 1000) {
            throw new IllegalStateException(
                "El inventario de medidores no esta disponible o supera el limite.");
        }
        return new InventarioMedidores("empresa-agua", codigo, "America/Lima", medidores);
    }
}
