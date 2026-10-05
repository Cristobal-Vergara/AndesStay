package com.example.AndesStay.service;

import org.springframework.security.oauth2.jwt.Jwt;
import java.util.Collection;
import java.util.List;

public final class AccountRoles {
    private static final List<String> KNOWN = List.of("Admin", "Recepcionista", "Huesped", "Auditor");
    private AccountRoles() {}

    public static List<String> effectiveRoles(Jwt jwt, boolean registered) {
        Object claim = jwt.getClaim("roles");
        List<String> assigned = claim instanceof Collection<?> values
                ? values.stream().filter(String.class::isInstance).map(String.class::cast)
                    .filter(KNOWN::contains).distinct().toList() : List.of();
        if (registered && canReceiveGuestAccess(jwt)) return List.of("Huesped");
        return assigned;
    }
    public static boolean canReceiveGuestAccess(Jwt jwt) {
        Object roles = jwt.getClaim("roles");
        return (roles == null || roles instanceof Collection<?> values && values.isEmpty())
                && isDelegatedUser(jwt);
    }
    public static boolean isDelegatedUser(Jwt jwt) {
        Object scope = jwt.getClaim("scp");
        Object tenant = jwt.getClaim("tid");
        Object object = jwt.getClaim("oid");
        return scope instanceof String scopes && List.of(scopes.split("\\s+")).contains("access_as_user")
                && tenant instanceof String tid && !tid.isBlank()
                && object instanceof String oid && !oid.isBlank()
                && (tid + ":" + oid).length() <= 255;
    }
    public static String ownerKey(Jwt jwt) {
        if (!isDelegatedUser(jwt)) throw new IllegalArgumentException("El token no identifica una cuenta autorizada.");
        return jwt.getClaimAsString("tid") + ":" + jwt.getClaimAsString("oid");
    }
}
