package com.example.andesstay.AndesStayBFF.config;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .cors(cors ->
                cors.configurationSource(
                    corsConfigurationSource()
                )
            )

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                
                // Health público
                .requestMatchers("/actuator/health")
                .permitAll()

                // Endpoint de prueba
                .requestMatchers("/api/bff/test")
                .authenticated()

                // Reservas
                .requestMatchers("/api/bff/reservations/**")
                .hasAnyRole(
                    "Admin",
                    "Recepcionista",
                    "Huesped"
                )

                // Catálogo
                .requestMatchers("/api/bff/catalog")
                .hasAnyRole(
                    "Admin",
                    "Recepcionista"
                )

                // Reportería
                .requestMatchers("/api/bff/reports")
                .hasRole("Admin")

                // Auditoría
                .requestMatchers("/api/bff/audit")
                .hasAnyRole(
                    "Admin",
                    "Auditor"
                )

                // Cualquier otro endpoint requiere autenticación
                .anyRequest()
                .authenticated()
            )

            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt ->
                    jwt.jwtAuthenticationConverter(
                        jwtAuthenticationConverter()
                    )
                )
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of("http://localhost:5173")
        );

        configuration.setAllowedMethods(
                List.of(
                    "GET",
                    "POST",
                    "PUT",
                    "DELETE",
                    "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of(
                    "Authorization",
                    "Content-Type"
                )
        );

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            var roles =
                    jwt.getClaimAsStringList("roles");

            if (roles == null) {
                return List.of();
            }

            return roles.stream()
                    .map(role ->
                        new SimpleGrantedAuthority(
                            "ROLE_" + role
                        )
                    )
                    .collect(Collectors.toList());
        });

        return converter;
    }
}