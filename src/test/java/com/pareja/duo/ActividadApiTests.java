package com.pareja.duo;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.pareja.duo.actividad.TipoPlanRepository;

@SpringBootTest
@AutoConfigureMockMvc
class ActividadApiTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    TipoPlanRepository tipos;

    @Test
    void hayTiposDePlanIniciales() throws Exception {
        mvc.perform(get("/api/tipos").with(user("persona-a")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(greaterThan(5)))
           .andExpect(jsonPath("$[*].nombre", hasItem("Cena o comida")));
    }

    @Test
    void crearActividadJuntos() throws Exception {
        Long tipoId = tipos.findAllByOrderByOrdenAscIdAsc().get(0).getId();
        String json = """
            {"tipoId": %d, "titulo": "Cena de prueba", "fecha": "2030-01-15", "hora": "21:30",
             "quien": "juntos", "lugar": "Casa"}
            """.formatted(tipoId);
        mvc.perform(post("/api/actividades").with(user("persona-a")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.titulo").value("Cena de prueba"))
           .andExpect(jsonPath("$.hora").value("21:30"));

        mvc.perform(get("/api/actividades").with(user("persona-b")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[*].titulo", hasItem("Cena de prueba")));
    }

    @Test
    void sinDetalleUsaElNombreDelTipo() throws Exception {
        Long tipoId = tipos.findAllByOrderByOrdenAscIdAsc().get(1).getId();
        String json = """
            {"tipoId": %d, "fecha": "2030-02-01", "hora": "18:00", "quien": "persona-b"}
            """.formatted(tipoId);
        mvc.perform(post("/api/actividades").with(user("persona-a")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.titulo").value("Cine o series"));
    }

    @Test
    void participanteDesconocidoDaError() throws Exception {
        Long tipoId = tipos.findAllByOrderByOrdenAscIdAsc().get(0).getId();
        String json = """
            {"tipoId": %d, "fecha": "2030-01-15", "hora": "21:30", "quien": "pepe"}
            """.formatted(tipoId);
        mvc.perform(post("/api/actividades").with(user("persona-a")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
           .andExpect(status().isBadRequest());
    }

    @Test
    void crearTipoDePlanPropio() throws Exception {
        mvc.perform(post("/api/tipos").with(user("persona-b")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": \"Concierto\", \"icono\": \"musica\"}"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.propio").value(true));

        mvc.perform(get("/api/tipos").with(user("persona-a")))
           .andExpect(jsonPath("$[*].nombre", hasItem("Concierto")))
           .andExpect(jsonPath("$[-1].nombre").value("Otro"));
    }

    private long crearJuntos(String creador, String titulo) throws Exception {
        Long tipoId = tipos.findAllByOrderByOrdenAscIdAsc().get(0).getId();
        String json = """
            {"tipoId": %d, "titulo": "%s", "fecha": "2030-03-10", "hora": "20:00", "quien": "juntos"}
            """.formatted(tipoId, titulo);
        String respuesta = mvc.perform(post("/api/actividades").with(user(creador)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.estado").value("PENDIENTE"))
           .andExpect(jsonPath("$.creadoPor").value(creador))
           .andReturn().getResponse().getContentAsString();
        return Long.parseLong(respuesta.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void elOtroAceptaUnPlanJuntos() throws Exception {
        long id = crearJuntos("persona-a", "Plan para aceptar");

        // Quien lo crea no puede responderse a si mismo
        mvc.perform(post("/api/actividades/" + id + "/responder").with(user("persona-a")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"aceptar\": true}"))
           .andExpect(status().isForbidden());

        mvc.perform(post("/api/actividades/" + id + "/responder").with(user("persona-b")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"aceptar\": true}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.estado").value("ACEPTADA"));

        // Ya no esta pendiente
        mvc.perform(post("/api/actividades/" + id + "/responder").with(user("persona-b")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"aceptar\": false}"))
           .andExpect(status().isConflict());
    }

    @Test
    void elOtroDeniegaYElCreadorLoDescarta() throws Exception {
        long id = crearJuntos("persona-b", "Plan para denegar");

        mvc.perform(post("/api/actividades/" + id + "/responder").with(user("persona-a")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"aceptar\": false}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.estado").value("RECHAZADA"));

        // Solo quien lo creo puede quitar el aviso
        mvc.perform(post("/api/actividades/" + id + "/descartar").with(user("persona-a")).with(csrf()))
           .andExpect(status().isForbidden());
        mvc.perform(post("/api/actividades/" + id + "/descartar").with(user("persona-b")).with(csrf()))
           .andExpect(status().isNoContent());
    }

    @Test
    void unPlanIndividualNoQuedaPendiente() throws Exception {
        Long tipoId = tipos.findAllByOrderByOrdenAscIdAsc().get(0).getId();
        String json = """
            {"tipoId": %d, "fecha": "2030-04-01", "hora": "10:00", "quien": "persona-a"}
            """.formatted(tipoId);
        mvc.perform(post("/api/actividades").with(user("persona-a")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.estado").value("ACEPTADA"));
    }

    private long crear(String creador, String quien, String titulo) throws Exception {
        Long tipoId = tipos.findAllByOrderByOrdenAscIdAsc().get(0).getId();
        String json = """
            {"tipoId": %d, "titulo": "%s", "fecha": "2030-05-10", "hora": "19:00", "quien": "%s",
             "descripcion": "Mesa reservada"}
            """.formatted(tipoId, titulo, quien);
        String respuesta = mvc.perform(post("/api/actividades").with(user(creador)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.descripcion").value("Mesa reservada"))
           .andReturn().getResponse().getContentAsString();
        return Long.parseLong(respuesta.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void editarUnPlanCambiaSusDatos() throws Exception {
        long id = crear("persona-a", "juntos", "Plan para editar");
        Long otroTipo = tipos.findAllByOrderByOrdenAscIdAsc().get(2).getId();
        String cambio = """
            {"tipoId": %d, "titulo": "Plan editado", "fecha": "2030-05-11", "hora": "20:15",
             "lugar": "Centro", "descripcion": "Entradas en https://ejemplo.test/x"}
            """.formatted(otroTipo);
        // Un plan de los dos lo puede cambiar cualquiera de los dos
        mvc.perform(put("/api/actividades/" + id).with(user("persona-b")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(cambio))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.titulo").value("Plan editado"))
           .andExpect(jsonPath("$.fecha").value("2030-05-11"))
           .andExpect(jsonPath("$.hora").value("20:15"))
           .andExpect(jsonPath("$.tipoId").value(otroTipo))
           .andExpect(jsonPath("$.descripcion").value("Entradas en https://ejemplo.test/x"))
           .andExpect(jsonPath("$.quien").value("juntos"));
    }

    @Test
    void unPlanSoloDelOtroNoSePuedeCambiarNiBorrar() throws Exception {
        long id = crear("persona-b", "persona-b", "Plan de Leo");
        Long tipoId = tipos.findAllByOrderByOrdenAscIdAsc().get(0).getId();
        mvc.perform(put("/api/actividades/" + id).with(user("persona-a")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipoId\": %d, \"fecha\": \"2030-05-10\", \"hora\": \"19:00\"}".formatted(tipoId)))
           .andExpect(status().isForbidden());
        mvc.perform(delete("/api/actividades/" + id).with(user("persona-a")).with(csrf()))
           .andExpect(status().isForbidden());
    }

    @Test
    void borrarUnPlanConSusFotos() throws Exception {
        long id = crear("persona-a", "persona-a", "Plan con foto");
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
        String respuesta = mvc.perform(multipart("/api/actividades/" + id + "/fotos")
                .file(new MockMultipartFile("foto", "foto.jpg", "image/jpeg", jpg))
                .with(user("persona-a")).with(csrf()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.fotos.length()").value(1))
           .andReturn().getResponse().getContentAsString();
        long fotoId = Long.parseLong(respuesta.replaceAll(".*\"fotos\":\\[(\\d+)\\].*", "$1"));

        mvc.perform(get("/api/actividades/fotos/" + fotoId).with(user("persona-b")))
           .andExpect(status().isOk())
           .andExpect(content().contentType("image/jpeg"));

        // Un archivo que no es imagen se rechaza
        mvc.perform(multipart("/api/actividades/" + id + "/fotos")
                .file(new MockMultipartFile("foto", "x.jpg", "image/jpeg", "no soy una foto".getBytes()))
                .with(user("persona-a")).with(csrf()))
           .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/actividades/" + id).with(user("persona-a")).with(csrf()))
           .andExpect(status().isNoContent());
        mvc.perform(get("/api/actividades/fotos/" + fotoId).with(user("persona-a")))
           .andExpect(status().isNotFound());
    }

    @Test
    void laPaginaUsaElTemaDeQuienEntra() throws Exception {
        mvc.perform(get("/").with(user("persona-b")))
           .andExpect(status().isOk())
           .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"persona-b\"")));
    }
}
