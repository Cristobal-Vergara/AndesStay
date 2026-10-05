package com.example.AndesStay.config;

import com.example.AndesStay.controller.UserProfileController;
import com.example.AndesStay.repository.UserProfileRepository;
import com.example.AndesStay.service.ReservationAccess;
import com.example.AndesStay.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserProfileController.class)
@Import({SecurityConfig.class, UserProfileService.class, ReservationAccess.class})
class UserProfileSecurityTests {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean UserProfileRepository profiles;
    @MockitoBean Clock clock;
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-10-04T12:00:00Z"));
        when(profiles.findByOwnerKey(anyString())).thenReturn(Optional.empty());
        when(profiles.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        authenticate();
    }
    @Test void anonymousCannotReadOrCreateProfiles() throws Exception {
        mvc.perform(get("/api/account/profile")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/account/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"displayName\":\"Ana\"}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles);
    }
    @Test void invalidTokenCannotRegister() throws Exception {
        when(decoder.decode("test")).thenThrow(new BadJwtException("invalid"));
        mvc.perform(post("/api/account/register").header("Authorization","Bearer test")
            .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Ana\"}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles);
    }
    @Test void identityWithoutRolesRegistersOnlyAsGuestAndCannotManageUnits() throws Exception {
        mvc.perform(get("/api/reservations").header("Authorization","Bearer test")).andExpect(status().isForbidden());
        mvc.perform(post("/api/account/register").header("Authorization","Bearer test")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"displayName\":\"  Ana Torres  \",\"email\":\"forged@test\",\"ownerKey\":\"other\",\"roles\":[\"Admin\"]}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.registered").value(true))
            .andExpect(jsonPath("$.displayName").value("Ana Torres"))
            .andExpect(jsonPath("$.email").value("verified@example.test"))
            .andExpect(jsonPath("$.ownerKey").doesNotExist()).andExpect(jsonPath("$.roles[0]").value("Huesped"))
            .andExpect(jsonPath("$.roles.length()").value(1));
        verify(profiles).saveAndFlush(argThat(p -> p.getOwnerKey().equals("tenant:object")
            && p.getEmail().equals("verified@example.test")));
        mvc.perform(post("/api/catalog/units").header("Authorization","Bearer test")
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }
    @Test void getUsesCurrentIdentityAndIgnoresOtherAccountParameters() throws Exception {
        mvc.perform(get("/api/account/profile").header("Authorization","Bearer test").param("ownerKey","victim"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.registered").value(false))
            .andExpect(jsonPath("$.email").value("verified@example.test"));
        verify(profiles).findByOwnerKey("tenant:object");
        verify(profiles,never()).findByOwnerKey("victim");
    }
    @Test void verifiedTokenWithoutStableMicrosoftIdentityIsRejected() throws Exception {
        when(decoder.decode("test")).thenReturn(Jwt.withTokenValue("test").header("alg","RS256").subject("object").build());
        mvc.perform(get("/api/account/profile").header("Authorization","Bearer test")).andExpect(status().isForbidden());
        verifyNoInteractions(profiles);
    }
    @Test void applicationTokenWithoutDelegatedScopeCannotReadOrCreateProfile() throws Exception {
        when(decoder.decode("test")).thenReturn(Jwt.withTokenValue("test").header("alg","RS256").subject("object")
            .claim("tid","tenant").claim("oid","object").build());
        mvc.perform(get("/api/account/profile").header("Authorization","Bearer test")).andExpect(status().isForbidden());
        mvc.perform(post("/api/account/register").header("Authorization","Bearer test")
            .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Ana\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(profiles);
    }
    @Test void malformedAndMissingNamesReturnBadRequestWithoutWriting() throws Exception {
        for(String body : new String[]{"{", "{}", "{\"displayName\":\" \"}"}) {
            mvc.perform(post("/api/account/register").header("Authorization","Bearer test")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verify(profiles,never()).saveAndFlush(any());
    }
    @Test void concurrentDuplicateConflictDoesNotExposeSql() throws Exception {
        when(profiles.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("SQL secret"));
        mvc.perform(post("/api/account/register").header("Authorization","Bearer test")
            .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Ana\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(
                "No se pudo crear el perfil. Actualiza la página para comprobar si ya está registrado."));
    }
    private void authenticate() {
        when(decoder.decode("test")).thenReturn(Jwt.withTokenValue("test").header("alg","RS256").subject("object")
            .claim("tid","tenant").claim("oid","object").claim("scp","access_as_user")
            .claim("preferred_username","verified@example.test").claim("name","Ana").build());
    }
}
