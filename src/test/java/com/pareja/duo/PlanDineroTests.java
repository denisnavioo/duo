package com.pareja.duo;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/** Vista "Mi plan" de Ahorros: saldos, ingreso mensual, apartados y vista elegida. */
@SpringBootTest
@AutoConfigureMockMvc
class PlanDineroTests {

    @Autowired
    MockMvc mvc;

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String quien, String cuerpo) {
        return b.with(user(quien)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    @Test
    void sinGuardarNadaProponeUnosApartados() throws Exception {
        mvc.perform(get("/api/ahorros/plan").with(user("usuario-plan-nuevo")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.vista").value("MES"))
           .andExpect(jsonPath("$.ingresoMensual").value(0))
           .andExpect(jsonPath("$.apartadosGuardados").value(false))
           .andExpect(jsonPath("$.apartados[0].nombre").value("Ahorro"))
           .andExpect(jsonPath("$.apartados[0].ahorro").value(true));
    }

    @Test
    void seGuardanElIngresoYLaVista() throws Exception {
        mvc.perform(json(put("/api/ahorros/plan"), "persona-a", "{\"ingresoMensual\":1450.5,\"vista\":\"PLAN\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.ingresoMensual").value(1450.5))
           .andExpect(jsonPath("$.vista").value("PLAN"));

        mvc.perform(json(put("/api/ahorros/plan"), "persona-a", "{\"vista\":\"OTRA\"}"))
           .andExpect(status().isBadRequest());
        mvc.perform(json(put("/api/ahorros/plan"), "persona-a", "{\"ingresoMensual\":-5}"))
           .andExpect(status().isBadRequest());
        mvc.perform(json(put("/api/ahorros/plan"), "persona-a", "{\"ingresoMensual\":0.001}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void ahoraNoCobroSeGuardaSinPerderElIngreso() throws Exception {
        mvc.perform(json(put("/api/ahorros/plan"), "usuario-sin-cobrar", "{\"ingresoMensual\":1300}"))
           .andExpect(jsonPath("$.sinIngresos").value(false));
        mvc.perform(json(put("/api/ahorros/plan"), "usuario-sin-cobrar", "{\"sinIngresos\":true}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.sinIngresos").value(true))
           .andExpect(jsonPath("$.ingresoMensual").value(1300));
        mvc.perform(json(put("/api/ahorros/plan"), "usuario-sin-cobrar", "{\"sinIngresos\":false}"))
           .andExpect(jsonPath("$.sinIngresos").value(false))
           .andExpect(jsonPath("$.ingresoMensual").value(1300));
    }

    @Test
    void losSaldosSonPrivadosYSoloLosCambiaSuDueno() throws Exception {
        String cuerpo = mvc.perform(json(post("/api/ahorros/plan/saldos"), "persona-b", "{\"nombre\":\"Cuenta secreta\",\"cantidad\":250.75}"))
           .andExpect(status().isCreated())
           .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(cuerpo, "$.id");

        mvc.perform(get("/api/ahorros/plan").with(user("persona-a")))
           .andExpect(jsonPath("$.saldos[*].nombre", not(hasItem("Cuenta secreta"))));
        mvc.perform(json(put("/api/ahorros/plan/saldos/" + id), "persona-a", "{\"cantidad\":0}"))
           .andExpect(status().isNotFound());
        mvc.perform(delete("/api/ahorros/plan/saldos/" + id).with(user("persona-a")).with(csrf()))
           .andExpect(status().isNotFound());

        // Una deuda (en negativo) tambien vale
        mvc.perform(json(put("/api/ahorros/plan/saldos/" + id), "persona-b", "{\"cantidad\":-80}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.cantidad").value(-80));
        mvc.perform(delete("/api/ahorros/plan/saldos/" + id).with(user("persona-b")).with(csrf()))
           .andExpect(status().isNoContent());
    }

    @Test
    void losApartadosSeGuardanEnterosYNoPasanDelCien() throws Exception {
        mvc.perform(json(put("/api/ahorros/plan/apartados"), "persona-a",
                "[{\"nombre\":\"Ahorro\",\"porcentaje\":40,\"ahorro\":true},{\"nombre\":\"Comida\",\"porcentaje\":35,\"ahorro\":false}]"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(get("/api/ahorros/plan").with(user("persona-a")))
           .andExpect(jsonPath("$.apartadosGuardados").value(true))
           .andExpect(jsonPath("$.apartados.length()").value(2))
           .andExpect(jsonPath("$.apartados[1].nombre").value("Comida"));

        // Mas del 100 %, un porcentaje raro o sin nombre: no se toca lo guardado
        mvc.perform(json(put("/api/ahorros/plan/apartados"), "persona-a",
                "[{\"nombre\":\"A\",\"porcentaje\":70,\"ahorro\":true},{\"nombre\":\"B\",\"porcentaje\":40,\"ahorro\":false}]"))
           .andExpect(status().isBadRequest());
        mvc.perform(json(put("/api/ahorros/plan/apartados"), "persona-a", "[{\"nombre\":\"A\",\"porcentaje\":10.123,\"ahorro\":true}]"))
           .andExpect(status().isBadRequest());
        mvc.perform(json(put("/api/ahorros/plan/apartados"), "persona-a", "[{\"nombre\":\" \",\"porcentaje\":10,\"ahorro\":true}]"))
           .andExpect(status().isBadRequest());
        mvc.perform(json(put("/api/ahorros/plan/apartados"), "persona-a", "[]"))
           .andExpect(status().isBadRequest());

        mvc.perform(get("/api/ahorros/plan").with(user("persona-a")))
           .andExpect(jsonPath("$.apartados.length()").value(2));
        // Los de Leo no cambian por los de Ana
        mvc.perform(get("/api/ahorros/plan").with(user("persona-b")))
           .andExpect(jsonPath("$.apartados[*].nombre", not(hasItem("Comida"))));
    }

    @Test
    void lasDosPersonasVenLaPaginaNuevaDeAhorros() throws Exception {
        mvc.perform(get("/ahorros").with(user("persona-a")))
           .andExpect(status().isOk())
           .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("ahorros"));
        mvc.perform(get("/ahorros").with(user("persona-b")))
           .andExpect(status().isOk())
           .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("ahorros"));
    }

    @Test
    void lasMetasSonPrivadasYSeValidan() throws Exception {
        String fecha = java.time.LocalDate.now().plusMonths(8).toString();
        String cuerpo = mvc.perform(json(post("/api/ahorros/plan/metas"), "persona-b",
                "{\"nombre\":\"Viaje secreto\",\"cantidad\":3000,\"fecha\":\"" + fecha + "\"}"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.cantidad").value(3000))
           .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(cuerpo, "$.id");

        mvc.perform(get("/api/ahorros/plan").with(user("persona-b")))
           .andExpect(jsonPath("$.metas[*].nombre", hasItem("Viaje secreto")));
        mvc.perform(get("/api/ahorros/plan").with(user("persona-a")))
           .andExpect(jsonPath("$.metas[*].nombre", not(hasItem("Viaje secreto"))));
        mvc.perform(json(put("/api/ahorros/plan/metas/" + id), "persona-a", "{\"cantidad\":1}"))
           .andExpect(status().isNotFound());

        // Fecha pasada, cantidad 0 o sin nombre: no vale
        mvc.perform(json(post("/api/ahorros/plan/metas"), "persona-b", "{\"nombre\":\"X\",\"cantidad\":100,\"fecha\":\"2020-01-01\"}"))
           .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/ahorros/plan/metas"), "persona-b", "{\"nombre\":\"X\",\"cantidad\":0,\"fecha\":\"" + fecha + "\"}"))
           .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/ahorros/plan/metas"), "persona-b", "{\"cantidad\":100,\"fecha\":\"" + fecha + "\"}"))
           .andExpect(status().isBadRequest());

        mvc.perform(json(put("/api/ahorros/plan/metas/" + id), "persona-b", "{\"cantidad\":3500.5}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.cantidad").value(3500.5))
           .andExpect(jsonPath("$.nombre").value("Viaje secreto"));
        mvc.perform(delete("/api/ahorros/plan/metas/" + id).with(user("persona-b")).with(csrf()))
           .andExpect(status().isNoContent());
    }

    @Test
    void losTotalesDeCadaMesSonLosApuntadosYPrivados() throws Exception {
        mvc.perform(json(post("/api/ahorros/mes/2041-02/ingresos"), "persona-a", "{\"concepto\":\"Sueldo\",\"importe\":1200}"))
           .andExpect(status().isCreated());
        mvc.perform(json(post("/api/ahorros/gastos"), "persona-a", "{\"fecha\":\"2041-02-10\",\"categoria\":\"Comida\",\"importe\":30.5}"))
           .andExpect(status().isCreated());
        mvc.perform(json(post("/api/ahorros/gastos"), "persona-a", "{\"fecha\":\"2041-02-20\",\"categoria\":\"Cenas\",\"importe\":19.5}"))
           .andExpect(status().isCreated());

        mvc.perform(get("/api/ahorros/totales").param("desde", "2041-01").param("meses", "3").with(user("persona-a")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(3))
           .andExpect(jsonPath("$[0].mes").value("2041-01"))
           .andExpect(jsonPath("$[0].ingresos").doesNotExist())
           .andExpect(jsonPath("$[1].ingresos").value(1200))
           .andExpect(jsonPath("$[1].gastos").value(50));
        // Leo no ve lo de Ana
        mvc.perform(get("/api/ahorros/totales").param("desde", "2041-02").param("meses", "1").with(user("persona-b")))
           .andExpect(jsonPath("$[0].ingresos").doesNotExist())
           .andExpect(jsonPath("$[0].gastos").value(0));
        mvc.perform(get("/api/ahorros/totales").param("desde", "2041-02").param("meses", "100").with(user("persona-a")))
           .andExpect(status().isBadRequest());
    }
}
