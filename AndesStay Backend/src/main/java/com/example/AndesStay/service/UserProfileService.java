package com.example.AndesStay.service;

import com.example.AndesStay.entity.UserProfile;
import com.example.AndesStay.repository.UserProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class UserProfileService {
    public record Profile(boolean registered, String displayName, String email, Instant createdAt, List<String> roles) {}
    private final UserProfileRepository profiles;
    private final ReservationAccess access;
    private final Clock clock;

    public UserProfileService(UserProfileRepository profiles, ReservationAccess access, Clock clock) {
        this.profiles = profiles; this.access = access; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public Profile get(Jwt jwt) {
        return profiles.findByOwnerKey(identity(jwt)).map(profile -> view(profile, jwt))
                .orElseGet(() -> new Profile(false, claim(jwt, "name"), email(jwt), null, AccountRoles.effectiveRoles(jwt, false)));
    }
    @Transactional
    public Profile register(String displayName, Jwt jwt) {
        String key = identity(jwt);
        if (displayName == null || displayName.strip().length() < 2 || displayName.strip().length() > 120
                || displayName.codePoints().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica un nombre de entre 2 y 120 caracteres.");
        }
        if (profiles.findByOwnerKey(key).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tu cuenta ya tiene un perfil en AndesStay.");
        }
        String email = email(jwt);
        if (email.length() > 320) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El correo de la cuenta Microsoft supera la longitud permitida.");
        }
        return view(profiles.saveAndFlush(new UserProfile(key, displayName.strip(), email, clock.instant())), jwt);
    }
    private String identity(Jwt jwt) {
        if (!AccountRoles.isDelegatedUser(jwt)) {
            throw new AccessDeniedException("La cuenta Microsoft debe autorizar el acceso a AndesStay.");
        }
        return access.currentIdentityId();
    }
    private Profile view(UserProfile profile, Jwt jwt) {
        return new Profile(true, profile.getDisplayName(), profile.getEmail(), profile.getCreatedAt(),
                AccountRoles.effectiveRoles(jwt, true));
    }
    private String email(Jwt jwt) {
        for (String name : new String[]{"preferred_username", "email", "upn"}) {
            String value = claim(jwt, name);
            if (!value.isBlank()) return value;
        }
        return "";
    }
    private String claim(Jwt jwt, String name) {
        Object value = jwt.getClaim(name);
        return value instanceof String text ? text : "";
    }
}
