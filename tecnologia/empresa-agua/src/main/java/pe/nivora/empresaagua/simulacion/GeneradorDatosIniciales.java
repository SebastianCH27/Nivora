package pe.nivora.empresaagua.simulacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(
    name = "empresa.carga-inicial.habilitada",
    havingValue = "true"
)
public class GeneradorDatosIniciales implements ApplicationRunner {

    private static final Logger log =
        LoggerFactory.getLogger(GeneradorDatosIniciales.class);

    private static final int TAMANO_LOTE = 1000;
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private static final String[] NOMBRES = {
        "Ana", "Luis", "María", "José", "Lucía", "Carlos",
        "Rosa", "Jorge", "Elena", "Pedro", "Diana", "Miguel"
    };

    private static final String[] APELLIDOS = {
        "Quispe", "Huamán", "Flores", "García", "Rojas", "Torres",
        "Vargas", "Mendoza", "López", "Castro", "Soto", "Ramos"
    };

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final int cantidad;

    public GeneradorDatosIniciales(
            JdbcTemplate jdbc,
            PlatformTransactionManager gestorTransacciones,
            @Value("${empresa.carga-inicial.cantidad:100000}") int cantidad) {
        this.jdbc = jdbc;
        this.transaccion = new TransactionTemplate(gestorTransacciones);
        this.cantidad = cantidad;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        if (cantidad < 1) {
            throw new IllegalArgumentException("La cantidad debe ser positiva.");
        }

        LocalDate fechaInicial = LocalDate.now(ZONA_LIMA)
            .withDayOfMonth(1).minusMonths(1);

        Boolean creada = transaccion.execute(estado -> {
            if (hayDatos()) {
                return false;
            }

            for (int desde = 1; desde <= cantidad; desde += TAMANO_LOTE) {
                int hasta = Math.min(desde + TAMANO_LOTE - 1, cantidad);
                insertarLote(desde, hasta, fechaInicial);
                log.info("Preparados {} de {} suministros", hasta, cantidad);
            }
            return true;
        });

        if (Boolean.TRUE.equals(creada)) {
            log.info("Carga inicial completada: {} suministros. Fecha inicial: {}",
                cantidad, fechaInicial);
        } else {
            log.info("Carga omitida: ya existen titulares, suministros, medidores o lecturas.");
        }
    }

    private boolean hayDatos() {
        Boolean existe = jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM titulares)
                OR EXISTS (SELECT 1 FROM suministros)
                OR EXISTS (SELECT 1 FROM medidores)
                OR EXISTS (SELECT 1 FROM lecturas)
            """, Boolean.class);
        return Boolean.TRUE.equals(existe);
    }

    private void insertarLote(int desde, int hasta, LocalDate fechaInicial) {
        List<Object[]> titulares = new ArrayList<>();
        List<Object[]> suministros = new ArrayList<>();
        List<Object[]> medidores = new ArrayList<>();
        List<Object[]> lecturas = new ArrayList<>();

        LocalDateTime fechaUtc = fechaInicial.atStartOfDay(ZONA_LIMA)
            .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

        // Los identificadores se asignan solo para esta carga sobre tablas vacias.
        for (long id = desde; id <= hasta; id++) {
            SplittableRandom azar = new SplittableRandom(2026L + id);
            String nombre = NOMBRES[azar.nextInt(NOMBRES.length)] + " "
                + APELLIDOS[azar.nextInt(APELLIDOS.length)] + " "
                + APELLIDOS[azar.nextInt(APELLIDOS.length)];

            String codigo = Long.toString(13100000L + id - 1);
            String serie = "MED-" + codigo;
            String direccion = "Sector " + ((id - 1) / 1000 + 1)
                + ", Calle " + ((id - 1) % 1000 / 20 + 1)
                + ", Vivienda " + ((id - 1) % 20 + 1);

            BigDecimal lecturaInicial =
                BigDecimal.valueOf(50000L + azar.nextInt(950001), 3);

            titulares.add(new Object[] {id, nombre});
            suministros.add(new Object[] {id, codigo, id, direccion});
            medidores.add(new Object[] {id, serie, id, fechaInicial});
            lecturas.add(new Object[] {id, id, fechaUtc, lecturaInicial});
        }

        jdbc.batchUpdate("""
            INSERT INTO titulares (id_titular, nombre_completo)
            VALUES (?, ?)
            """, titulares);

        jdbc.batchUpdate("""
            INSERT INTO suministros
                (id_suministro, codigo, id_titular, direccion)
            VALUES (?, ?, ?, ?)
            """, suministros);

        jdbc.batchUpdate("""
            INSERT INTO medidores
                (id_medidor, numero_serie, id_suministro, fecha_instalacion)
            VALUES (?, ?, ?, ?)
            """, medidores);

        jdbc.batchUpdate("""
            INSERT INTO lecturas
                (id_suministro, id_medidor, fecha_medicion, lectura_acumulada_m3)
            VALUES (?, ?, ?, ?)
            """, lecturas);
    }
}