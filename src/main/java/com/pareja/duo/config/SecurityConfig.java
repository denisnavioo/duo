package com.pareja.duo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Duo no usa contraseñas: al entrar eliges quién eres (una de las dos personas) y la sesión
 * recuerda tu elección. Spring Security se mantiene para la sesión, la protección CSRF y las
 * cabeceras de seguridad.
 *
 * Como cualquiera que abra la web puede elegir persona, Duo solo escucha en este ordenador
 * (server.address=127.0.0.1).
 */
@Configuration
public class SecurityConfig {

    /** Que puede cargar la web: solo cosas propias, mas las fuentes de Google. */
    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com",
            "img-src 'self' data: blob:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/entrar", "/css/**", "/js/**", "/favicon.svg", "/logo-duo.svg", "/error",
                        // Para instalar Duo en el movil y recibir avisos (no contienen datos)
                        "/sw.js", "/manifest.webmanifest", "/icono-192.png", "/icono-512.png", "/icono-insignia.png",
                        "/apple-touch-icon.png").permitAll()
                .anyRequest().authenticated())
            // Sin sesion, a elegir persona
            .exceptionHandling(e -> e.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/entrar")))
            .logout(logout -> logout
                .logoutSuccessUrl("/entrar")
                .deleteCookies("JSESSIONID")
                .permitAll())
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                .referrerPolicy(r -> r.policy(ReferrerPolicy.SAME_ORIGIN))
                .permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
                .frameOptions(f -> f.deny()));
        return http.build();
    }

    /** No hay usuarios con contraseña; evita que Spring Boot cree uno de ejemplo. */
    @Bean
    UserDetailsService sinContrasenas() {
        return username -> {
            throw new UsernameNotFoundException("Duo no usa contraseñas");
        };
    }
}
