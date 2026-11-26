package com.pareja.duo;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class NotaDiariaTests {

    @Autowired
    MockMvc mvc;

    private ResultActions enviar(String usuario, String texto) throws Exception {
        return mvc.perform(post("/api/notas/hoy").with(user(usuario)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"" + texto + "\"}"));
    }

    @Test
    void soloUnaAlDiaYLaLeeElOtro() throws Exception {
        enviar("persona-a", "Suerte en el examen").andExpect(status().isOk());

        mvc.perform(get("/api/notas/hoy").with(user("persona-a")))
           .andExpect(jsonPath("$.mia.texto").value("Suerte en el examen"));
        mvc.perform(get("/api/notas/hoy").with(user("persona-b")))
           .andExpect(jsonPath("$.delOtro.texto").value("Suerte en el examen"))
           .andExpect(jsonPath("$.delOtro.autor").value("persona-a"));

        // Una vez enviada no se puede mandar otra ni cambiarla
        enviar("persona-a", "Cenamos fuera").andExpect(status().isConflict());
        mvc.perform(put("/api/notas/hoy").with(user("persona-a")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"cambiada\"}"))
           .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/notas/hoy").with(user("persona-b")))
           .andExpect(jsonPath("$.delOtro.texto").value("Suerte en el examen"));
    }

    @Test
    void notaVaciaNoSeEnvia() throws Exception {
        enviar("persona-b", "   ").andExpect(status().isBadRequest());
    }

    @Test
    void sinCsrfNoSeGuarda() throws Exception {
        mvc.perform(post("/api/notas/hoy").with(user("persona-b")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"hola\"}"))
           .andExpect(status().isForbidden());
    }
}
