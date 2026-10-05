package com.example.AndesStay.config;

import com.example.AndesStay.repository.UserProfileRepository;
import com.example.AndesStay.service.AccountRoles;
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
                        .requestMatchers(HttpMethod.GET, "/api/account/profile").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/account/register").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/reservations/**").hasAnyRole("Admin", "Recepcionista", "Huesped")
                        .requestMatchers(HttpMethod.POST, "/api/reservations").hasAnyRole("Admin", "Recepcionista", "Huesped")
                        .requestMatchers(HttpMethod.PUT, "/api/reservations/**").hasAnyRole("Admin", "Recepcionista")
                        .requestMatchers(HttpMethod.DELETE, "/api/reservations/**").hasAnyRole("Admin", "Recepcionista")
                        .requestMatchers("/api/reservations/**").denyAll()
                        .requestMatchers(HttpMethod.GET, "/api/catalog/**").hasAnyRole("Admin", "Recepcionista", "Huesped")
                        .requestMatchers(HttpMethod.POST, "/api/catalog/**").hasAnyRole("Admin", "Recepcionista")
                        .requestMatchers(HttpMethod.PUT, "/api/catalog/**").hasAnyRole("Admin", "Recepcionista")
                        .requestMatchers(HttpMethod.DELETE, "/api/catalog/**").hasAnyRole("Admin", "Recepcionista")
                        .requestMatchers("/api/catalog/**").denyAll()
                        .requestMatchers(HttpMethod.GET, "/api/messaging/status").hasAnyRole("Admin", "Recepcionista")
                        .requestMatchers(HttpMethod.POST, "/api/messaging/demo").hasRole("Admin")
                        .requestMatchers(HttpMethod.GET, "/api/notifications").hasAnyRole("Admin", "Recepcionista", "Huesped")
                        .requestMatchers(HttpMethod.GET, "/api/account").hasAnyRole("Admin", "Recepcionista", "Huesped")
                        .requestMatchers(HttpMethod.GET, "/api/reports/operations").hasAnyRole("Admin", "Recepcionista")
                        .requestMatchers(HttpMethod.GET, "/api/reports/kpis", "/api/reports/secure").hasRole("Admin")
                        .requestMatchers(HttpMethod.GET, "/api/audit", "/api/audit/**").hasAnyRole("Admin", "Auditor")

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
    public JwtAuthenticationConverter jwtAuthenticationConverter(UserProfileRepository profiles) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            boolean registered = AccountRoles.canReceiveGuestAccess(jwt)
                    && profiles.existsByOwnerKey(AccountRoles.ownerKey(jwt));
            return AccountRoles.effectiveRoles(jwt, registered).stream()
                    .<org.springframework.security.core.GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
        });
        return converter;
    }
}
