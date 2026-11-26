package com.pareja.duo;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class NovedadesTests {

    @Autowired
    MockMvc mvc;

    @Test
    void loQueAnadeUnoLeSaleAlOtroComoNoLeido() throws Exception {
        mvc.perform(post("/api/novedades/vistas").with(user("persona-b")).with(csrf()))
           .andExpect(status().isNoContent());

        mvc.perform(post("/api/horarios").with(user("persona-a")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dias\":[6],\"inicio\":\"10:00\",\"fin\":\"11:00\",\"tipo\":\"OTRO\",\"titulo\":\"Novedad test\"}"))
           .andExpect(status().isCreated());

        mvc.perform(get("/api/novedades").with(user("persona-b")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.noLeidas").value(greaterThan(0)))
           .andExpect(jsonPath("$.novedades[*].texto", hasItem(containsString("Novedad test"))));

        // Al abrir la campanita se marcan como vistas
        mvc.perform(post("/api/novedades/vistas").with(user("persona-b")).with(csrf()));
        mvc.perform(get("/api/novedades").with(user("persona-b")))
           .andExpect(jsonPath("$.noLeidas").value(0));
    }

    @Test
    void lasFinanzasPersonalesNoLasVeElOtro() throws Exception {
        mvc.perform(post("/api/ahorros/mes/2032-01/ingresos").with(user("persona-a")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"concepto\":\"Ingreso secreto test\",\"importe\":10}"))
           .andExpect(status().isCreated());

        mvc.perform(get("/api/novedades").with(user("persona-a")))
           .andExpect(jsonPath("$.novedades[*].texto", hasItem(containsString("Ingreso secreto test"))));
        mvc.perform(get("/api/novedades").with(user("persona-b")))
           .andExpect(jsonPath("$.novedades[*].texto", not(hasItem(containsString("Ingreso secreto test")))));
    }
}
