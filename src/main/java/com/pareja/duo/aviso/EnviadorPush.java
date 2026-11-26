package com.pareja.duo.aviso;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Manda avisos a los moviles de una persona. */
@Service
public class EnviadorPush {

    private static final Logger log = LoggerFactory.getLogger(EnviadorPush.class);

    /** Lo que llega al movil: el service worker lo convierte en notificacion. */
    public record Aviso(String titulo, String texto, String url) {

        String json() {
            return "{\"titulo\":" + texto(titulo) + ",\"texto\":" + texto(texto) + ",\"url\":" + texto(url) + "}";
        }

        private static String texto(String s) {
            StringBuilder b = new StringBuilder("\"");
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"' -> b.append("\\\"");
                    case '\\' -> b.append("\\\\");
                    case '\n' -> b.append("\\n");
                    default -> {
                        if (c < 0x20) {
                            b.append(String.format("\\u%04x", (int) c));
                        } else {
                            b.append(c);
                        }
                    }
                }
            }
            return b.append('"').toString();
        }
    }

    private final SuscripcionPushRepository suscripciones;
    private final ClaveVapidRepository claves;
    private final String contacto;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private String publica;
    private PrivateKey privada;

    public EnviadorPush(SuscripcionPushRepository suscripciones, ClaveVapidRepository claves,
            @Value("${duo.avisos.contacto:https://example.com}") String contacto) {
        this.suscripciones = suscripciones;
        this.claves = claves;
        this.contacto = contacto;
    }

    /** Clave publica que el navegador necesita para suscribirse. */
    public synchronized String clavePublica() {
        if (publica == null) {
            cargarClaves();
        }
        return publica;
    }

    private void cargarClaves() {
        try {
            ClaveVapid c = claves.findById(1L).orElse(null);
            if (c == null) {
                KeyPair par = CifradoPush.nuevoPar();
                c = new ClaveVapid();
                c.setPublica(CifradoPush.b64(CifradoPush.crudo((ECPublicKey) par.getPublic())));
                c.setPrivada(CifradoPush.b64(par.getPrivate().getEncoded()));
                c = claves.save(c);
                log.info("Creadas las llaves para los avisos del movil");
            }
            privada = CifradoPush.privadaDePkcs8(CifradoPush.deB64(c.getPrivada()));
            publica = c.getPublica();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudieron preparar las llaves de los avisos", e);
        }
    }

    /** Manda el aviso a todos los moviles de la persona. Devuelve a cuantos ha llegado. */
    public int enviar(String usuario, Aviso aviso) {
        clavePublica();
        int entregados = 0;
        for (SuscripcionPush s : suscripciones.findByUsuario(usuario)) {
            if (enviarA(s, aviso)) {
                entregados++;
            }
        }
        return entregados;
    }

    private boolean enviarA(SuscripcionPush s, Aviso aviso) {
        try {
            URI destino = URI.create(s.getEndpoint());
            String origen = destino.getScheme() + "://" + destino.getHost() + (destino.getPort() > 0 ? ":" + destino.getPort() : "");
            long caduca = Instant.now().plus(Duration.ofHours(12)).getEpochSecond();
            String jwt = CifradoPush.jwtVapid(origen, contacto, caduca, privada);
            byte[] cuerpo = CifradoPush.cifrar(aviso.json().getBytes(StandardCharsets.UTF_8),
                    CifradoPush.deB64(s.getP256dh()), CifradoPush.deB64(s.getAuth()));
            HttpRequest req = HttpRequest.newBuilder(destino)
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "vapid t=" + jwt + ", k=" + publica)
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", "43200") // si el movil esta apagado, el aviso espera hasta 12 horas
                    .header("Urgency", "normal")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo))
                    .build();
            int codigo = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (codigo == 404 || codigo == 410) {
                // El movil ya no acepta avisos (se desactivaron o se borro la app): se olvida
                suscripciones.delete(s);
                log.info("Aviso: suscripcion caducada de {} borrada", s.getUsuario());
                return false;
            }
            if (codigo >= 300) {
                log.warn("Aviso para {} rechazado por {} (HTTP {})", s.getUsuario(), destino.getHost(), codigo);
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("No se pudo mandar un aviso a {}: {}", s.getUsuario(), e.getMessage());
            return false;
        }
    }
}
