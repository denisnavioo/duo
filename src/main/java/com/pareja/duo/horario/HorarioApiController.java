package com.pareja.duo.horario;

import java.security.Principal;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.pareja.duo.novedad.Novedades;

/** Horarios semanales: los dos ven los dos horarios, pero cada uno solo edita el suyo. */
@RestController
@RequestMapping("/api/horarios")
public class HorarioApiController {

    static final Set<String> TIPOS = Set.of("CLASE", "PRACTICAS", "TRABAJO", "OTRO");

    private final BloqueHorarioRepository bloques;
    private final Novedades novedades;

    public HorarioApiController(BloqueHorarioRepository bloques, Novedades novedades) {
        this.bloques = bloques;
        this.novedades = novedades;
    }

    public record BloqueDto(Long id, String usuario, int dia, String inicio, String fin, String tipo,
                            String titulo, String detalle, String lugar) {
        static BloqueDto de(BloqueHorario b) {
            return new BloqueDto(b.getId(), b.getUsuario(), b.getDia(), b.getInicio().toString(), b.getFin().toString(),
                    b.getTipo(), b.getTitulo(), b.getDetalle(), b.getLugar());
        }
    }

    /** Para crear se pueden marcar varios dias a la vez (ej. practicas de lunes a viernes). */
    public record DatosBloque(List<Integer> dias, Integer dia, String inicio, String fin, String tipo,
                              String titulo, String detalle, String lugar) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<BloqueDto> todos() {
        return bloques.findAllByOrderByDiaAscInicioAsc().stream().map(BloqueDto::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public List<BloqueDto> crear(@RequestBody DatosBloque d, Principal p) {
        List<Integer> dias = d.dias() != null && !d.dias().isEmpty() ? d.dias() : (d.dia() == null ? List.of() : List.of(d.dia()));
        if (dias.isEmpty()) {
            throw error("Elige al menos un día");
        }
        List<BloqueDto> creados = new ArrayList<>();
        for (Integer dia : Set.copyOf(dias)) {
            BloqueHorario b = new BloqueHorario();
            b.setUsuario(p.getName());
            rellenar(b, dia, d);
            creados.add(BloqueDto.de(bloques.save(b)));
        }
        String dias2 = creados.stream().map(c -> Novedades.diaSemana(c.dia())).reduce((x, y) -> x + ", " + y).orElse("");
        BloqueDto primero = creados.get(0);
        novedades.compartida(p.getName(), "ANADIR", "HORARIOS", "añadió «" + primero.titulo() + "» a su horario ("
                + dias2 + ", " + primero.inicio() + "–" + primero.fin() + ")");
        return creados;
    }

    @PutMapping("/{id}")
    @Transactional
    public BloqueDto editar(@PathVariable Long id, @RequestBody DatosBloque d, Principal p) {
        BloqueHorario b = bloques.findByIdAndUsuario(id, p.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solo puedes cambiar tu horario"));
        rellenar(b, d.dia(), d);
        novedades.compartida(p.getName(), "CAMBIAR", "HORARIOS", "cambió «" + b.getTitulo() + "» de su horario ("
                + Novedades.diaSemana(b.getDia()) + ", " + b.getInicio() + "–" + b.getFin() + ")");
        return BloqueDto.de(b);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void borrar(@PathVariable Long id, Principal p) {
        BloqueHorario b = bloques.findByIdAndUsuario(id, p.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solo puedes cambiar tu horario"));
        novedades.compartida(p.getName(), "QUITAR", "HORARIOS", "quitó «" + b.getTitulo() + "» de su horario ("
                + Novedades.diaSemana(b.getDia()) + ", " + b.getInicio() + "–" + b.getFin() + ")");
        bloques.delete(b);
    }

    private static void rellenar(BloqueHorario b, Integer dia, DatosBloque d) {
        if (dia == null || dia < 1 || dia > 7) {
            throw error("Día no válido");
        }
        LocalTime inicio = hora(d.inicio()), fin = hora(d.fin());
        if (!fin.isAfter(inicio)) {
            throw error("La hora de fin tiene que ser después de la de inicio");
        }
        String tipo = d.tipo() == null ? "CLASE" : d.tipo();
        if (!TIPOS.contains(tipo)) {
            throw error("Tipo no válido");
        }
        String titulo = texto(d.titulo(), 40);
        if (titulo == null) {
            throw error("Escribe un título (ej. Clase de programación)");
        }
        b.setDia(dia);
        b.setInicio(inicio);
        b.setFin(fin);
        b.setTipo(tipo);
        b.setTitulo(titulo);
        b.setDetalle(texto(d.detalle(), 60));
        b.setLugar(texto(d.lugar(), 30));
    }

    private static LocalTime hora(String valor) {
        try {
            return LocalTime.parse(valor);
        } catch (DateTimeParseException | NullPointerException e) {
            throw error("Hora no válida");
        }
    }

    private static String texto(String valor, int max) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.length() > max ? limpio.substring(0, max) : limpio;
    }

    private static ResponseStatusException error(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
