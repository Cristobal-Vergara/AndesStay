package com.example.AndesStay.config;
import com.example.AndesStay.controller.UnitController;
import com.example.AndesStay.entity.Unit;
import com.example.AndesStay.service.UnitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UnitController.class)
@Import(SecurityConfig.class)
class CatalogSecurityTests {
    @MockitoBean com.example.AndesStay.repository.UserProfileRepository profiles;
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean UnitService service;
    @BeforeEach void stub() {
        when(service.getUnits(anyInt(),anyInt())).thenReturn(Page.empty());
        when(service.createUnit(any())).thenReturn(new Unit());
    }
    @ParameterizedTest @CsvSource({"Huesped,GET,200","Huesped,POST,403","Huesped,PUT,403","Huesped,DELETE,403",
        "Recepcionista,POST,201","Admin,POST,201","Admin,DELETE,204","Auditor,GET,403"})
    void catalogRoles(String role,String method,int expected) throws Exception {
        authenticate(role);
        var request=switch(method) {
            case "GET"->get("/api/catalog/units");
            case "POST"->post("/api/catalog/units");
            case "PUT"->put("/api/catalog/units/1");
            default->delete("/api/catalog/units/1");
        };
        mvc.perform(request.header("Authorization","Bearer test").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Unidad\",\"capacity\":2}")).andExpect(status().is(expected));
        if(expected==403) verifyNoInteractions(service);
    }
    @Test void fractionalCapacityIsRejectedInsteadOfTruncated() throws Exception {
        authenticate("Admin");
        mvc.perform(post("/api/catalog/units").header("Authorization","Bearer test").contentType(MediaType.APPLICATION_JSON)
            .content("{\"capacity\":2.5}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void listReturnsPaginationMetadata() throws Exception {
        authenticate("Huesped");
        mvc.perform(get("/api/catalog/units").header("Authorization","Bearer test").param("page","2").param("size","10"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray()).andExpect(jsonPath("$.totalElements").value(0));
        verify(service).getUnits(2,10);
    }
    private void authenticate(String role) {
        when(decoder.decode("test")).thenReturn(Jwt.withTokenValue("test").header("alg","none").subject("user").claim("roles",List.of(role)).build());
    }
}
