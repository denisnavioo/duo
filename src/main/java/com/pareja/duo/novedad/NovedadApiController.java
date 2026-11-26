package com.pareja.duo.novedad;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pareja.duo.usuario.Usuario;
import com.pareja.duo.usuario.UsuarioRepository;

/** La campanita: lo ultimo que se ha añadido y quitado. */
@RestController
@RequestMapping("/api/novedades")
public class NovedadApiController {

    private final NovedadRepository novedades;
    private final UsuarioRepository usuarios;

    public NovedadApiController(NovedadRepository novedades, UsuarioRepository usuarios) {
        this.novedades = novedades;
        this.usuarios = usuarios;
    }

    public record NovedadDto(Long id, String usuario, String accion, String seccion, String texto,
                             String fecha, boolean privado, boolean nueva) {}

    public record ListaDto(long noLeidas, List<NovedadDto> novedades) {}

    @GetMapping
    @Transactional(readOnly = true)
    public ListaDto lista(Principal p) {
        String yo = p.getName();
        LocalDateTime desde = ultimaVista(yo);
        List<NovedadDto> lista = novedades.visiblesPara(yo, PageRequest.of(0, 60)).stream()
                .map(n -> new NovedadDto(n.getId(), n.getUsuario(), n.getAccion(), n.getSeccion(), n.getTexto(),
                        n.getFecha().toString(), n.isPrivado(),
                        !n.getUsuario().equals(yo) && n.getFecha().isAfter(desde)))
                .toList();
        return new ListaDto(novedades.noLeidas(yo, desde), lista);
    }

    /** Al abrir la campanita se marcan como vistas. */
    @PostMapping("/vistas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void marcarVistas(Principal p) {
        usuarios.findByUsername(p.getName()).ifPresent(u -> u.setUltimaVistaNovedades(LocalDateTime.now()));
    }

    private LocalDateTime ultimaVista(String yo) {
        return usuarios.findByUsername(yo).map(Usuario::getUltimaVistaNovedades)
                .orElse(LocalDateTime.of(2000, 1, 1, 0, 0));
    }
}
