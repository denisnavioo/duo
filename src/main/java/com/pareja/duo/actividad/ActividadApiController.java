package com.pareja.duo.actividad;

import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.pareja.duo.novedad.Novedades;
import com.pareja.duo.usuario.PerfilController;
import com.pareja.duo.usuario.UsuarioRepository;

/** API JSON que usa la pagina del calendario. */
@RestController
@RequestMapping("/api")
public class ActividadApiController {

    /** Iconos SVG que existen en la pagina. */
    static final Set<String> ICONOS = Set.of(
            "cuenco", "cine", "pin", "ola", "pesa", "raqueta", "unas", "libro",
            "maletin", "regalo", "musica", "tijeras", "corazon", "destello", "reloj");

    /** Fotos por plan y tamaño maximo de cada una (el navegador ya las reduce). */
    static final int MAX_FOTOS = 6;
    static final long MAX_BYTES_FOTO = 1_500_000;

    private final ActividadRepository actividades;
    private final FotoActividadRepository fotos;
    private final TipoPlanRepository tipos;
    private final UsuarioRepository usuarios;
    private final Novedades novedades;

    public ActividadApiController(ActividadRepository actividades, FotoActividadRepository fotos,
                                  TipoPlanRepository tipos, UsuarioRepository usuarios, Novedades novedades) {
        this.actividades = actividades;
        this.fotos = fotos;
        this.tipos = tipos;
        this.usuarios = usuarios;
        this.novedades = novedades;
    }

    // ---------- Actividades ----------

    public record ActividadDto(Long id, String titulo, String fecha, String hora, String quien,
                               Long tipoId, String tipoNombre, String icono, String lugar,
                               String descripcion, String estado, String creadoPor,
                               List<Long> fotos) {
        static ActividadDto de(Actividad a, List<Long> fotos) {
            return new ActividadDto(a.getId(), a.getTitulo(), a.getFecha().toString(),
                    a.getHora().toString(), a.getQuien(), a.getTipo().getId(), a.getTipo().getNombre(),
                    a.getTipo().getIcono(), a.getLugar(), a.getDescripcion(), a.estadoEfectivo(),
                    a.getCreadoPor(), fotos);
        }
    }

    private ActividadDto dto(Actividad a) {
        return ActividadDto.de(a, a.getId() == null ? List.of() : fotos.idsDe(a.getId()));
    }

    public record Respuesta(Boolean aceptar) {}

    public record NuevaActividad(String titulo, String fecha, String hora, String quien,
                                 Long tipoId, String lugar, String descripcion) {}

    /** Lo que se puede cambiar desde la ventana del plan (quien participa no cambia). */
    public record CambioActividad(String titulo, String fecha, String hora, Long tipoId,
                                  String lugar, String descripcion) {}

    @GetMapping("/actividades")
    @Transactional(readOnly = true)
    public List<ActividadDto> listar() {
        Map<Long, List<Long>> fotosPorPlan = new HashMap<>();
        for (Object[] fila : fotos.idsPorActividad()) {
            fotosPorPlan.computeIfAbsent((Long) fila[1], k -> new ArrayList<>()).add((Long) fila[0]);
        }
        return actividades.findAllByOrderByFechaAscHoraAsc().stream()
                .map(a -> ActividadDto.de(a, fotosPorPlan.getOrDefault(a.getId(), List.of()))).toList();
    }

    @PostMapping("/actividades")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ActividadDto crear(@RequestBody NuevaActividad datos, Principal principal) {
        TipoPlan tipo = tipos.findById(requerido(datos.tipoId(), "tipo de plan"))
                .orElseThrow(() -> error("El tipo de plan no existe"));
        String quien = texto(datos.quien(), 20);
        if (!"juntos".equals(quien) && (quien == null || !usuarios.existsByUsername(quien))) {
            throw error("Participante no valido");
        }

        Actividad a = new Actividad();
        a.setTipo(tipo);
        a.setQuien(quien);
        a.setFecha(fecha(datos.fecha()));
        a.setHora(hora(datos.hora()));
        String titulo = texto(datos.titulo(), 60);
        a.setTitulo(titulo == null ? tipo.getNombre() : titulo);
        a.setLugar(texto(datos.lugar(), 80));
        a.setDescripcion(texto(datos.descripcion(), 500));
        a.setCreadoPor(principal.getName());
        // Un plan para los dos queda pendiente hasta que el otro lo acepte.
        a.setEstado("juntos".equals(quien) ? Actividad.PENDIENTE : Actividad.ACEPTADA);
        Actividad guardada = actividades.save(a);
        String paraQuien = "juntos".equals(quien) ? " (plan juntos, pendiente de aceptar)"
                : quien.equals(principal.getName()) ? "" : " para " + usuarios.findByUsername(quien).map(u -> u.getNombre()).orElse(quien);
        novedades.compartida(principal.getName(), "ANADIR", "CALENDARIO",
                "añadió «" + guardada.getTitulo() + "» al calendario el " + Novedades.dia(guardada.getFecha())
                        + " a las " + Novedades.hora(guardada.getHora()) + paraQuien);
        return dto(guardada);
    }

