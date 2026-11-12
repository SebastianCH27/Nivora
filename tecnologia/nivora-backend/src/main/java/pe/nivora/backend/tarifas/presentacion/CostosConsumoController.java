package pe.nivora.backend.tarifas.presentacion;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import pe.nivora.backend.acceso.infraestructura.seguridad.PrincipalConsulta;
import pe.nivora.backend.lecturas.aplicacion.ConsultarConsumoActual.Actualizacion;
import pe.nivora.backend.lecturas.dominio.ConsumoPeriodo;
import pe.nivora.backend.lecturas.dominio.contratos.RepositorioLecturasNoDisponibleException;
import pe.nivora.backend.lecturas.dominio.contratos.SuministroNoRegistradoException;
import pe.nivora.backend.tarifas.aplicacion.ConsultarCostosConsumo;
import pe.nivora.backend.tarifas.aplicacion.ConsultarProyeccionMensual;
import pe.nivora.backend.tarifas.dominio.CostoEstimado;
import pe.nivora.backend.tarifas.dominio.HistorialTarifario;
import pe.nivora.backend.tarifas.dominio.ProyeccionMensual;

@RestController
public class CostosConsumoController {
    private final ConsultarCostosConsumo consulta;
    private final ConsultarProyeccionMensual proyeccion;

    public CostosConsumoController(ConsultarCostosConsumo consulta, ConsultarProyeccionMensual proyeccion) {
        this.consulta = consulta;
        this.proyeccion = proyeccion;
    }

    @GetMapping(value = "/api/v1/acceso/consulta/costos", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Respuesta> consultar(@AuthenticationPrincipal PrincipalConsulta principal,
            @RequestParam(name = "meses", defaultValue = "3") int meses) {
        var resultado = consulta.ejecutar(principal.sesion().codigoSuministro(), meses);
        var anteriores = resultado.anteriores().stream().map(CostosConsumoController::periodo).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
            new Respuesta(resultado.fuente(), resultado.codigoSuministro(), "PEN", resultado.zonaHoraria(),
                resultado.consultadoEn(), resultado.revisionTarifaria() == null ? null : resultado.revisionTarifaria().toString(),
                periodo(resultado.actual()), anteriores));
    }

    @GetMapping(value = "/api/v1/acceso/consulta/proyeccion", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RespuestaProyeccion> proyectar(@AuthenticationPrincipal PrincipalConsulta principal) {
        var resultado = proyeccion.ejecutar(principal.sesion().codigoSuministro());
        var observado = resultado.observado();
        var datos = resultado.proyeccion().datos();
        var volumen = datos == null ? null : new VolumenProyectado(datos.litros().toString(),
            decimal(datos.metrosCubicos()), decimal(datos.promedioLitrosPorDia()),
            segundos(datos.duracionObservada()), segundos(datos.duracionMes()));
        var estimacion = resultado.estimacion();
        var costo = estimacion == null ? null : new CostoProyectado(estimacion.estado(),
            estimacion.revisionTarifaria() == null ? null : estimacion.revisionTarifaria().toString(),
            tarifa(estimacion.tarifa()), desglose(estimacion.costo()));
        var supuestos = new Supuestos("RITMO_PROMEDIO_CONSTANTE", ProyeccionMensual.MINIMO_OBSERVADO.toSeconds());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new RespuestaProyeccion(
            observado.fuente(), observado.codigoSuministro(), "PEN", observado.periodo(), observado.zonaHoraria(),
            observado.inicioMes(), observado.finMesExclusivo(), observado.consultadoEn(), supuestos,
            resultado.proyeccion().estado(), consumo(observado.consumo()), observado.actualizacion(), volumen, costo));
    }

    private static PeriodoCosto periodo(ConsultarCostosConsumo.Estimacion estimacion) {
        var periodo = estimacion.periodo();
        return new PeriodoCosto(periodo.periodo(), periodo.inicioMes(), periodo.finMesExclusivo(), estimacion.estado(),
            consumo(periodo.consumo()), tarifa(estimacion.tarifa()), desglose(estimacion.costo()));
    }

    private static Consumo consumo(ConsumoPeriodo consumo) {
        return new Consumo(consumo.estado(), consumo.motivo(),
            consumo.litros() == null ? null : consumo.litros().toString(), decimal(consumo.metrosCubicos()),
            consumo.desde(), consumo.hasta(), consumo.idMedidor(), consumo.idLecturaInicial(), consumo.idLecturaFinal());
    }

