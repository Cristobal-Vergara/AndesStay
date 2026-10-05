package com.example.andesstay.AndesStayBFF.config;

import com.example.andesstay.AndesStayBFF.service.BffReservationService;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Value("${andesstay.cors.allowed-origins:http://localhost:5173}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        http.csrf(csrf -> csrf.disable()).cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/bff/account/profile").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/bff/account/register").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/bff/reservations/**").hasAnyRole("Admin", "Recepcionista", "Huesped")
                        .requestMatchers(HttpMethod.POST, "/api/bff/reservations").hasAnyRole("Admin", "Recepcionista", "Huesped")
                        .requestMatchers(HttpMethod.PUT, "/api/bff/reservations/**").hasAnyRole("Admin", "Recepcionista")
                        .requestMatchers(HttpMethod.DELETE, "/api/bff/reservations/**").hasAnyRole("Admin", "Recepcionista")
                        .requestMatchers("/api/bff/reservations/**").denyAll()
                        .requestMatchers(HttpMethod.GET, "/api/bff/catalog/**").hasAnyRole("Admin", "Recepcionista", "Huesped")
                        .requestMatchers(HttpMethod.POST, "/api/bff/catalog/**").hasAnyRole("Admin", "Recepcionista")
                        .requestMatchers(HttpMethod.PUT, "/api/bff/catalog/**").hasAnyRole("Admin", "Recepcionista")
                        .requestMatchers(HttpMethod.DELETE, "/api/bff/catalog/**").hasAnyRole("Admin", "Recepcionista")
                        .requestMatchers("/api/bff/catalog/**").denyAll()
                        .requestMatchers(HttpMethod.GET, "/api/bff/messaging/status").hasAnyRole("Admin", "Recepcionista")
                        .requestMatchers(HttpMethod.POST, "/api/bff/messaging/demo").hasRole("Admin")
                        .requestMatchers(HttpMethod.GET, "/api/bff/notifications").hasAnyRole("Admin", "Recepcionista", "Huesped")
                        .requestMatchers(HttpMethod.GET, "/api/bff/account").hasAnyRole("Admin", "Recepcionista", "Huesped")
                        .requestMatchers(HttpMethod.GET, "/api/bff/reports/operations").hasAnyRole("Admin", "Recepcionista")
                        .requestMatchers(HttpMethod.GET, "/api/bff/reports", "/api/bff/reports/secure").hasRole("Admin")
                        .requestMatchers(HttpMethod.GET, "/api/bff/audit", "/api/bff/audit/**").hasAnyRole("Admin", "Auditor")
                        .requestMatchers(HttpMethod.GET, "/api/bff/test").authenticated()
                        .requestMatchers(HttpMethod.GET, "/actuator/info").hasRole("Admin")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(BffReservationService service) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Object claim = jwt.getClaim("roles");
            List<String> roles = claim instanceof Collection<?> values ? values.stream()
                    .filter(String.class::isInstance).map(String.class::cast)
                    .filter(List.of("Admin", "Recepcionista", "Huesped", "Auditor")::contains).distinct().toList() : List.of();
            if ((claim == null || claim instanceof Collection<?> values && values.isEmpty())
                    && isDelegatedUser(jwt) && service.hasRegisteredGuestProfile(jwt.getTokenValue())) {
                roles = List.of("Huesped");
            }
            return roles.stream()
                    .<org.springframework.security.core.GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
        });
        return converter;
    }

    private boolean isDelegatedUser(Jwt jwt) {
        Object scope = jwt.getClaim("scp");
        Object tenant = jwt.getClaim("tid");
        Object object = jwt.getClaim("oid");
        return scope instanceof String scopes && List.of(scopes.split("\\s+")).contains("access_as_user")
                && tenant instanceof String tid && !tid.isBlank()
                && object instanceof String oid && !oid.isBlank()
                && (tid + ":" + oid).length() <= 255;
    }
}