    /** Cambia un plan desde su ventana. */
    @PutMapping("/actividades/{id}")
    @Transactional
    public ActividadDto editar(@PathVariable Long id, @RequestBody CambioActividad datos, Principal principal) {
        Actividad a = modificable(id, principal);
        TipoPlan tipo = tipos.findById(requerido(datos.tipoId(), "tipo de plan"))
                .orElseThrow(() -> error("El tipo de plan no existe"));
        a.setTipo(tipo);
        a.setFecha(fecha(datos.fecha()));
        a.setHora(hora(datos.hora()));
        String titulo = texto(datos.titulo(), 60);
        a.setTitulo(titulo == null ? tipo.getNombre() : titulo);
        a.setLugar(texto(datos.lugar(), 80));
        a.setDescripcion(texto(datos.descripcion(), 500));
        novedades.compartida(principal.getName(), "CAMBIAR", "CALENDARIO",
                "cambió «" + a.getTitulo() + "» del calendario (" + Novedades.dia(a.getFecha())
                        + " a las " + Novedades.hora(a.getHora()) + ")");
        return dto(a);
    }

    /** Borra un plan desde su ventana. */
    @DeleteMapping("/actividades/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void eliminar(@PathVariable Long id, Principal principal) {
        Actividad a = modificable(id, principal);
        novedades.compartida(principal.getName(), "QUITAR", "CALENDARIO",
                "quitó del calendario «" + a.getTitulo() + "» del " + Novedades.dia(a.getFecha()));
        fotos.borrarDe(a.getId());
        actividades.delete(a);
    }

    // ---------- Fotos de un plan ----------

    @PostMapping("/actividades/{id}/fotos")
    @Transactional
    public ActividadDto subirFoto(@PathVariable Long id, @RequestParam("foto") MultipartFile archivo,
                                  Principal principal) throws IOException {
        Actividad a = modificable(id, principal);
        if (archivo.isEmpty() || archivo.getSize() > MAX_BYTES_FOTO) {
            throw error("La foto es demasiado grande");
        }
        if (fotos.countByActividadId(a.getId()) >= MAX_FOTOS) {
            throw error("Como mucho " + MAX_FOTOS + " fotos por plan");
        }
        byte[] datos = archivo.getBytes();
        String tipo = PerfilController.tipoDeImagen(datos);
        if (tipo == null) {
            throw error("Formato no válido: usa una imagen JPG, PNG o WebP");
        }
        FotoActividad f = new FotoActividad();
        f.setActividad(a);
        f.setDatos(datos);
        f.setTipo(tipo);
        f.setSubidaPor(principal.getName());
        fotos.save(f);
        novedades.compartida(principal.getName(), "ANADIR", "CALENDARIO",
                "añadió una foto a «" + a.getTitulo() + "»");
        return dto(a);
    }

