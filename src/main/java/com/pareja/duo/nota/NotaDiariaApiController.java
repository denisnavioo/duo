package com.pareja.duo.nota;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Nota diaria: cada uno envia una al dia y el otro la lee. No se puede cambiar, borrar ni responder.
 * El dia se cuenta en hora de Madrid, aunque el servidor este en UTC.
 */
@RestController
@RequestMapping("/api/notas")
public class NotaDiariaApiController {

    static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    private final NotaDiariaRepository notas;

    public NotaDiariaApiController(NotaDiariaRepository notas) {
        this.notas = notas;
    }

    public record NotaDto(String autor, String texto, String actualizada) {
        static NotaDto de(NotaDiaria n) {
            return new NotaDto(n.getAutor(), n.getTexto(), n.getActualizada().toString());
        }
    }

    /** mia: la que he escrito yo hoy; delOtro: la que me ha dejado el otro hoy. */
    public record NotasHoy(String dia, NotaDto mia, NotaDto delOtro) {}

    public record TextoNota(String texto) {}

    @GetMapping("/hoy")
    @Transactional(readOnly = true)
    public NotasHoy hoy(Principal p) {
        LocalDate hoy = LocalDate.now(ZONA);
        return new NotasHoy(hoy.toString(),
                notas.findByAutorAndDia(p.getName(), hoy).map(NotaDto::de).orElse(null),
                notas.findFirstByAutorNotAndDia(p.getName(), hoy).map(NotaDto::de).orElse(null));
    }

    /** Envia mi nota de hoy. Solo una al dia: una vez enviada no se puede cambiar ni borrar. */
    @PostMapping("/hoy")
    @Transactional
    public NotasHoy enviar(@RequestBody TextoNota datos, Principal p) {
        LocalDate hoy = LocalDate.now(ZONA);
        String texto = datos == null || datos.texto() == null ? "" : datos.texto().strip();
        if (texto.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe algo antes de enviarla");
        }
        if (notas.findByAutorAndDia(p.getName(), hoy).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya has enviado la nota de hoy");
        }
        NotaDiaria nota = new NotaDiaria();
        nota.setAutor(p.getName());
        nota.setDia(hoy);
        nota.setTexto(texto.length() > NotaDiaria.MAX ? texto.substring(0, NotaDiaria.MAX) : texto);
        nota.setActualizada(LocalDateTime.now(ZONA));
        try {
            notas.saveAndFlush(nota);
        } catch (DataIntegrityViolationException e) {
            // Dos envios a la vez: la restriccion unica (autor, dia) deja pasar solo uno
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya has enviado la nota de hoy");
        }
        return hoy(p);
    }
}
