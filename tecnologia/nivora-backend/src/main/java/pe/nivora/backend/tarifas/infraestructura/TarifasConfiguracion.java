package pe.nivora.backend.tarifas.infraestructura;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.nivora.backend.compartido.infraestructura.empresaagua.ClienteEmpresaAgua;
import pe.nivora.backend.lecturas.aplicacion.ConsultarConsumoActual;
import pe.nivora.backend.lecturas.aplicacion.ConsultarEstadisticasConsumo;
import pe.nivora.backend.tarifas.aplicacion.ConsultarCostosConsumo;
import pe.nivora.backend.tarifas.aplicacion.ConsultarProyeccionMensual;
import pe.nivora.backend.tarifas.aplicacion.IncorporarTarifas;
import pe.nivora.backend.tarifas.dominio.FuenteTarifas;
import pe.nivora.backend.tarifas.dominio.RepositorioTarifas;

@Configuration(proxyBeanMethods = false)
public class TarifasConfiguracion {
    @Bean
    FuenteTarifas fuenteTarifas(ClienteEmpresaAgua cliente) {
        return new FuenteTarifasHttp(cliente);
    }

    @Bean
    IncorporarTarifas incorporarTarifas(FuenteTarifas fuente, RepositorioTarifas repositorio, Clock reloj) {
        return new IncorporarTarifas(fuente, repositorio, reloj);
    }

    @Bean
    ConsultarCostosConsumo consultarCostosConsumo(ConsultarEstadisticasConsumo consumos, RepositorioTarifas tarifas) {
        return new ConsultarCostosConsumo(consumos, tarifas);
    }

    @Bean
    ConsultarProyeccionMensual consultarProyeccionMensual(ConsultarConsumoActual consumo, RepositorioTarifas tarifas) {
        return new ConsultarProyeccionMensual(consumo, tarifas);
    }
}
