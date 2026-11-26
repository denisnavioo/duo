package com.pareja.duo;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class AhorrosApiTests {

    @Autowired
    MockMvc mvc;

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String quien, String cuerpo) {
        return b.with(user(quien)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    @Test
    void mesVacioTieneRepartoPorDefecto() throws Exception {
        mvc.perform(get("/api/ahorros/mes/2031-01").with(user("persona-a")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.reparto.ahorro").value(50))
           .andExpect(jsonPath("$.reparto.guardado").value(false))
           .andExpect(jsonPath("$.ingresos.length()").value(0));
    }

    @Test
    void losIngresosSonPrivados() throws Exception {
        mvc.perform(json(post("/api/ahorros/mes/2031-02/ingresos"), "persona-b", "{\"concepto\":\"Sueldo secreto\",\"importe\":812.50}"))
           .andExpect(status().isCreated());

        mvc.perform(get("/api/ahorros/mes/2031-02").with(user("persona-b")))
           .andExpect(jsonPath("$.ingresos[*].concepto", hasItem("Sueldo secreto")));
        mvc.perform(get("/api/ahorros/mes/2031-02").with(user("persona-a")))
           .andExpect(jsonPath("$.ingresos[*].concepto", not(hasItem("Sueldo secreto"))));
    }

    @Test
    void copiarElMesAnterior() throws Exception {
        mvc.perform(json(post("/api/ahorros/mes/2031-03/ingresos"), "persona-a", "{\"concepto\":\"Sueldo\",\"importe\":1000}"))
           .andExpect(status().isCreated());
        mvc.perform(json(put("/api/ahorros/mes/2031-03/reparto"), "persona-a", "{\"ahorro\":65,\"variables\":20,\"fijos\":5,\"ocio\":10}"))
           .andExpect(status().isOk());

        mvc.perform(get("/api/ahorros/mes/2031-04").with(user("persona-a")))
           .andExpect(jsonPath("$.mesAnteriorConDatos").value("2031-03"));

        mvc.perform(json(post("/api/ahorros/mes/2031-04/copiar"), "persona-a", "{}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.ingresos[0].concepto").value("Sueldo"))
           .andExpect(jsonPath("$.reparto.ahorro").value(65))
           .andExpect(jsonPath("$.mesAnteriorConDatos").doesNotExist());
    }

    @Test
    void repartoFueraDeRangoDaError() throws Exception {
        mvc.perform(json(put("/api/ahorros/mes/2031-05/reparto"), "persona-a", "{\"ahorro\":150,\"variables\":20,\"fijos\":5,\"ocio\":10}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void gastosDelMes() throws Exception {
        mvc.perform(json(post("/api/ahorros/gastos"), "persona-b", "{\"fecha\":\"2031-06-07\",\"categoria\":\"Cenas\",\"descripcion\":\"Pizzería\",\"importe\":11}"))
           .andExpect(status().isCreated());
        mvc.perform(get("/api/ahorros/mes/2031-06").with(user("persona-b")))
           .andExpect(jsonPath("$.gastos[0].descripcion").value("Pizzería"));
        mvc.perform(get("/api/ahorros/mes/2031-07").with(user("persona-b")))
           .andExpect(jsonPath("$.gastos.length()").value(0));
    }

    @Test
    void suscripcionTrimestralYUso() throws Exception {
        String r = mvc.perform(json(post("/api/ahorros/suscripciones"), "persona-a",
                "{\"nombre\":\"Transporte\",\"precio\":45.50,\"frecuencia\":\"TRIMESTRAL\",\"cobro\":\"2026-09-13\"}"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.costeMensual").value(15.17))
           .andReturn().getResponse().getContentAsString();
        String id = r.replaceAll(".*\"id\":(\\d+).*", "$1");

        mvc.perform(json(patch("/api/ahorros/suscripciones/" + id), "persona-a", "{\"enUso\":false}"))
           .andExpect(jsonPath("$.enUso").value(false));
        // La otra persona no puede tocarla
        mvc.perform(delete("/api/ahorros/suscripciones/" + id).with(user("persona-b")).with(csrf()))
           .andExpect(status().isNotFound());
    }

    @Test
    void objetivoComunConAportacionesDeLosDos() throws Exception {
        String r = mvc.perform(json(post("/api/ahorros/objetivos"), "persona-a", "{\"nombre\":\"Viaje juntos\",\"meta\":1200}"))
           .andExpect(status().isCreated())
           .andReturn().getResponse().getContentAsString();
        String id = r.replaceAll(".*\"id\":(\\d+).*", "$1");

        mvc.perform(json(post("/api/ahorros/objetivos/" + id + "/aportaciones"), "persona-a", "{\"cantidad\":100}"))
           .andExpect(status().isCreated());
        mvc.perform(json(post("/api/ahorros/objetivos/" + id + "/aportaciones"), "persona-b", "{\"cantidad\":50}"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.aportado").value(150.0));

        mvc.perform(get("/api/ahorros/objetivos").with(user("persona-b")))
           .andExpect(jsonPath("$[*].nombre", hasItem("Viaje juntos")));
    }

    @Test
    void laPaginaDeAhorrosCarga() throws Exception {
        mvc.perform(get("/ahorros").with(user("persona-b")))
           .andExpect(status().isOk());
    }

    @Test
    void importesConMillonesDeDecimalesSeRechazanAlMomento() throws Exception {
        // 1e-600000000 tiene 600 millones de decimales: redondearlo bloquearia el servidor
        mvc.perform(json(post("/api/ahorros/movimientos"), "persona-a", "{\"fecha\":\"2040-01-01\",\"cantidad\":1e-600000000,\"cuenta\":\"x\"}"))
           .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/ahorros/mes/2040-01/ingresos"), "persona-a", "{\"concepto\":\"x\",\"importe\":1e-600000000}"))
           .andExpect(status().isBadRequest());
        // Los ceros de sobra si valen
        mvc.perform(json(post("/api/ahorros/mes/2040-01/ingresos"), "persona-a", "{\"concepto\":\"x\",\"importe\":12.500}"))
           .andExpect(status().isCreated());
    }
}
