package com.example.AndesStay.config;

import com.example.AndesStay.controller.ReservationController;
import com.example.AndesStay.entity.Reservation;
import com.example.AndesStay.repository.UserProfileRepository;
import com.example.AndesStay.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReservationController.class)
@Import(SecurityConfig.class)
class PublicGuestSecurityTests {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean UserProfileRepository profiles;
    @MockitoBean ReservationService service;
    @BeforeEach void setup() {
        when(profiles.existsByOwnerKey("tenant:customer")).thenReturn(true);
        when(service.getReservations(anyInt(),anyInt())).thenReturn(org.springframework.data.domain.Page.empty());
        when(service.createReservation(any())).thenReturn(new Reservation());
        authenticate("access_as_user",null,"customer");
    }
    @Test void registeredDelegatedIdentityWithoutEntraRolesCanReadAndCreateOwnReservations() throws Exception {
        mvc.perform(get("/api/v1/reservations").header("Authorization","Bearer public")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/reservations").header("Authorization","Bearer public")
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        verify(profiles,times(2)).existsByOwnerKey("tenant:customer");
    }
    @Test void unregisteredIdentityDoesNotGetGuestAccess() throws Exception {
        when(profiles.existsByOwnerKey("tenant:customer")).thenReturn(false);
        mvc.perform(get("/api/v1/reservations").header("Authorization","Bearer public")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"","other_scope","access_as_user_extra"})
    void absentOrWrongDelegatedPermissionCannotGetAutomaticGuestAccess(String scope) throws Exception {
        authenticate(scope,null,"customer");
        mvc.perform(get("/api/v1/reservations").header("Authorization","Bearer public")).andExpect(status().isForbidden());
        verifyNoInteractions(profiles,service);
    }
    @Test void anotherUserCannotUseAnExistingProfile() throws Exception {
        authenticate("access_as_user",null,"another");
        mvc.perform(get("/api/v1/reservations").header("Authorization","Bearer public")).andExpect(status().isForbidden());
        verify(profiles).existsByOwnerKey("tenant:another"); verifyNoInteractions(service);
    }
    @Test void auditorDoesNotGainGuestAccessThroughRegistration() throws Exception {
        authenticate("access_as_user","Auditor","customer");
        mvc.perform(get("/api/v1/reservations").header("Authorization","Bearer public")).andExpect(status().isForbidden());
        verifyNoInteractions(profiles,service);
    }
    @Test void guestCannotUseAnyPrivilegedOperation() throws Exception {
        for(String path:List.of("/api/v1/reservations/1","/api/v1/reservations/1/status","/api/v1/reservations/1/owner","/api/v1/catalog/units/1")) {
            mvc.perform(put(path).header("Authorization","Bearer public").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        }
        for(String path:List.of("/api/v1/reservations/1","/api/v1/catalog/units/1"))
            mvc.perform(delete(path).header("Authorization","Bearer public")).andExpect(status().isForbidden());
        for(String path:List.of("/api/v1/reports/kpis","/api/v1/audit","/actuator/info"))
            mvc.perform(get(path).header("Authorization","Bearer public")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    private void authenticate(String scope,String role,String oid) {
        var builder=Jwt.withTokenValue("public").header("alg","RS256").subject(oid).claim("tid","tenant").claim("oid",oid);
        if(!scope.isEmpty()) builder.claim("scp",scope);
        if(role!=null) builder.claim("roles",List.of(role));
        when(decoder.decode("public")).thenReturn(builder.build());
    }
}