    /** Las fotos no cambian nunca (se borran y se suben otras), asi que se pueden guardar en cache. */
    @GetMapping("/actividades/fotos/{fotoId}")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> foto(@PathVariable Long fotoId) {
        FotoActividad f = fotos.findById(fotoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.getTipo()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(f.getDatos());
    }

    @DeleteMapping("/actividades/fotos/{fotoId}")
    @Transactional
    public ActividadDto quitarFoto(@PathVariable Long fotoId, Principal principal) {
        FotoActividad f = fotos.findById(fotoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La foto no existe"));
        Actividad a = modificable(f.getActividad().getId(), principal);
        fotos.delete(f);
        fotos.flush();
        novedades.compartida(principal.getName(), "QUITAR", "CALENDARIO",
                "quitó una foto de «" + a.getTitulo() + "»");
        return dto(a);
    }

    /**
     * Cada uno puede cambiar o borrar sus planes, los de los dos y los que ha creado él.
     * Los planes solo del otro (creados por el otro) son suyos.
     */
    private Actividad modificable(Long id, Principal principal) {
        Actividad a = actividades.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La actividad no existe"));
        String yo = principal.getName();
        if (Actividad.RECHAZADA.equals(a.estadoEfectivo())
                || !(yo.equals(a.getCreadoPor()) || yo.equals(a.getQuien()) || "juntos".equals(a.getQuien()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes cambiar esta actividad");
        }
        return a;
    }

    /** El otro acepta o deniega un plan "juntos" pendiente. */
    @PostMapping("/actividades/{id}/responder")
    @Transactional
    public ActividadDto responder(@PathVariable Long id, @RequestBody Respuesta respuesta, Principal principal) {
        Actividad a = actividades.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La actividad no existe"));
        if (!Actividad.PENDIENTE.equals(a.estadoEfectivo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta actividad ya no está pendiente");
        }
        if (principal.getName().equals(a.getCreadoPor())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tiene que responder la otra persona");
        }
        if (respuesta == null || respuesta.aceptar() == null) {
            throw error("Falta la respuesta");
        }
        a.setEstado(respuesta.aceptar() ? Actividad.ACEPTADA : Actividad.RECHAZADA);
        novedades.compartida(principal.getName(), respuesta.aceptar() ? "ACEPTAR" : "RECHAZAR", "CALENDARIO",
                (respuesta.aceptar() ? "aceptó" : "denegó") + " el plan juntos «" + a.getTitulo() + "» del "
                        + Novedades.dia(a.getFecha()));
        return dto(a);
    }

    /** Quien creo un plan rechazado lo quita tras ver el aviso. */
    @PostMapping("/actividades/{id}/descartar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void descartar(@PathVariable Long id, Principal principal) {
        Actividad a = actividades.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La actividad no existe"));
        if (!Actividad.RECHAZADA.equals(a.estadoEfectivo()) || !principal.getName().equals(a.getCreadoPor())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No se puede descartar esta actividad");
        }
        novedades.compartida(principal.getName(), "QUITAR", "CALENDARIO",
                "quitó del calendario el plan denegado «" + a.getTitulo() + "»");
        fotos.borrarDe(a.getId());
        actividades.delete(a);
    }

    // ---------- Tipos de plan ----------

    public record TipoDto(Long id, String nombre, String icono, boolean propio) {
        static TipoDto de(TipoPlan t) {
            return new TipoDto(t.getId(), t.getNombre(), t.getIcono(), t.isPropio());
        }
    }

    public record NuevoTipo(String nombre, String icono) {}

    @GetMapping("/tipos")
    @Transactional(readOnly = true)
    public List<TipoDto> tipos() {
        return tipos.findAllByOrderByOrdenAscIdAsc().stream().map(TipoDto::de).toList();
    }

    @PostMapping("/tipos")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TipoDto crearTipo(@RequestBody NuevoTipo datos, Principal principal) {
        String nombre = texto(datos.nombre(), 30);
        if (nombre == null) {
            throw error("Escribe un nombre para el tipo de plan");
        }
        if (datos.icono() == null || !ICONOS.contains(datos.icono())) {
            throw error("Icono no valido");
        }
        if (tipos.existsByNombreIgnoreCase(nombre)) {
            throw error("Ya existe un tipo de plan con ese nombre");
        }
        // Los tipos propios van antes de "Otro" (orden 999).
        novedades.compartida(principal.getName(), "ANADIR", "CALENDARIO", "creó el tipo de plan «" + nombre + "»");
        return TipoDto.de(tipos.save(new TipoPlan(nombre, datos.icono(), 500, true)));
    }

    // ---------- Utilidades ----------

    private static String texto(String valor, int max) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.length() > max ? limpio.substring(0, max) : limpio;
    }

    private static <T> T requerido(T valor, String campo) {
        if (valor == null) {
            throw error("Falta el campo: " + campo);
        }
        return valor;
    }

    private static LocalDate fecha(String valor) {
        try {
            return LocalDate.parse(requerido(valor, "fecha"));
        } catch (DateTimeParseException e) {
            throw error("Fecha no valida");
        }
    }

    private static LocalTime hora(String valor) {
        try {
            return LocalTime.parse(requerido(valor, "hora"));
        } catch (DateTimeParseException e) {
            throw error("Hora no valida");
        }
    }

    private static ResponseStatusException error(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
