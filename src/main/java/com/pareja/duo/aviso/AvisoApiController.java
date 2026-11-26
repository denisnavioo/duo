package com.pareja.duo.aviso;

import java.net.URI;
import java.security.Principal;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Activar y desactivar los avisos en el movil. */
@RestController
@RequestMapping("/api/avisos")
public class AvisoApiController {

    /** Solo se aceptan los servicios de avisos de los navegadores, para no mandar nada a otro sitio. */
    private static final Set<String> SERVICIOS = Set.of(
            "fcm.googleapis.com", "android.googleapis.com", "updates.push.services.mozilla.com",
            "web.push.apple.com", "api.push.apple.com");

    private final SuscripcionPushRepository suscripciones;
    private final EnviadorPush enviador;

    public AvisoApiController(SuscripcionPushRepository suscripciones, EnviadorPush enviador) {
        this.suscripciones = suscripciones;
        this.enviador = enviador;
    }

    public record Claves(String p256dh, String auth) {
    }

    public record Suscripcion(String endpoint, Claves keys) {
    }

    public record Fin(String endpoint) {
    }

    @GetMapping("/clave")
    public Map<String, String> clave() {
        return Map.of("clave", enviador.clavePublica());
    }

    /** Cuantos moviles tiene la persona con los avisos activados. */
    @GetMapping
    public Map<String, Integer> estado(Principal p) {
        return Map.of("moviles", suscripciones.findByUsuario(p.getName()).size());
    }

    @PostMapping("/suscripcion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void suscribir(@RequestBody Suscripcion s, Principal p) {
        if (s == null || s.endpoint() == null || s.keys() == null || s.keys().p256dh() == null || s.keys().auth() == null
                || s.endpoint().length() > 1000 || s.keys().p256dh().length() > 120 || s.keys().auth().length() > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Suscripción no válida");
        }
        if (!servicioValido(s.endpoint())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este navegador usa un servicio de avisos desconocido");
        }
        try {
            CifradoPush.publicaDeCrudo(CifradoPush.deB64(s.keys().p256dh()));
            if (CifradoPush.deB64(s.keys().auth()).length != 16) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Suscripción no válida");
        }
        // El mismo movil puede pasar de una cuenta a otra: se queda con la ultima
        SuscripcionPush sp = suscripciones.findByEndpoint(s.endpoint()).orElseGet(SuscripcionPush::new);
        sp.setUsuario(p.getName());
        sp.setEndpoint(s.endpoint());
        sp.setP256dh(s.keys().p256dh());
        sp.setAuth(s.keys().auth());
        suscripciones.save(sp);
    }

    @DeleteMapping("/suscripcion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void desuscribir(@RequestBody Fin f, Principal p) {
        if (f == null || f.endpoint() == null) {
            return;
        }
        suscripciones.findByEndpoint(f.endpoint())
                .filter(s -> s.getUsuario().equals(p.getName()))
                .ifPresent(suscripciones::delete);
    }

    /** Manda un aviso de prueba a los moviles de quien lo pide. */
    @PostMapping("/prueba")
    public Map<String, Integer> prueba(Principal p) {
        int n = enviador.enviar(p.getName(), new EnviadorPush.Aviso("Duo",
                "Los avisos funcionan. Cada día a las 20:00 te recordaremos los planes de mañana.", "/calendario"));
        return Map.of("entregados", n);
    }

    static boolean servicioValido(String endpoint) {
        try {
            URI u = URI.create(endpoint);
            if (!"https".equals(u.getScheme()) || u.getHost() == null) {
                return false;
            }
            String host = u.getHost().toLowerCase();
            return SERVICIOS.contains(host) || host.endsWith(".notify.windows.com") || host.endsWith(".push.apple.com");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
