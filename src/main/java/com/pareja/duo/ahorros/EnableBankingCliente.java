package com.pareja.duo.ahorros;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Cliente HTTP de solo lectura de Enable Banking (AIS). */
@Component
public class EnableBankingCliente implements BancoCliente {
    private static final String API = "https://api.enablebanking.com";
    private final String appId;
    private final String clavePrivada;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public EnableBankingCliente(@Value("${duo.banco.app-id:}") String appId,
                                @Value("${duo.banco.clave-privada:}") String clavePrivada,
                                JsonMapper json) {
        this.appId = appId == null ? "" : appId.trim();
        this.clavePrivada = clavePrivada == null ? "" : clavePrivada.trim();
        this.json = json;
    }

    @Override public boolean disponible() { return !appId.isBlank() && !clavePrivada.isBlank() && Files.isRegularFile(Path.of(clavePrivada)); }

    @Override public JsonNode bancosEspaña() { return llamada("GET", "/aspsps?country=ES&service=AIS", null); }

    @Override public JsonNode iniciarAutorizacion(String nombreBanco, String state, String redirectUrl, OffsetDateTime validoHasta) {
        var cuerpo = json.createObjectNode();
        cuerpo.putObject("access").put("valid_until", validoHasta.toString());
        cuerpo.putObject("aspsp").put("name", nombreBanco).put("country", "ES");
        cuerpo.put("state", state);
        cuerpo.put("redirect_url", redirectUrl);
        cuerpo.put("psu_type", "personal");
        cuerpo.put("language", "es");
        return llamada("POST", "/auth", cuerpo);
    }

    @Override public JsonNode crearSesion(String code) {
        var cuerpo = json.createObjectNode().put("code", code);
        return llamada("POST", "/sessions", cuerpo);
    }

    @Override public JsonNode movimientos(String cuentaUid, LocalDate desde, String continuationKey, Psu psu) {
        String path = "/accounts/" + segmento(cuentaUid) + "/transactions?date_from=" + desde + "&transaction_status=BOOK";
        if (continuationKey != null && !continuationKey.isBlank()) path += "&continuation_key=" + URLEncoder.encode(continuationKey, StandardCharsets.UTF_8);
        return llamada("GET", path, null, psu);
    }

    @Override public JsonNode saldos(String cuentaUid, Psu psu) { return llamada("GET", "/accounts/" + segmento(cuentaUid) + "/balances", null, psu); }

    @Override public void cerrarSesion(String sessionId) { llamada("DELETE", "/sessions/" + segmento(sessionId), null); }

    private JsonNode llamada(String metodo, String path, JsonNode cuerpo) { return llamada(metodo, path, cuerpo, null); }

