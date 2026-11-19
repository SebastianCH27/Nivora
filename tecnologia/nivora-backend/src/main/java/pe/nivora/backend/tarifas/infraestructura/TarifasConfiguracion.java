package pe.nivora.backend.tarifas.infraestructura;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.nivora.backend.compartido.infraestructura.empresaagua.ClienteEmpresaAgua;
import pe.nivora.backend.lecturas.aplicacion.ConsultarConsumoActual;
import pe.nivora.backend.lecturas.aplicacion.ConsultarEstadisticasConsumo;
import pe.nivora.backend.tarifas.aplicacion.ConsultarCostosConsumo;
import pe.nivora.backend.tarifas.aplicacion.ConsultarComparacionCostos;
import pe.nivora.backend.lecturas.dominio.contratos.ConsultaLecturasPeriodo;
import pe.nivora.backend.periodos.dominio.RepositorioPeriodosMensuales;
import pe.nivora.backend.tarifas.aplicacion.ConsultarProyeccionMensual;
import pe.nivora.backend.tarifas.aplicacion.GestionarTarifas;
import pe.nivora.backend.tarifas.aplicacion.IncorporarTarifas;
import pe.nivora.backend.tarifas.aplicacion.SincronizarTarifas;
import pe.nivora.backend.tarifas.dominio.FuenteTarifas;
import pe.nivora.backend.tarifas.dominio.RepositorioTarifas;
import pe.nivora.backend.tarifas.dominio.RepositorioSincronizacionTarifas;

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
    SincronizarTarifas sincronizarTarifas(RepositorioSincronizacionTarifas programacion,
            IncorporarTarifas incorporar, Clock reloj,
            @Value("${nivora.tarifas.sincronizacion.maximo-intentos-por-ciclo}") int intentos,
            @Value("${nivora.tarifas.sincronizacion.reserva-segundos}") long reserva,
            @Value("${nivora.tarifas.sincronizacion.intervalo-completo-segundos}") long intervalo,
            @Value("${nivora.tarifas.sincronizacion.reintento-inicial-segundos}") long inicial,
            @Value("${nivora.tarifas.sincronizacion.reintento-maximo-segundos}") long maximo) {
        var politica = new SincronizarTarifas.Politica(intentos, Duration.ofSeconds(reserva),
            Duration.ofSeconds(intervalo), Duration.ofSeconds(inicial), Duration.ofSeconds(maximo));
        return new SincronizarTarifas(programacion, incorporar, politica, reloj);
    }

    @Bean
    ConsultarCostosConsumo consultarCostosConsumo(ConsultarEstadisticasConsumo consumos, RepositorioTarifas tarifas) {
        return new ConsultarCostosConsumo(consumos, tarifas);
    }

    @Bean
    ConsultarComparacionCostos consultarComparacionCostos(ConsultaLecturasPeriodo lecturas,
            RepositorioTarifas tarifas, RepositorioPeriodosMensuales periodos, Clock reloj) {
        return new ConsultarComparacionCostos(lecturas, tarifas, periodos, reloj);
    }

    @Bean
    ConsultarProyeccionMensual consultarProyeccionMensual(ConsultarConsumoActual consumo, RepositorioTarifas tarifas) {
        return new ConsultarProyeccionMensual(consumo, tarifas);
    }

    @Bean
    GestionarTarifas gestionarTarifas(RepositorioTarifas tarifas, Clock reloj) {
        return new GestionarTarifas(tarifas, reloj);
    }
}