    private static String segundos(Duration duracion) {
        return BigDecimal.valueOf(duracion.toNanos(), 9).stripTrailingZeros().toPlainString();
    }

    private static Tarifa tarifa(HistorialTarifario.Asignacion asignacion) {
        if (asignacion == null) return null;
        var version = asignacion.tarifa();
        return new Tarifa(version.codigo(), version.version(), version.nombre(), asignacion.aplicableDesde(),
            asignacion.aplicableHasta(), decimal(version.igvPorcentaje()));
    }

    private static Desglose desglose(CostoEstimado costo) {
        if (costo == null) return null;
        var tramos = costo.tramos().stream().map(t -> new Tramo(decimal(t.desdeM3()), decimal(t.hastaM3()),
            decimal(t.volumenM3()), decimal(t.precioAguaPorM3()), decimal(t.precioAlcantarilladoPorM3()),
            decimal(t.importeAgua()), decimal(t.importeAlcantarillado()))).toList();
        return new Desglose(decimal(costo.agua()), decimal(costo.alcantarillado()), decimal(costo.cargoFijo()),
            decimal(costo.subtotalConocido()), decimal(costo.igv()), decimal(costo.totalEstimado()), tramos);
    }

    private static String decimal(BigDecimal numero) {
        return numero == null ? null : numero.toPlainString();
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> rangoInvalido() {
        return problema(HttpStatus.BAD_REQUEST, "Rango invalido", "Usa de 1 a 12 meses anteriores.");
    }

    @ExceptionHandler(SuministroNoRegistradoException.class)
    ResponseEntity<ProblemDetail> suministroAusente() {
        return problema(HttpStatus.NOT_FOUND, "Costo no disponible", "El suministro de la sesion no esta registrado.");
    }

    @ExceptionHandler({DataAccessException.class, RepositorioLecturasNoDisponibleException.class, IllegalStateException.class})
    ResponseEntity<ProblemDetail> datosNoDisponibles() {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Costo no disponible",
            "No se pudieron consultar las lecturas y tarifas guardadas.");
    }

    private static ResponseEntity<ProblemDetail> problema(HttpStatus estado, String titulo, String detalle) {
        var cuerpo = ProblemDetail.forStatusAndDetail(estado, detalle);
        cuerpo.setTitle(titulo);
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(cuerpo);
    }

    public record Respuesta(String fuente, String codigoSuministro, String moneda, String zonaHoraria,
            Instant consultadoEn, String revisionTarifaria, PeriodoCosto actual, List<PeriodoCosto> anteriores) {}

    public record PeriodoCosto(String periodo, Instant inicioMes, Instant finMesExclusivo,
            ConsultarCostosConsumo.Estado estado, Consumo consumo, Tarifa tarifa, Desglose desglose) {}

    public record RespuestaProyeccion(String fuente, String codigoSuministro, String moneda,
            String periodo, String zonaHoraria, Instant inicioMes, Instant finMesExclusivo, Instant calculadoEn,
            Supuestos supuestos, ProyeccionMensual.Estado estado, Consumo observado, Actualizacion actualizacion,
            VolumenProyectado proyeccion, CostoProyectado costoProyectado) {}

    public record Supuestos(String metodo, long minimoObservadoSegundos) {}

    public record VolumenProyectado(String litros, String metrosCubicos, String promedioLitrosPorDia,
            String segundosObservados, String segundosDelMes) {}

    public record CostoProyectado(ConsultarCostosConsumo.Estado estado, String revisionTarifaria,
            Tarifa tarifa, Desglose desglose) {}

    public record Consumo(ConsumoPeriodo.Estado estado, ConsumoPeriodo.Motivo motivo, String litros,
            String metrosCubicos, Instant desde, Instant hasta, String idMedidor, String idLecturaInicial,
            String idLecturaFinal) {}

    public record Tarifa(String codigoTarifa, int versionTarifa, String nombreTarifa,
            LocalDate aplicableDesde, LocalDate aplicableHasta, String igvPorcentaje) {}

    public record Desglose(String agua, String alcantarillado, String cargoFijo, String subtotalConocido,
            String igv, String totalEstimado, List<Tramo> tramos) {}

    public record Tramo(String desdeM3, String hastaM3, String volumenM3, String precioAguaPorM3,
            String precioAlcantarilladoPorM3, String importeAgua, String importeAlcantarillado) {}
}