    private JsonNode llamada(String metodo, String path, JsonNode cuerpo, Psu psu) {
        if (!disponible()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "La conexión bancaria no está configurada");
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(API + path)).timeout(Duration.ofSeconds(35))
                    .header("Authorization", "Bearer " + crearJwt()).header("Accept", "application/json");
            // Con el usuario delante, el banco no la cuenta entre las 4 consultas automaticas del dia
            if (psu != null && psu.ip() != null) {
                req.header("Psu-Ip-Address", psu.ip());
                if (psu.agente() != null && !psu.agente().isBlank()) req.header("Psu-User-Agent", psu.agente());
            }
            if (cuerpo != null) req.header("Content-Type", "application/json").method(metodo, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
            else req.method(metodo, HttpRequest.BodyPublishers.noBody());
            HttpResponse<String> respuesta = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (respuesta.statusCode() < 200 || respuesta.statusCode() >= 300) {
                // No propagar el cuerpo externo entero (puede incluir identificadores de sesión o cuenta):
                // solo su código de error y un mensaje corto, sin números largos ni identificadores
                throw new BancoApiException("Enable Banking respondió con error HTTP " + respuesta.statusCode() + resumenError(respuesta.body()),
                        respuesta.statusCode());
            }
            if (respuesta.body().isBlank()) return json.createObjectNode();
            return json.readTree(respuesta.body());
        } catch (BancoApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BancoApiException("Se interrumpió la comunicación con el proveedor bancario", e);
        } catch (Exception e) {
            throw new BancoApiException("No se pudo comunicar con el proveedor bancario", e);
        }
    }

    /** " (CODIGO: mensaje corto)" del error de Enable Banking, sin identificadores; vacio si no se entiende. */
    private String resumenError(String cuerpo) {
        try {
            JsonNode e = json.readTree(cuerpo);
            String codigo = e.path("error").asText(e.path("code").asText(""));
            String texto = e.path("message").asText("");
            String r = (codigo + (texto.isBlank() ? "" : ": " + texto))
                    .replaceAll("[0-9a-fA-F-]{16,}", "…")      // identificadores (uuid, hashes)
                    .replaceAll("[\\r\\n\\t]", " ");
            if (r.isBlank()) return "";
            return " (" + (r.length() > 160 ? r.substring(0, 160) + "…" : r) + ")";
        } catch (Exception ignored) {
            return "";
        }
    }

    String crearJwt() throws Exception {
        // 30 s de holgura hacia atras: si el reloj del servidor va un poco adelantado, Enable Banking rechaza
        // el JWT con "JWT can not be issued in the future" (pasa si el reloj del servidor no esta bien sincronizado)
        long ahora = Instant.now().getEpochSecond() - 30;
        String cabecera = codificar(Map.of("typ", "JWT", "alg", "RS256", "kid", appId));
        String carga = codificar(Map.of("iss", "enablebanking.com", "aud", "api.enablebanking.com", "iat", ahora, "exp", ahora + 300));
        String contenido = cabecera + "." + carga;
        Signature firma = Signature.getInstance("SHA256withRSA");
        firma.initSign(leerLlave());
        firma.update(contenido.getBytes(StandardCharsets.US_ASCII));
        return contenido + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(firma.sign());
    }

    private String codificar(Object objeto) throws IOException { return Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(objeto)); }

    private java.security.PrivateKey leerLlave() throws Exception {
        String pem = Files.readString(Path.of(clavePrivada), StandardCharsets.UTF_8)
                .replaceAll("-----BEGIN [^-]+-----", "").replaceAll("-----END [^-]+-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(pem);
        // La descarga del panel suele ser PKCS#8; admitir también RSA PRIVATE KEY (PKCS#1).
        if (Files.readString(Path.of(clavePrivada), StandardCharsets.UTF_8).contains("BEGIN RSA PRIVATE KEY")) der = pkcs1A_pkcs8(der);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static byte[] pkcs1A_pkcs8(byte[] pkcs1) {
        // Codifica PKCS#8 con longitud DER apropiada para el PKCS#1 de entrada.
        byte[] algoritmo = new byte[] {0x30, 0x0d, 0x06, 0x09, 0x2a, (byte)0x86, 0x48, (byte)0x86, (byte)0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        byte[] octeto = derTlv(0x04, pkcs1);
        byte[] version = new byte[] {0x02, 0x01, 0x00};
        byte[] seqInterna = concat(version, algoritmo, octeto);
        return derTlv(0x30, seqInterna);
    }

    private static byte[] derTlv(int tag, byte[] contenido) {
        byte[] longitud;
        if (contenido.length < 128) longitud = new byte[] {(byte) contenido.length};
        else {
            int n = 0, v = contenido.length; while (v > 0) { n++; v >>>= 8; }
            longitud = new byte[n + 1]; longitud[0] = (byte)(0x80 | n);
            for (int i = n; i > 0; i--) longitud[i] = (byte)(contenido.length >>> ((n - i) * 8));
        }
        return concat(new byte[] {(byte) tag}, longitud, contenido);
    }
    private static byte[] concat(byte[]... trozos) { int n=0; for(byte[] t:trozos)n+=t.length; byte[] r=new byte[n]; int p=0; for(byte[] t:trozos){System.arraycopy(t,0,r,p,t.length);p+=t.length;} return r; }
    private static String segmento(String valor) { return URLEncoder.encode(valor, StandardCharsets.UTF_8).replace("+", "%20"); }

    public static class BancoApiException extends RuntimeException {
        private final int statusCode;
        public BancoApiException(String mensaje) { this(mensaje, 0); }
        public BancoApiException(String mensaje, int statusCode) { super(mensaje); this.statusCode = statusCode; }
        public BancoApiException(String mensaje, Throwable causa) { super(mensaje, causa); this.statusCode = 0; }
        public int statusCode() { return statusCode; }
    }
}
