package com.pareja.duo.ahorros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Base64;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "duo.banco.usuarios=persona-a,prueba-banco-vuelta,prueba-banco-sin-config")
@AutoConfigureMockMvc
class BancoAhorrosTests {
    @Autowired ServicioBanco servicio;
    @Autowired GastoRepository gastos;
    @Autowired IngresoRepository ingresos;
    @Autowired SaldoCuentaRepository saldos;
    @Autowired MovimientoBancoRepository movimientos;
    @Autowired ReglaCategoriaRepository reglas;
    @Autowired JsonMapper json;
    @Autowired MockMvc mvc;
    @MockitoBean BancoCliente banco;

    @BeforeEach
    void prepararBancoFalso() throws Exception {
        when(banco.disponible()).thenReturn(true);
        when(banco.bancosEspaña()).thenReturn(json.readTree("""
                {"aspsps":[{"name":"Banco Ejemplo","country":"ES","maximum_consent_validity":15552000}]}
                """));
        when(banco.iniciarAutorizacion(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(json.readTree("{\"url\":\"https://auth.example.test/consent\"}"));
        when(banco.crearSesion("codigo-falso")).thenReturn(json.readTree("""
                {"session_id":"sesion-falsa","aspsp":{"name":"Banco Ejemplo"},
                 "access":{"valid_until":"2030-05-20T12:00:00Z"},
                 "accounts":[{"uid":"cuenta-falsa-123","name":"Cuenta diaria"}]}
                """));
        when(banco.movimientos(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(transaccionesPorDefecto());
        when(banco.saldos(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(json.readTree("""
                {"balances":[{"balance_type":"CLAV","balance_amount":{"currency":"EUR","amount":"812.45"}}]}
                """));
    }

    @Test
    void autorizaConLaUrlDevueltaPorElBancoFalso() {
        assertThat(servicio.iniciar("prueba-banco-inicio", "state-falso")).isEqualTo("https://auth.example.test/consent");
    }

    @Test
    void importaUnaVezCargosIngresosYSaldo() throws Exception {
        String usuario = "prueba-banco-unico";
        servicio.completar(usuario, "codigo-falso");
        servicio.sincronizar(usuario);

        var desde = LocalDate.now().minusDays(5);
        assertThat(gastos.findByUsuarioAndFechaBetweenOrderByFechaDescIdDesc(usuario, desde, LocalDate.now()))
                .singleElement().satisfies(g -> {
                    assertThat(g.getImporte()).isEqualByComparingTo("12.40");
                    assertThat(g.getCategoria()).isEqualTo("Sin categoría");
                });
        assertThat(ingresos.findByUsuarioAndMesOrderByIdAsc(usuario, YearMonth.now().toString()))
                .singleElement().satisfies(i -> {
                    assertThat(i.getConcepto()).contains("Nómina");
                    assertThat(i.getImporte()).isEqualByComparingTo("950.00");
                });
        assertThat(movimientos.findTop100ByUsuarioAndEstadoOrderByFechaDescIdDesc(usuario, "PENDIENTE")).hasSize(1);
        assertThat(saldos.findByUsuarioAndBancoCuentaUid(usuario, "cuenta-falsa-123").orElseThrow().getCantidad())
                .isEqualByComparingTo("812.45");
        mvc.perform(get("/api/ahorros/mes/" + YearMonth.now()).with(user(usuario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.gastos[0].origenBanco").value(true));
    }

    @Test
    void conElUsuarioDelanteSeMandaSuIpYElLimiteDiarioSeExplicaSinDesconectar() throws Exception {
        String usuario = "prueba-banco-limite";
        servicio.completar(usuario, "codigo-falso");
        var psu = new BancoCliente.Psu("203.0.113.7", "Navegador de prueba");
        servicio.sincronizar(usuario, psu);
        org.mockito.Mockito.verify(banco, org.mockito.Mockito.atLeastOnce()).saldos("cuenta-falsa-123", psu);

        when(banco.saldos(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull()))
                .thenThrow(new EnableBankingCliente.BancoApiException("Enable Banking respondió con error HTTP 429", 429));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> servicio.sincronizar(usuario))
                .isInstanceOf(EnableBankingCliente.BancoApiException.class);
        assertThat(servicio.estado(usuario).ultimoError()).isEqualTo(ServicioBanco.LIMITE_DIARIO);
        assertThat(servicio.estado(usuario).conectado()).isTrue();
    }

    @Test
    void sincronizarNoRevierteEdicionesNiRecreaUnGastoBorrado() throws Exception {
        String usuario = "prueba-banco-ediciones";
        servicio.completar(usuario, "codigo-falso");
        Gasto gasto = gastos.findByUsuarioAndFechaBetweenOrderByFechaDescIdDesc(usuario,
                LocalDate.now().minusDays(5), LocalDate.now()).getFirst();
        gasto.setCategoria("Editado a mano"); gastos.save(gasto);
        servicio.sincronizar(usuario);
        assertThat(gastos.findByIdAndUsuario(gasto.getId(), usuario).orElseThrow().getCategoria()).isEqualTo("Editado a mano");
        gastos.deleteById(gasto.getId());
        servicio.sincronizar(usuario);
        assertThat(gastos.findByUsuarioAndFechaBetweenOrderByFechaDescIdDesc(usuario,
                LocalDate.now().minusDays(5), LocalDate.now())).isEmpty();
    }

    @Test
    void aplicaReglasDeCategoriaEIgnoraTraspasos() throws Exception {
        String usuario = "prueba-banco-reglas";
        ReglaCategoria categoria = new ReglaCategoria(); categoria.setUsuario(usuario); categoria.setContiene("MERCADO");
        categoria.setAccion("CATEGORIA"); categoria.setCategoria("Compra"); reglas.save(categoria);
        ReglaCategoria ignorar = new ReglaCategoria(); ignorar.setUsuario(usuario); ignorar.setContiene("TRASPASO PROPIO");
        ignorar.setAccion("IGNORAR"); reglas.save(ignorar);
        String fecha = LocalDate.now().minusDays(2).toString();
        when(banco.movimientos(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(json.readTree("""
                    {"transactions":[
                      {"entry_reference":"tx-mercado","transaction_amount":{"currency":"EUR","amount":"12"},"credit_debit_indicator":"DBIT","booking_date":"%s","remittance_information":["Mercado local"]},
                      {"entry_reference":"tx-traspaso","transaction_amount":{"currency":"EUR","amount":"20"},"credit_debit_indicator":"DBIT","booking_date":"%s","remittance_information":["Traspaso propio"]}
                    ],"continuation_key":null}
                    """.formatted(fecha, fecha)));
        servicio.completar(usuario, "codigo-falso");

        var gastosUsuario = gastos.findByUsuarioAndFechaBetweenOrderByFechaDescIdDesc(usuario,
                LocalDate.now().minusDays(5), LocalDate.now());
        assertThat(gastosUsuario).singleElement().extracting(Gasto::getCategoria).isEqualTo("Compra");
        assertThat(movimientos.findTop100ByUsuarioAndEstadoOrderByFechaDescIdDesc(usuario, "IGNORADO")).hasSize(1);
    }

    @Test
    void laConexionYLosMovimientosSonPrivadosPorUsuario() throws Exception {
        servicio.completar("persona-a", "codigo-falso");
        mvc.perform(get("/api/ahorros/banco/estado").with(user("persona-b")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conectado").value(false));
        assertThat(gastos.findByUsuarioAndFechaBetweenOrderByFechaDescIdDesc("persona-b", LocalDate.now().minusDays(5), LocalDate.now())).isEmpty();
    }

    @Test
    void soloLasCuentasPermitidasPuedenConectarElBanco() throws Exception {
        mvc.perform(get("/api/ahorros/banco/estado").with(user("persona-b")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permitido").value(false));
        mvc.perform(get("/api/ahorros/banco/estado").with(user("persona-a")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permitido").value(true));
        mvc.perform(post("/api/ahorros/banco/conectar").with(user("persona-b")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/ahorros/banco/sincronizar").with(user("persona-b")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ahorros/banco/reglas").with(user("persona-b")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rechazaStateIncorrectoEnLaVuelta() throws Exception {
        mvc.perform(get("/ahorros/banco/vuelta").param("code", "codigo-falso").param("state", "incorrecto").with(user("persona-a")))
                .andExpect(status().isForbidden());
    }

    @Test
    void completaLaVueltaConElStateGuardadoEnLaSesion() throws Exception {
        MvcResult inicio = mvc.perform(post("/api/ahorros/banco/conectar").with(user("prueba-banco-vuelta")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("https://auth.example.test/consent"))
                .andReturn();
        MockHttpSession sesion = (MockHttpSession) inicio.getRequest().getSession(false);
        String state = (String) sesion.getAttribute("duo.banco.state");
        mvc.perform(get("/ahorros/banco/vuelta").session(sesion).param("code", "codigo-falso").param("state", state)
                        .with(user("prueba-banco-vuelta")))
                .andExpect(status().is3xxRedirection()).andExpect(header().string("Location", "/ahorros?banco=conectado"));
        assertThat(servicio.estado("prueba-banco-vuelta").conectado()).isTrue();
    }

    @Test
    void estadoExplicaCuandoFaltaConfiguracion() throws Exception {
        when(banco.disponible()).thenReturn(false);
        mvc.perform(get("/api/ahorros/banco/estado").with(user("prueba-banco-sin-config")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.disponible").value(false));
    }

    @Test
    void jwtIncluyeKidYSeVerificaConLaClavePublica() throws Exception {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA"); generador.initialize(2048);
        KeyPair par = generador.generateKeyPair();
        Path pem = Files.createTempFile("duo-enablebanking-test-", ".pem");
        try {
            String contenido = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(par.getPrivate().getEncoded());
            Files.writeString(pem, "-----BEGIN PRIVATE KEY-----\n" + contenido + "\n-----END PRIVATE KEY-----\n");
            EnableBankingCliente cliente = new EnableBankingCliente("app-id-prueba", pem.toString(), json);
            String jwt = cliente.crearJwt();
            String[] partes = jwt.split("\\.");
            assertThat(json.readTree(Base64.getUrlDecoder().decode(partes[0])).path("kid").asText()).isEqualTo("app-id-prueba");
            assertThat(json.readTree(Base64.getUrlDecoder().decode(partes[1])).path("aud").asText()).isEqualTo("api.enablebanking.com");
            Signature verificador = Signature.getInstance("SHA256withRSA"); verificador.initVerify(par.getPublic());
            verificador.update((partes[0] + "." + partes[1]).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            assertThat(verificador.verify(Base64.getUrlDecoder().decode(partes[2]))).isTrue();
        } finally { Files.deleteIfExists(pem); }
    }

    private JsonNode transaccionesPorDefecto() throws Exception {
        String fecha = LocalDate.now().minusDays(2).toString();
        return json.readTree("""
                {"transactions":[
                  {"entry_reference":"tx-gasto-1","transaction_amount":{"currency":"EUR","amount":"12.40"},
                   "credit_debit_indicator":"DBIT","booking_date":"%s","remittance_information":["Compra diaria"]},
                  {"entry_reference":"tx-ingreso-1","transaction_amount":{"currency":"EUR","amount":"950.00"},
                   "credit_debit_indicator":"CRDT","booking_date":"%s","remittance_information":["Nómina"]}
                ],"continuation_key":null}
                """.formatted(fecha, fecha));
    }
}
