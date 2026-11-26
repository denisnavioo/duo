package com.pareja.duo;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SeguridadTests {

    @Autowired
    MockMvc mvc;

    @Test
    void sinElegirPersonaSeVaAEntrar() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/entrar"));
        mvc.perform(get("/api/perfil")).andExpect(status().is3xxRedirection());
    }

    @Test
    void laPantallaDeEntradaMuestraALasDos() throws Exception {
        mvc.perform(get("/entrar"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Ana")))
           .andExpect(content().string(containsString("Leo")));
    }

    @Test
    void elegirPersonaAbreLaSesion() throws Exception {
        mvc.perform(post("/entrar").param("quien", "persona-b").with(csrf()))
           .andExpect(redirectedUrl("/"))
           .andExpect(authenticated().withUsername("persona-b"));
    }

    @Test
    void unaPersonaQueNoExisteNoEntra() throws Exception {
        mvc.perform(post("/entrar").param("quien", "intruso").with(csrf()))
           .andExpect(redirectedUrl("/entrar"))
           .andExpect(unauthenticated());
    }

    @Test
    void sinTokenCsrfNoSeEntra() throws Exception {
        mvc.perform(post("/entrar").param("quien", "persona-a")).andExpect(status().isForbidden());
    }

    @Test
    void cabecerasDeSeguridad() throws Exception {
        mvc.perform(get("/entrar"))
           .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
           .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
           .andExpect(header().string("X-Frame-Options", "DENY"))
           .andExpect(header().string("X-Content-Type-Options", "nosniff"))
           .andExpect(header().exists("Referrer-Policy"))
           .andExpect(header().exists("Permissions-Policy"));
    }
}
