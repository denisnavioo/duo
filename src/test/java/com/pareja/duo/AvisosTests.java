package com.pareja.duo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.pareja.duo.actividad.Actividad;
import com.pareja.duo.actividad.ActividadRepository;
import com.pareja.duo.actividad.TipoPlanRepository;
import com.pareja.duo.aviso.CifradoPush;
import com.pareja.duo.aviso.CifradoPushPruebas;
import com.pareja.duo.aviso.EnviadorPush;
import com.pareja.duo.aviso.RecordatorioPlanes;
import com.pareja.duo.aviso.SuscripcionPushRepository;

@SpringBootTest
@AutoConfigureMockMvc
class AvisosTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    RecordatorioPlanes recordatorio;

    @Autowired
    ActividadRepository actividades;

    @Autowired
    TipoPlanRepository tipos;

    @Autowired
    SuscripcionPushRepository suscripciones;

    /** Lo que haria el movil al recibirlo: descifrarlo con su clave privada. */
    @Test
    void elMovilPuedeDescifrarElAviso() throws Exception {
        KeyPair movil = CifradoPush.nuevoPar();
        byte[] p256dh = CifradoPush.crudo((ECPublicKey) movil.getPublic());
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        byte[] mensaje = "{\"titulo\":\"Mañana: Cena\"}".getBytes(StandardCharsets.UTF_8);

        byte[] cuerpo = CifradoPush.cifrar(mensaje, p256dh, auth);

        ByteBuffer b = ByteBuffer.wrap(cuerpo);
        byte[] salt = new byte[16];
        b.get(salt);
        assertEquals(4096, b.getInt());
        assertEquals(65, b.get());
        byte[] delServidor = new byte[65];
        b.get(delServidor);
        byte[] cifrado = new byte[b.remaining()];
        b.get(cifrado);

        byte[] secreto = CifradoPushPruebas.ecdh(movil, delServidor);
        byte[][] claveYNonce = CifradoPushPruebas.derivar(secreto, auth, p256dh, delServidor, salt);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(claveYNonce[0], "AES"), new GCMParameterSpec(128, claveYNonce[1]));
        byte[] claro = c.doFinal(cifrado);
        assertEquals(2, claro[claro.length - 1], "delimitador de ultimo registro");
        assertArrayEquals(mensaje, Arrays.copyOf(claro, claro.length - 1));
    }

    /** Valores del ejemplo oficial (RFC 8291, apendice A): asi sabemos que los moviles lo entenderan. */
    @Test
    void cifradoIgualQueElEjemploDelEstandar() throws Exception {
        byte[] asPublic = CifradoPush.deB64("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
        byte[] uaPublic = CifradoPush.deB64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] salt = CifradoPush.deB64("DGv6ra1nlYgDCS1FRnbzlw");
        byte[] auth = CifradoPush.deB64("BTBZMqHH6r4Tts7J_aSIgg");
        byte[] ecdh = CifradoPush.deB64("kyrL1jIIOHEzg3sM2ZWRHDRB62YACZhhSlknJ672kSs");

        byte[][] claveYNonce = CifradoPushPruebas.derivar(ecdh, auth, uaPublic, asPublic, salt);
        assertEquals("oIhVW04MRdy2XN9CiKLxTg", CifradoPush.b64(claveYNonce[0]));
        assertEquals("4h_95klXJ5E_qnoN", CifradoPush.b64(claveYNonce[1]));

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(claveYNonce[0], "AES"), new GCMParameterSpec(128, claveYNonce[1]));
        byte[] claro = CifradoPush.deB64("V2hlbiBJIGdyb3cgdXAsIEkgd2FudCB0byBiZSBhIHdhdGVybWVsb24C");
        assertEquals("8pfeW0KbunFT06SuDKoJH9Ql87S1QUrdirN6GcG7sFz1y1sqLgVi1VhjVkHsUoEsbI_0LpXMuGvnzQ",
                CifradoPush.b64(c.doFinal(claro)));
    }

    @Test
    void laFirmaVapidSeVerificaConLaClavePublica() throws Exception {
        KeyPair par = CifradoPush.nuevoPar();
        String jwt = CifradoPush.jwtVapid("https://fcm.googleapis.com", "https://example.com", 2_000_000_000L, par.getPrivate());
        String[] partes = jwt.split("\\.");
        assertEquals(3, partes.length);
        assertTrue(new String(CifradoPush.deB64(partes[1]), StandardCharsets.UTF_8).contains("\"aud\":\"https://fcm.googleapis.com\""));
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initVerify(CifradoPush.publicaDeCrudo(CifradoPush.crudo((ECPublicKey) par.getPublic())));
        s.update((partes[0] + "." + partes[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(s.verify(CifradoPush.deB64(partes[2])));
    }

    private Actividad plan(String titulo, LocalDate dia, String hora, String quien, String estado) {
        Actividad a = new Actividad();
        a.setTitulo(titulo);
        a.setFecha(dia);
        a.setHora(LocalTime.parse(hora));
        a.setQuien(quien);
        a.setTipo(tipos.findAllByOrderByOrdenAscIdAsc().get(0));
        a.setCreadoPor("persona-a");
        a.setEstado(estado);
        return actividades.save(a);
    }

    @Test
    void cadaUnoRecibeSusPlanesYLosDeLosDos() {
        LocalDate dia = LocalDate.of(2031, 3, 10);
        plan("Médico", dia, "10:00", "persona-a", Actividad.ACEPTADA);
        plan("Cena", dia, "21:00", "juntos", Actividad.PENDIENTE);
        plan("Dentista", dia, "12:00", "persona-b", Actividad.ACEPTADA);
        plan("Rechazado", dia, "18:00", "juntos", Actividad.RECHAZADA);
        plan("Otro día", dia.plusDays(1), "09:00", "persona-a", Actividad.ACEPTADA);

        Map<String, EnviadorPush.Aviso> avisos = recordatorio.avisosPara(dia);

        EnviadorPush.Aviso avisoA = avisos.get("persona-a");
        assertEquals("Mañana tienes 2 planes", avisoA.titulo());
        assertEquals("10:00 · Médico\n21:00 · Cena · con Leo (sin aceptar)", avisoA.texto());
        assertEquals("/calendario", avisoA.url());

        EnviadorPush.Aviso avisoB = avisos.get("persona-b");
        assertTrue(avisoB.texto().contains("12:00 · Dentista"));
        assertTrue(avisoB.texto().contains("con Ana"));
        assertFalse(avisoB.texto().contains("Médico"));
        assertFalse(avisoB.texto().contains("Rechazado"));
    }

    @Test
    void unSoloPlanAbreEsePlan() {
        LocalDate dia = LocalDate.of(2031, 4, 2);
        Actividad a = plan("Examen", dia, "09:30", "persona-b", Actividad.ACEPTADA);
        Map<String, EnviadorPush.Aviso> avisos = recordatorio.avisosPara(dia);
        assertEquals("Mañana: Examen", avisos.get("persona-b").titulo());
        assertEquals("/calendario?plan=" + a.getId(), avisos.get("persona-b").url());
        assertFalse(avisos.containsKey("persona-a"), "sin planes no hay aviso");
    }

    @Test
    void suscribirseYQuitarLaSuscripcion() throws Exception {
        KeyPair movil = CifradoPush.nuevoPar();
        String p256dh = CifradoPush.b64(CifradoPush.crudo((ECPublicKey) movil.getPublic()));
        String auth = CifradoPush.b64(new byte[16]);
        String endpoint = "https://fcm.googleapis.com/fcm/send/prueba-123";
        String json = """
            {"endpoint": "%s", "keys": {"p256dh": "%s", "auth": "%s"}}
            """.formatted(endpoint, p256dh, auth);

        mvc.perform(get("/api/avisos/clave").with(user("persona-a")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.clave").isString());
        mvc.perform(post("/api/avisos/suscripcion").with(user("persona-a")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
           .andExpect(status().isNoContent());
        assertEquals("persona-a", suscripciones.findByEndpoint(endpoint).orElseThrow().getUsuario());

        // Leo no puede quitar la de Ana
        String fin = "{\"endpoint\": \"%s\"}".formatted(endpoint);
        mvc.perform(delete("/api/avisos/suscripcion").with(user("persona-b")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(fin))
           .andExpect(status().isNoContent());
        assertTrue(suscripciones.findByEndpoint(endpoint).isPresent());

        mvc.perform(delete("/api/avisos/suscripcion").with(user("persona-a")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(fin))
           .andExpect(status().isNoContent());
        assertTrue(suscripciones.findByEndpoint(endpoint).isEmpty());
    }

    @Test
    void soloSeAceptanServiciosDeAvisosConocidos() throws Exception {
        KeyPair movil = CifradoPush.nuevoPar();
        String json = """
            {"endpoint": "http://192.168.1.10:8080/robar", "keys": {"p256dh": "%s", "auth": "%s"}}
            """.formatted(CifradoPush.b64(CifradoPush.crudo((ECPublicKey) movil.getPublic())), CifradoPush.b64(new byte[16]));
        mvc.perform(post("/api/avisos/suscripcion").with(user("persona-a")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
           .andExpect(status().isBadRequest());
    }

    @Test
    void elServiceWorkerYElManifiestoSonPublicos() throws Exception {
        mvc.perform(get("/sw.js")).andExpect(status().isOk());
        mvc.perform(get("/manifest.webmanifest")).andExpect(status().isOk());
        mvc.perform(get("/api/avisos/clave")).andExpect(status().is3xxRedirection());
    }
}
