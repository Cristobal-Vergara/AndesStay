package com.example.AndesStay.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class ReservationAccess {

    public boolean isStaff() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && (hasRole(authentication, "Admin") || hasRole(authentication, "Recepcionista"));
    }

    public void requireStaff() {
        if (!isStaff()) {
            throw new AccessDeniedException("La operación requiere administración o recepción");
        }
    }

    public String currentOwnerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(isStaff() || hasRole(authentication, "Huesped"))) {
            throw new AccessDeniedException("No se puede identificar al propietario de la reserva");
        }
        return currentIdentityId();
    }

    public String currentIdentityId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication) || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Microsoft debe verificar tu identidad para registrar el perfil");
        }
        String tenantId = jwtAuthentication.getToken().getClaimAsString("tid");
        String objectId = jwtAuthentication.getToken().getClaimAsString("oid");

        if (tenantId == null || tenantId.isBlank() || objectId == null || objectId.isBlank()) {
            throw new AccessDeniedException("El token debe identificar al usuario y su organización");
        }

        return tenantId + ":" + objectId;
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }
}
