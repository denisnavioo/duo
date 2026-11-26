package com.pareja.duo;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class HorarioApiTests {

    @Autowired
    MockMvc mvc;

    @Test
    void crearVariosDiasYQueLoVeaElOtro() throws Exception {
        mvc.perform(post("/api/horarios").with(user("persona-a")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dias\":[1,2,3,4,5],\"inicio\":\"15:30\",\"fin\":\"19:30\",\"tipo\":\"PRACTICAS\",\"titulo\":\"Prácticas test\"}"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.length()").value(5));

        mvc.perform(get("/api/horarios").with(user("persona-b")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[*].titulo", hasItem("Prácticas test")));
    }

    @Test
    void finAntesQueInicioDaError() throws Exception {
        mvc.perform(post("/api/horarios").with(user("persona-a")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dias\":[1],\"inicio\":\"12:00\",\"fin\":\"11:00\",\"tipo\":\"CLASE\",\"titulo\":\"Mal\"}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void soloElDuenoPuedeEditarYBorrar() throws Exception {
        String r = mvc.perform(post("/api/horarios").with(user("persona-b")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dias\":[3],\"inicio\":\"09:00\",\"fin\":\"14:00\",\"tipo\":\"TRABAJO\",\"titulo\":\"Trabajo test\"}"))
           .andExpect(status().isCreated())
           .andReturn().getResponse().getContentAsString();
        String id = r.replaceAll(".*\"id\":(\\d+).*", "$1");

        mvc.perform(put("/api/horarios/" + id).with(user("persona-a")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dia\":3,\"inicio\":\"09:00\",\"fin\":\"10:00\",\"tipo\":\"TRABAJO\",\"titulo\":\"Hackeado\"}"))
           .andExpect(status().isNotFound());
        mvc.perform(delete("/api/horarios/" + id).with(user("persona-a")).with(csrf()))
           .andExpect(status().isNotFound());

        mvc.perform(put("/api/horarios/" + id).with(user("persona-b")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dia\":4,\"inicio\":\"10:00\",\"fin\":\"14:00\",\"tipo\":\"TRABAJO\",\"titulo\":\"Trabajo cambiado\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.dia").value(4));
        mvc.perform(delete("/api/horarios/" + id).with(user("persona-b")).with(csrf()))
           .andExpect(status().isNoContent());
    }

    @Test
    void laPaginaDeHorariosCarga() throws Exception {
        mvc.perform(get("/horarios").with(user("persona-a"))).andExpect(status().isOk());
    }
}
