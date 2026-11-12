package pe.nivora.backend.notificaciones.infraestructura;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import pe.nivora.backend.notificaciones.dominio.AlertaConsumo;
import pe.nivora.backend.notificaciones.dominio.RepositorioAlertasConsumo;

@Repository
public class RepositorioAlertasConsumoJdbc implements RepositorioAlertasConsumo {
    private final JdbcTemplate jdbc;

    public RepositorioAlertasConsumoJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean registrar(AlertaConsumo alerta) {
        try {
            jdbc.update("""
                INSERT INTO alertas_consumo (codigo_suministro, periodo, consumo_actual_litros,
                    consumo_anterior_litros, medido_hasta_utc, detectada_en_utc)
                VALUES (?, ?, ?, ?, ?, ?)
                """, alerta.suministro().valor(), alerta.periodo().atDay(1), alerta.consumoActualLitros(),
                alerta.consumoAnteriorLitros(), LocalDateTime.ofInstant(alerta.medidoHasta(), ZoneOffset.UTC),
                LocalDateTime.ofInstant(alerta.detectadaEn(), ZoneOffset.UTC));
            return true;
        } catch (DuplicateKeyException repetida) {
            return false;
        }
    }
}
