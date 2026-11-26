package com.pareja.duo.web;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.pareja.duo.usuario.Usuario;
import com.pareja.duo.usuario.UsuarioRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Pantalla de entrada: «¿Quién eres?» con las dos personas. Sin contraseñas. */
@Controller
public class EntrarController {

    private final UsuarioRepository usuarios;
    private final SecurityContextRepository contextos = new HttpSessionSecurityContextRepository();

    public EntrarController(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping("/entrar")
    public String elegir(Model model) {
        List<Usuario> personas = usuarios.findAll().stream()
                .sorted(java.util.Comparator.comparing(Usuario::getId))
                .toList();
        model.addAttribute("personas", personas);
        return "entrar";
    }

    @PostMapping("/entrar")
    public String entrar(@RequestParam("quien") String quien, HttpServletRequest req, HttpServletResponse res) {
        if (quien == null || !usuarios.existsByUsername(quien)) {
            return "redirect:/entrar";
        }
        // Sesion nueva al cambiar de persona, para que no se reutilice la anterior
        if (req.getSession(false) != null) {
            req.changeSessionId();
        }
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                quien, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(contexto);
        contextos.saveContext(contexto, req, res);
        return "redirect:/";
    }
}
