package com.pareja.duo.ahorros;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping("/api/ahorros/banco")
public class BancoApiController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BancoApiController.class);
    /**
     * Fallos del proveedor bancario: 424 y no 502, para que un proxy delante de Duo no cambie
     * la respuesta por su propia pagina de error y el mensaje llegue a verse.
     */
    private static final HttpStatus FALLO_PROVEEDOR = HttpStatus.FAILED_DEPENDENCY;
    private static final String ESTADO_PENDIENTE = "duo.banco.state";
    private static final String USUARIO_PENDIENTE = "duo.banco.usuario";
    private final ServicioBanco servicio;

    public BancoApiController(ServicioBanco servicio) { this.servicio = servicio; }

    @GetMapping("/estado") public ServicioBanco.Estado estado(Principal p) { return servicio.estado(p.getName()); }

    @PostMapping("/conectar")
    public Map<String,String> conectar(Principal p, HttpServletRequest request) {
        exigirPermiso(p);
        String state = UUID.randomUUID().toString();
        HttpSession sesion = request.getSession(true);
        sesion.setAttribute(ESTADO_PENDIENTE, state);
        sesion.setAttribute(USUARIO_PENDIENTE, p.getName());
        try { return Map.of("url", servicio.iniciar(p.getName(), state)); }
        catch (RuntimeException e) {
            sesion.removeAttribute(ESTADO_PENDIENTE); sesion.removeAttribute(USUARIO_PENDIENTE);
            log.warn("No se pudo iniciar la conexion bancaria de «{}»: {}", p.getName(), motivo(e));
            throw new ResponseStatusException(FALLO_PROVEEDOR, mensaje(e));
        }
    }

    @PostMapping("/sincronizar")
    public Map<String,Integer> sincronizar(Principal p, jakarta.servlet.http.HttpServletRequest peticion) {
        exigirPermiso(p);
        try { return Map.of("importados", servicio.sincronizar(p.getName(), psu(peticion))); }
        catch (IllegalStateException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje(e)); }
        catch (RuntimeException e) {
            log.warn("No se pudo sincronizar el banco de «{}»: {}", p.getName(), motivo(e));
            if (e instanceof EnableBankingCliente.BancoApiException api && api.statusCode() == 429)
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "El banco no deja más consultas por ahora. Inténtalo dentro de un rato.");
            throw new ResponseStatusException(FALLO_PROVEEDOR, "No se pudo sincronizar el banco. Revisa el estado e inténtalo de nuevo.");
        }
    }

    @DeleteMapping("/desconectar") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desconectar(Principal p) { exigirPermiso(p); servicio.desconectar(p.getName()); }

    @GetMapping("/reglas") public List<ServicioBanco.ReglaDto> reglas(Principal p) {
        return servicio.permitido(p.getName()) ? servicio.reglas(p.getName()) : List.of();
    }
    @PostMapping("/reglas") @ResponseStatus(HttpStatus.CREATED)
    public ServicioBanco.ReglaDto crearRegla(@RequestBody ServicioBanco.AltaRegla d, Principal p) {
        exigirPermiso(p);
        try { return servicio.crearRegla(p.getName(), d); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje(e)); }
    }
    @PutMapping("/reglas/{id}")
    public ServicioBanco.ReglaDto actualizarRegla(@PathVariable Long id, @RequestBody ServicioBanco.AltaRegla d, Principal p) {
        exigirPermiso(p);
        try { return servicio.actualizarRegla(p.getName(), id, d); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje(e)); }
    }
    @DeleteMapping("/reglas/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrarRegla(@PathVariable Long id, Principal p) {
        exigirPermiso(p);
        try { servicio.borrarRegla(p.getName(), id); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, mensaje(e)); }
    }
    @GetMapping("/revisar") public List<ServicioBanco.RevisarDto> revisar(Principal p) {
        return servicio.permitido(p.getName()) ? servicio.porRevisar(p.getName()) : List.of();
    }
    @PostMapping("/revisar/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolver(@PathVariable Long id, @RequestBody ServicioBanco.Revision d, Principal p) {
        exigirPermiso(p);
        try { servicio.revisar(p.getName(), id, d); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje(e)); }
    }

    private void exigirPermiso(Principal p) {
        if (!servicio.permitido(p.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La conexión con el banco no está disponible para esta cuenta");
        }
    }

    /** Para el registro: el tipo de error y su mensaje, y la causa si la hay (nunca cuerpos del banco ni claves). */
    /**
     * Quien pulsa "Sincronizar": su IP y su navegador. El banco lo exige para
     * contar la consulta como hecha por el usuario y no gastar las 4 automaticas del dia.
     */
    static BancoCliente.Psu psu(jakarta.servlet.http.HttpServletRequest peticion) {
        String ip = peticion.getRemoteAddr();
        if (ip == null || !ip.matches("[0-9A-Fa-f:.]{2,45}")) return null;
        String agente = peticion.getHeader("User-Agent");
        if (agente != null) agente = agente.replaceAll("[\r\n]", " ");
        if (agente != null && agente.length() > 300) agente = agente.substring(0, 300);
        return new BancoCliente.Psu(ip, agente);
    }

    private static String motivo(RuntimeException e) {
        String m = e.getClass().getSimpleName() + ": " + e.getMessage();
        Throwable c = e.getCause();
        return c == null ? m : m + " (causa: " + c.getClass().getSimpleName() + ": " + c.getMessage() + ")";
    }

    private static String mensaje(RuntimeException e) {
        return e.getMessage() == null || e.getMessage().isBlank() ? "No se pudo completar la operación" : e.getMessage();
    }
}

@Controller
class VueltaBancoController {
    private static final String ESTADO_PENDIENTE = "duo.banco.state";
    private static final String USUARIO_PENDIENTE = "duo.banco.usuario";
    private final ServicioBanco servicio;
    VueltaBancoController(ServicioBanco servicio) { this.servicio=servicio; }

    @GetMapping("/ahorros/banco/vuelta")
    @Transactional
    public RedirectView volver(@RequestParam(required = false) String code,
                               @RequestParam(required = false) String state,
                               @RequestParam(required = false) String error,
                               Principal principal, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object stateGuardado = session == null ? null : session.getAttribute(ESTADO_PENDIENTE);
        Object usuario = session == null ? null : session.getAttribute(USUARIO_PENDIENTE);
        if (session != null) { session.removeAttribute(ESTADO_PENDIENTE); session.removeAttribute(USUARIO_PENDIENTE); }
        if (principal == null || stateGuardado == null || usuario == null || !principal.getName().equals(usuario)
                || state == null || !stateGuardado.toString().equals(state)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La vuelta del banco no coincide con la solicitud iniciada");
        }
        if (!servicio.permitido(principal.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La conexión con el banco no está disponible para esta cuenta");
        }
        if (error != null || code == null || code.isBlank()) {
            org.slf4j.LoggerFactory.getLogger(VueltaBancoController.class).warn("Autorizacion bancaria cancelada o sin codigo ({})", error == null ? "sin codigo" : error);
            return new RedirectView("/ahorros?banco=cancelado");
        }
        try { servicio.completar(principal.getName(), code, BancoApiController.psu(request)); return new RedirectView("/ahorros?banco=conectado"); }
        catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(VueltaBancoController.class).warn("No se pudo completar la conexion bancaria: {}: {}",
                    e.getClass().getSimpleName(), e.getMessage());
            return new RedirectView("/ahorros?banco=error");
        }
    }
}
