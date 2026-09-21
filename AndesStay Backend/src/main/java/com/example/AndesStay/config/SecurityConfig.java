package com.example.AndesStay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
            .cors(cors -> {})

            .csrf(csrf -> csrf.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            .authorizeHttpRequests(auth -> auth

                .requestMatchers("/api/catalog/**")
                .hasAnyRole("Admin", "Recepcionista")

                .requestMatchers("/api/reservations/**")
                .hasAnyRole("Admin", "Recepcionista", "Huesped")

                .requestMatchers("/api/reports/kpis")
                .hasRole("Admin")

                .requestMatchers("/api/audit/**")
                .hasAnyRole("Admin", "Auditor")

                .requestMatchers("/api/reports/secure")
                .hasRole("Admin")

                .anyRequest().authenticated()
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

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
            List.of("http://localhost:5173")
        );

        configuration.setAllowedMethods(
            List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
        );

        configuration.setAllowedHeaders(
            List.of("Authorization", "Content-Type")
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

        var roles = jwt.getClaimAsStringList("roles");

        if (roles == null) {
            return java.util.List.of();
        }

        return roles.stream()
                .map(role ->
                    new SimpleGrantedAuthority("ROLE_" + role)
                )
                .collect(Collectors.toList());
    });

    return converter;
    }
}