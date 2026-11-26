package com.pareja.duo;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PerfilTests {

    /** Cabecera minima de un PNG: suficiente para que el servidor lo reconozca. */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};

    @Autowired
    MockMvc mvc;

    @Test
    void subirVerYQuitarFoto() throws Exception {
        mvc.perform(multipart("/api/perfil/foto").file(new MockMultipartFile("foto", "f.png", "image/png", PNG))
                .with(user("persona-b")).with(csrf()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.tieneFoto").value(true));

        // Ana puede ver la foto de Leo
        mvc.perform(get("/usuarios/persona-b/foto").with(user("persona-a")))
           .andExpect(status().isOk())
           .andExpect(content().contentType("image/png"));

        mvc.perform(delete("/api/perfil/foto").with(user("persona-b")).with(csrf()))
           .andExpect(jsonPath("$.tieneFoto").value(false));
        mvc.perform(get("/usuarios/persona-b/foto").with(user("persona-a")))
           .andExpect(status().isNotFound());
    }

    @Test
    void unArchivoQueNoEsImagenSeRechaza() throws Exception {
        mvc.perform(multipart("/api/perfil/foto").file(new MockMultipartFile("foto", "virus.png", "image/png", "<script>".getBytes()))
                .with(user("persona-a")).with(csrf()))
           .andExpect(status().isBadRequest());
    }

    @Test
    void laFotoNoSeVeSinIniciarSesion() throws Exception {
        mvc.perform(get("/usuarios/persona-a/foto"))
           .andExpect(status().is3xxRedirection());
    }

    @Test
    void cambiarNombre() throws Exception {
        mvc.perform(put("/api/perfil").with(user("persona-a")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Den\"}"))
           .andExpect(jsonPath("$.nombre").value("Den"))
           .andExpect(jsonPath("$.inicial").value("D"));
        // Lo dejamos como estaba
        mvc.perform(put("/api/perfil").with(user("persona-a")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Ana\"}"))
           .andExpect(status().isOk());
    }
}
