package com.pareja.duo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.pareja.duo.repartir.CalculoDeudas;
import com.pareja.duo.repartir.CalculoDeudas.Gasto;
import com.pareja.duo.repartir.CalculoDeudas.Transferencia;

@SpringBootTest
@AutoConfigureMockMvc
class RepartirTests {

    @Autowired
    MockMvc mvc;

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String cuerpo) {
        return b.with(user("persona-a")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    /** El ejemplo de la bolera: Nora no bebió en la última ronda. */
    @Test
    void calculaQuienDebeAQuien() {
        List<String> gente = List.of("Ana", "Leo", "Iris", "Nora");
        var r = CalculoDeudas.calcular(gente, List.of(
                new Gasto(new BigDecimal("48"), "Nora", gente),
                new Gasto(new BigDecimal("7"), "Leo", gente),
                new Gasto(new BigDecimal("8"), "Iris", gente),
                new Gasto(new BigDecimal("8"), "Leo", List.of("Ana", "Leo", "Iris"))), List.of());

        // Los saldos suman cero: el dinero cuadra al céntimo
        assertThat(r.saldos().stream().map(CalculoDeudas.Saldo::saldo).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("0");
        assertThat(r.transferencias()).containsExactly(
                new Transferencia("Ana", "Nora", new BigDecimal("18.42")),
                new Transferencia("Iris", "Nora", new BigDecimal("10.41")),
                new Transferencia("Leo", "Nora", new BigDecimal("3.42")));
    }

    @Test
    void quedadaCompletaPorLaApi() throws Exception {
        String creada = mvc.perform(json(post("/api/repartir/quedadas"),
                        "{\"nombre\":\"Bolera\",\"fecha\":\"2031-05-10\",\"personas\":[\"Ana\",\"Leo\",\"Nora\",\"nora\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.personas.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(creada, "$.id");
        String base = "/api/repartir/quedadas/" + id;

        mvc.perform(json(post(base + "/gastos"),
                        "{\"concepto\":\"Bolos\",\"importe\":30,\"pagador\":\"nora\",\"participantes\":[\"Ana\",\"Leo\",\"Nora\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gastos[0].pagador").value("Nora"))
                .andExpect(jsonPath("$.transferencias.length()").value(2));

        // Importe con millones de decimales: se rechaza sin intentar redondearlo
        mvc.perform(json(post(base + "/gastos"), "{\"concepto\":\"X\",\"importe\":1e-600000000,\"pagador\":\"Ana\",\"participantes\":[\"Ana\"]}"))
                .andExpect(status().isBadRequest());

        // Pagador que no está en la quedada
        mvc.perform(json(post(base + "/gastos"), "{\"concepto\":\"X\",\"importe\":5,\"pagador\":\"Pepe\",\"participantes\":[\"Ana\"]}"))
                .andExpect(status().isBadRequest());

        // Ana paga su parte: solo queda Leo
        mvc.perform(json(post(base + "/pagos"), "{\"de\":\"Ana\",\"para\":\"Nora\",\"importe\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferencias.length()").value(1))
                .andExpect(jsonPath("$.transferencias[0].de").value("Leo"));

        // No se puede quitar a alguien que aparece en un gasto
        mvc.perform(delete(base + "/personas").param("nombre", "Nora").with(user("persona-b")).with(csrf()))
                .andExpect(status().isBadRequest());

        // Leo también la ve
        mvc.perform(get("/api/repartir/quedadas").with(user("persona-b")))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].pendiente").value(10.0));
    }

    @Autowired
    com.pareja.duo.ahorros.MovimientoBancoRepository movimientosBanco;

    private Long movimiento(String concepto, String importe, boolean entrada) {
        var m = new com.pareja.duo.ahorros.MovimientoBanco();
        m.setUsuario("persona-a"); m.setHuella("prueba-" + concepto + "-" + System.nanoTime()); m.setIdTransaccion("tx-" + System.nanoTime());
        m.setCuentaUid("cuenta-prueba"); m.setFecha(java.time.LocalDate.now()); m.setImporte(new BigDecimal(importe));
        m.setConcepto(concepto); m.setEntrada(entrada); m.setEstado("IMPORTADO");
        return movimientosBanco.save(m).getId();
    }

    /** Ana enlaza su Bizum con la salida de su banco; Leo ve que está comprobado pero no el concepto. */
    @Test
    void unBizumSeCompruebaConUnMovimientoDelBanco() throws Exception {
        String creada = mvc.perform(json(post("/api/repartir/quedadas"),
                "{\"nombre\":\"Bolera banco\",\"personas\":[\"Ana\",\"Nora\"]}"))
           .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(creada, "$.id");
        String conPago = mvc.perform(json(post("/api/repartir/quedadas/" + id + "/pagos"),
                "{\"de\":\"Ana\",\"para\":\"Nora\",\"importe\":18.42}"))
           .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Integer pagoId = JsonPath.read(conPago, "$.pagos[0].id");
        String base = "/api/repartir/quedadas/" + id + "/pagos/" + pagoId + "/banco";

        Long salida = movimiento("BIZUM A NORA", "18.42", false);
        Long otraSalida = movimiento("MERCADONA", "30.10", false);
        Long entrada = movimiento("BIZUM DE NORA", "18.42", true);

        String candidatos = mvc.perform(get(base).with(user("persona-a")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].id").value(salida))
           .andExpect(jsonPath("$[0].coincide").value(true))
           .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(candidatos, "$[*].id");
        assertThat(ids).contains(otraSalida.intValue()).doesNotContain(entrada.intValue());

        // Una entrada no demuestra un Bizum que Ana hizo; Leo no tiene el banco conectado
        mvc.perform(json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(base), "{\"movimientoId\":" + entrada + "}"))
           .andExpect(status().isBadRequest());
        mvc.perform(get(base).with(user("persona-b"))).andExpect(status().isForbidden());

        mvc.perform(json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(base), "{\"movimientoId\":" + salida + "}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.pagos[0].banco.mio").value(true))
           .andExpect(jsonPath("$.pagos[0].banco.concepto").value("BIZUM A NORA"));
        mvc.perform(get("/api/repartir/quedadas/" + id).with(user("persona-b")))
           .andExpect(jsonPath("$.pagos[0].banco.quien").value("Ana"))
           .andExpect(jsonPath("$.pagos[0].banco.mio").value(false))
           .andExpect(jsonPath("$.pagos[0].banco.concepto").doesNotExist());
        // Para este mismo Bizum se sigue ofreciendo (por si quiere cambiarlo)
        mvc.perform(get(base).with(user("persona-a"))).andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.hasItem(salida.intValue())));

        mvc.perform(delete(base).with(user("persona-b")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete(base).with(user("persona-a")).with(csrf()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.pagos[0].banco").doesNotExist());
    }
}
