package com.pareja.duo.web;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.pareja.duo.usuario.Usuario;
import com.pareja.duo.usuario.UsuarioRepository;

@Controller
public class InicioController {

    private final UsuarioRepository usuarios;
    private final java.util.Set<String> vistaNueva;

    public InicioController(UsuarioRepository usuarios,
                            @org.springframework.beans.factory.annotation.Value("${duo.ahorros.vista-nueva:persona-a,persona-b}") java.util.List<String> vistaNueva) {
        this.usuarios = usuarios;
        this.vistaNueva = java.util.Set.copyOf(vistaNueva.stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    /** Pagina principal: resumen de todo (proximo plan juntos, notas, semana, horas y ahorro). */
    @GetMapping("/")
    public String resumen(Principal principal, Model model) {
        ponerUsuarios(principal, model);
        return "resumen";
    }

    /** Calendario compartido. */
    @GetMapping("/calendario")
    public String calendario(Principal principal, Model model) {
        ponerUsuarios(principal, model);
        return "calendario";
    }

    /** Horarios de clase y trabajo de los dos. */
    @GetMapping("/horarios")
    public String horarios(Principal principal, Model model) {
        ponerUsuarios(principal, model);
        return "horarios";
    }

    /**
     * Ahorros: finanzas de cada uno y objetivos en comun.
     * Quien esta en duo.ahorros.vista-nueva ve la pagina unica pensada para el banco conectado
     * (como vas, por revisar, en que se va el dinero, plan y metas); el resto, la de siempre
     * con "Mi plan" / "Mes a mes".
     */
    @GetMapping("/ahorros")
    public String ahorros(Principal principal, Model model) {
        ponerUsuarios(principal, model);
        return vistaNueva.contains(principal.getName()) ? "ahorros" : "ahorros-clasico";
    }

    /** Repartir: quedadas con amigos, quién pagó qué y quién debe a quién. */
    @GetMapping("/repartir")
    public String repartir(Principal principal, Model model) {
        ponerUsuarios(principal, model);
        return "repartir";
    }

    private void ponerUsuarios(Principal principal, Model model) {
        Usuario yo = usuarios.findByUsername(principal.getName()).orElseThrow();
        Usuario otro = usuarios.findAll().stream()
                .filter(u -> !u.getUsername().equals(yo.getUsername()))
                .findFirst()
                .orElse(yo);
        model.addAttribute("yo", yo);
        model.addAttribute("otro", otro);
    }
}
