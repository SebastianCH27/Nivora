package pe.nivora.backend.acceso.administradores;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import pe.nivora.backend.acceso.aplicacion.InicializarAdministracion;
import pe.nivora.backend.acceso.dominio.administradores.Administrador;
import pe.nivora.backend.acceso.dominio.administradores.UsuarioAdministrador;
import pe.nivora.backend.acceso.dominio.contratos.RepositorioAdministradores;
import pe.nivora.backend.acceso.dominio.contratos.ProtectorClavesAdministrativas;
import pe.nivora.backend.acceso.infraestructura.administradores.AdministradoresConfiguracion;
import pe.nivora.backend.acceso.infraestructura.administradores.InicializarAdministracionAlIniciar;
import pe.nivora.backend.acceso.infraestructura.administradores.ProtectorClavesArgon2;

import static org.junit.jupiter.api.Assertions.*;

class InicializadorAdministrativoConfiguracionTests {
    private InicializarAdministracion servicio() {
        return new InicializarAdministracion(repositorio(),
            new ProtectorClavesArgon2(), Clock.systemUTC());
    }

    private RepositorioAdministradores repositorio() {
        return new RepositorioAdministradores() {
            @Override public Administrador crearPrimero(UsuarioAdministrador usuario,
                    String nombre, String hash, Instant ahora) {
                throw new AssertionError("La configuracion no debe crear cuentas.");
            }
            @Override public Optional<Administrador> buscar(UsuarioAdministrador usuario) {
                throw new AssertionError("La configuracion no debe consultar cuentas.");
            }
        };
    }

    @Test
    void sinActivacionExplicitaNoExisteInicializador() {
        new ApplicationContextRunner()
            .withUserConfiguration(InicializarAdministracionAlIniciar.class)
            .withBean(InicializarAdministracion.class, this::servicio)
            .run(contexto -> assertTrue(contexto.getBeansOfType(InicializarAdministracionAlIniciar.class).isEmpty()));
    }

    @Test
    void soloSeActivaExplicitamenteEnAplicacionNoWeb() {
        new ApplicationContextRunner()
            .withPropertyValues("nivora.administradores.inicializar-al-iniciar=true")
            .withUserConfiguration(InicializarAdministracionAlIniciar.class)
            .withBean(InicializarAdministracion.class, this::servicio)
            .run(contexto -> assertEquals(1, contexto.getBeansOfType(InicializarAdministracionAlIniciar.class).size()));
    }

    @Test
    void elServidorWebNoActivaLaInicializacionAunqueSeIndiqueLaPropiedad() {
        new WebApplicationContextRunner()
            .withPropertyValues("nivora.administradores.inicializar-al-iniciar=true")
            .withUserConfiguration(InicializarAdministracionAlIniciar.class)
            .withBean(InicializarAdministracion.class, this::servicio)
            .run(contexto -> assertTrue(contexto.getBeansOfType(InicializarAdministracionAlIniciar.class).isEmpty()));
    }

    @Test
    void claveCortaFueraDeInicializacionSeRechaza() {
        new ApplicationContextRunner().withUserConfiguration(AdministradoresConfiguracion.class)
            .withPropertyValues("nivora.administradores.permitir-clave-corta-inicial=true")
            .withBean(RepositorioAdministradores.class, this::repositorio)
            .withBean(Clock.class, Clock::systemUTC)
            .run(contexto -> assertNotNull(contexto.getStartupFailure()));
    }

    @Test
    void inicializacionNoWebPuedeHabilitarClaveCortaExplicitamente() {
        new ApplicationContextRunner().withUserConfiguration(AdministradoresConfiguracion.class)
            .withPropertyValues("nivora.administradores.permitir-clave-corta-inicial=true",
                "nivora.administradores.inicializar-al-iniciar=true")
            .withBean(RepositorioAdministradores.class, this::repositorio)
            .withBean(Clock.class, Clock::systemUTC)
            .run(contexto -> {
                assertNull(contexto.getStartupFailure());
                var protector = contexto.getBean(ProtectorClavesAdministrativas.class);
                assertTrue(new ProtectorClavesArgon2().coincide("314159".toCharArray(),
                    protector.proteger("314159".toCharArray())));
            });
    }

    @Test
    void servidorWebRechazaElModoDeClaveCorta() {
        new WebApplicationContextRunner().withUserConfiguration(AdministradoresConfiguracion.class)
            .withPropertyValues("nivora.administradores.permitir-clave-corta-inicial=true",
                "nivora.administradores.inicializar-al-iniciar=true")
            .withBean(RepositorioAdministradores.class, this::repositorio)
            .withBean(Clock.class, Clock::systemUTC)
            .run(contexto -> assertNotNull(contexto.getStartupFailure()));
    }
}
