package pe.nivora.backend.acceso.infraestructura.seguridad;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import pe.nivora.backend.acceso.aplicacion.SesionesAdministrativas;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SeguridadConfiguracion {
    @Bean
    SecurityFilterChain seguridad(HttpSecurity http, SesionesAdministrativas sesiones,
            RespuestaSeguridad errores) throws Exception {
        return http
            // La API solo acepta credenciales explicitas Bearer: ni cookies ni Basic.
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(s -> s.securityContextRepository(new NullSecurityContextRepository()))
            .authorizeHttpRequests(a -> a
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/acceso/administradores/sesiones").permitAll()
                .requestMatchers("/api/v1/acceso/administradores/sesion").hasRole("ADMIN")
                .requestMatchers("/api/v1/supervision/**").hasAuthority("SUPERVISAR_INTEGRACION")
                .anyRequest().denyAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((q, r, x) -> errores.escribir(q, r, 401,
                    "Sesion requerida", "Inicia sesion administrativa para realizar esta consulta."))
                .accessDeniedHandler((q, r, x) -> errores.escribir(q, r, 403,
                    "Permiso insuficiente", "La sesion no tiene permiso para realizar esta operacion.")))
            .addFilterBefore(new FiltroSesionAdministrativa(sesiones, errores), AnonymousAuthenticationFilter.class)
            .build();
    }
}
