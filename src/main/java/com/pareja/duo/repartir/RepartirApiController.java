package com.pareja.duo.repartir;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.http.HttpStatus;
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
import org.springframework.web.server.ResponseStatusException;

import com.pareja.duo.ahorros.MovimientoBanco;
import com.pareja.duo.ahorros.MovimientoBancoRepository;
import com.pareja.duo.ahorros.ServicioBanco;
import com.pareja.duo.novedad.Novedades;
import com.pareja.duo.usuario.Usuario;
import com.pareja.duo.usuario.UsuarioRepository;

/**
 * API de "Repartir": quedadas con amigos donde cada uno paga algo,
 * y Duo calcula quién tiene que pasarle dinero a quién. Las ven y editan los dos.
 */
@RestController
@RequestMapping("/api/repartir")
public class RepartirApiController {

    private static final BigDecimal MAXIMO = new BigDecimal("100000");
    private static final int MAX_PERSONAS = 30;
    private static final Set<String> TIPOS = Set.of("COMIDA", "COPAS", "OCIO", "VIAJE", "FIESTA", "REGALO", "OTRO");

    private final QuedadaRepository quedadas;
    private final Novedades novedades;
    private final PagoQuedadaRepository pagos;
    private final MovimientoBancoRepository movimientosBanco;
    private final UsuarioRepository usuarios;
    private final ServicioBanco banco;

    public RepartirApiController(QuedadaRepository quedadas, Novedades novedades, PagoQuedadaRepository pagos,
                                 MovimientoBancoRepository movimientosBanco, UsuarioRepository usuarios, ServicioBanco banco) {
        this.quedadas = quedadas;
        this.novedades = novedades;
        this.pagos = pagos;
        this.movimientosBanco = movimientosBanco;
        this.usuarios = usuarios;
        this.banco = banco;
    }

    // ================= Lo que se devuelve =================

    public record GastoDto(Long id, String concepto, BigDecimal importe, String pagador, List<String> participantes) {}

    /**
     * Prueba en el banco de un Bizum: quien lo comprobo (su nombre), el dia del movimiento y, solo para el,
     * el concepto que pone su banco (el resto no ve datos de una cuenta que no es suya).
     */
    public record Comprobante(String quien, String fecha, String concepto, boolean mio) {}

    public record PagoDto(Long id, String de, String para, BigDecimal importe, Comprobante banco) {}

    public record QuedadaDto(Long id, String nombre, String fecha, String tipo, String creadoPor, List<String> personas,
                             List<GastoDto> gastos, List<PagoDto> pagos, BigDecimal total,
                             List<CalculoDeudas.Saldo> saldos, List<CalculoDeudas.Transferencia> transferencias) {

        static QuedadaDto de(Quedada q) {
            return de(q, pago -> null);
        }

        static QuedadaDto de(Quedada q, java.util.function.Function<PagoQuedada, Comprobante> comprobante) {
            List<String> personas = List.copyOf(q.getPersonas());
            List<GastoDto> gastos = q.getGastos().stream()
                    .map(g -> new GastoDto(g.getId(), g.getConcepto(), g.getImporte(), g.getPagador(),
                            personas.stream().filter(g.getParticipantes()::contains).toList()))
                    .toList();
            List<PagoDto> pagos = q.getPagos().stream()
                    .map(p -> new PagoDto(p.getId(), p.getDe(), p.getPara(), p.getImporte(), comprobante.apply(p)))
                    .toList();
            CalculoDeudas.Resultado r = CalculoDeudas.calcular(personas,
                    gastos.stream().map(g -> new CalculoDeudas.Gasto(g.importe(), g.pagador(), g.participantes())).toList(),
                    pagos.stream().map(p -> new CalculoDeudas.Pago(p.de(), p.para(), p.importe())).toList());
            BigDecimal total = gastos.stream().map(GastoDto::importe).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new QuedadaDto(q.getId(), q.getNombre(), q.getFecha().toString(), RepartirApiController.tipo(q), q.getCreadoPor(), personas,
                    gastos, pagos, total, r.saldos(), r.transferencias());
        }
    }

    /** Resumen para la lista: quién vino, cuánto se gastó, cuánto queda por pasar y el saldo de cada uno. */
    public record QuedadaResumenDto(Long id, String nombre, String fecha, String tipo, List<String> personas,
                                    int gastos, BigDecimal total, BigDecimal pendiente, List<CalculoDeudas.Saldo> saldos,
                                    List<CalculoDeudas.Transferencia> transferencias) {

        static QuedadaResumenDto de(Quedada q) {
            QuedadaDto d = QuedadaDto.de(q);
            BigDecimal pendiente = d.transferencias().stream()
                    .map(CalculoDeudas.Transferencia::importe).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new QuedadaResumenDto(d.id(), d.nombre(), d.fecha(), d.tipo(), d.personas(), d.gastos().size(),
                    d.total(), pendiente, d.saldos(), d.transferencias());
        }
    }

    private static String tipo(Quedada q) {
        return q.getTipo() == null ? "OTRO" : q.getTipo();
    }

    // ================= Lo que se recibe =================

    public record NuevaQuedada(String nombre, String fecha, String tipo, List<String> personas) {}

    public record NuevaPersona(String nombre) {}

    public record NuevoGasto(String concepto, BigDecimal importe, String pagador, List<String> participantes) {}

    public record NuevoPago(String de, String para, BigDecimal importe) {}

    // ================= Quedadas =================

    @GetMapping("/quedadas")
    @Transactional(readOnly = true)
    public List<QuedadaResumenDto> lista() {
        return quedadas.findAllByOrderByFechaDescIdDesc().stream().map(QuedadaResumenDto::de).toList();
    }

    /** Nombres usados en quedadas anteriores, para proponerlos al crear una nueva. */
    @GetMapping("/personas")
    @Transactional(readOnly = true)
    public Set<String> conocidas() {
        Set<String> nombres = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        quedadas.findAll().forEach(q -> nombres.addAll(q.getPersonas()));
        return nombres;
    }

    @GetMapping("/quedadas/{id}")
    @Transactional(readOnly = true)
    public QuedadaDto ver(@PathVariable Long id, Principal p) {
        return dto(buscar(id), p);
    }

    @PostMapping("/quedadas")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public QuedadaDto crear(@RequestBody NuevaQuedada d, Principal p) {
        Quedada q = new Quedada();
        q.setNombre(requerido(texto(d.nombre(), 60), "Ponle un nombre a la quedada"));
        q.setFecha(d.fecha() == null || d.fecha().isBlank() ? LocalDate.now() : fecha(d.fecha()));
        q.setCreadoPor(p.getName());
        q.setTipo(d.tipo() != null && TIPOS.contains(d.tipo()) ? d.tipo() : "OTRO");
        List<String> personas = new ArrayList<>();
        for (String nombre : d.personas() == null ? List.<String>of() : d.personas()) {
            String limpio = texto(nombre, 30);
            if (limpio != null && buscarNombre(personas, limpio) == null) {
                personas.add(limpio);
            }
        }
        if (personas.size() < 2) {
            throw error("Añade al menos a dos personas");
        }
        if (personas.size() > MAX_PERSONAS) {
            throw error("Como mucho " + MAX_PERSONAS + " personas");
        }
        q.setPersonas(personas);
        novedades.compartida(p.getName(), "ANADIR", "REPARTIR", "creó la quedada «" + q.getNombre() + "»");
        return dto(quedadas.save(q), p);
    }

    @DeleteMapping("/quedadas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void borrar(@PathVariable Long id, Principal p) {
        Quedada q = buscar(id);
        novedades.compartida(p.getName(), "QUITAR", "REPARTIR", "borró la quedada «" + q.getNombre() + "»");
        quedadas.delete(q);
    }

    // ================= Personas =================

    @PostMapping("/quedadas/{id}/personas")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public QuedadaDto anadirPersona(@PathVariable Long id, @RequestBody NuevaPersona d, Principal p) {
        Quedada q = buscar(id);
        String nombre = requerido(texto(d.nombre(), 30), "Escribe un nombre");
        if (buscarNombre(q.getPersonas(), nombre) != null) {
            throw error(nombre + " ya está en la quedada");
        }
        if (q.getPersonas().size() >= MAX_PERSONAS) {
            throw error("Como mucho " + MAX_PERSONAS + " personas");
        }
        q.getPersonas().add(nombre);
        return dto(q, p);
    }

    @DeleteMapping("/quedadas/{id}/personas")
    @Transactional
    public QuedadaDto quitarPersona(@PathVariable Long id, @RequestParam String nombre, Principal p) {
        Quedada q = buscar(id);
        String persona = persona(q, nombre);
        boolean usada = q.getGastos().stream().anyMatch(g -> g.getPagador().equals(persona) || g.getParticipantes().contains(persona))
                || q.getPagos().stream().anyMatch(x -> x.getDe().equals(persona) || x.getPara().equals(persona));
        if (usada) {
            throw error(persona + " aparece en algún gasto. Quítala primero de esos gastos.");
        }
        if (q.getPersonas().size() <= 2) {
            throw error("Tiene que haber al menos dos personas");
        }
        q.getPersonas().remove(persona);
        return dto(q, p);
    }

    // ================= Gastos =================

    @PostMapping("/quedadas/{id}/gastos")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public QuedadaDto anadirGasto(@PathVariable Long id, @RequestBody NuevoGasto d, Principal p) {
        Quedada q = buscar(id);
        GastoQuedada g = new GastoQuedada();
        g.setQuedada(q);
        rellenar(q, g, d);
        q.getGastos().add(g);
        novedades.compartida(p.getName(), "ANADIR", "REPARTIR",
                "apuntó «" + g.getConcepto() + "» (" + Novedades.euros(g.getImporte()) + ", pagó " + g.getPagador() + ") en «" + q.getNombre() + "»");
        return dto(quedadas.saveAndFlush(q), p);
    }

    @PutMapping("/quedadas/{id}/gastos/{gastoId}")
    @Transactional
    public QuedadaDto cambiarGasto(@PathVariable Long id, @PathVariable Long gastoId, @RequestBody NuevoGasto d, Principal p) {
        Quedada q = buscar(id);
        GastoQuedada g = q.getGastos().stream().filter(x -> x.getId().equals(gastoId)).findFirst()
                .orElseThrow(RepartirApiController::noEncontrado);
        rellenar(q, g, d);
        return dto(q, p);
    }

    @DeleteMapping("/quedadas/{id}/gastos/{gastoId}")
    @Transactional
    public QuedadaDto borrarGasto(@PathVariable Long id, @PathVariable Long gastoId, Principal p) {
        Quedada q = buscar(id);
        if (!q.getGastos().removeIf(g -> g.getId().equals(gastoId))) {
            throw noEncontrado();
        }
        return dto(q, p);
    }

    // ================= Bizums hechos =================

    @PostMapping("/quedadas/{id}/pagos")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public QuedadaDto anadirPago(@PathVariable Long id, @RequestBody NuevoPago d, Principal p) {
        Quedada q = buscar(id);
        PagoQuedada pago = new PagoQuedada();
        pago.setQuedada(q);
        pago.setDe(persona(q, d.de()));
        pago.setPara(persona(q, d.para()));
        if (pago.getDe().equals(pago.getPara())) {
            throw error("Elige a dos personas distintas");
        }
        pago.setImporte(importe(d.importe()));
        q.getPagos().add(pago);
        novedades.compartida(p.getName(), "ANADIR", "REPARTIR",
                "apuntó que " + pago.getDe() + " pasó " + Novedades.euros(pago.getImporte()) + " a " + pago.getPara() + " («" + q.getNombre() + "»)");
        return dto(quedadas.saveAndFlush(q), p);
    }

    @DeleteMapping("/quedadas/{id}/pagos/{pagoId}")
    @Transactional
    public QuedadaDto borrarPago(@PathVariable Long id, @PathVariable Long pagoId, Principal p) {
        Quedada q = buscar(id);
        if (!q.getPagos().removeIf(x -> x.getId().equals(pagoId))) {
            throw noEncontrado();
        }
        return dto(q, p);
    }

    // ================= Comprobar un Bizum con el banco =================

    /** Un movimiento del banco que podria ser este Bizum; "coincide" si el importe es el mismo. */
    public record CandidatoDto(Long id, String fecha, String concepto, BigDecimal importe, boolean coincide) {}

    public record Vincular(Long movimientoId) {}

    /**
     * Movimientos del banco de quien mira que pueden ser este Bizum: del mismo sentido (si lo hizo, salidas;
     * si lo recibio, entradas), desde una semana antes de la quedada hasta tres meses despues, sin los ya usados.
     * Primero los del mismo importe, luego los que dicen "Bizum", y por cercania a la fecha.
     */
    @GetMapping("/quedadas/{id}/pagos/{pagoId}/banco")
    @Transactional(readOnly = true)
    public List<CandidatoDto> candidatos(@PathVariable Long id, @PathVariable Long pagoId, Principal p) {
        Quedada q = buscar(id);
        PagoQuedada pago = pago(q, pagoId);
        boolean recibido = sentido(pago, p);
        LocalDate desde = q.getFecha().minusDays(7), hasta = q.getFecha().plusDays(90);
        Set<Long> usados = new java.util.HashSet<>(pagos.movimientosUsados(p.getName()));
        if (pago.getBancoMovimientoId() != null) usados.remove(pago.getBancoMovimientoId());
        return movimientosBanco.findByUsuarioAndFechaBetweenOrderByFechaDescIdDesc(p.getName(), desde, hasta).stream()
                .filter(m -> !usados.contains(m.getId()))
                .filter(m -> { Boolean entra = entrada(m); return entra == null || entra == recibido; })
                .sorted(java.util.Comparator
                        .comparing((MovimientoBanco m) -> m.getImporte().compareTo(pago.getImporte()) != 0)
                        .thenComparing(m -> !m.getConcepto().toLowerCase(Locale.ROOT).contains("bizum"))
                        .thenComparing(m -> Math.abs(java.time.temporal.ChronoUnit.DAYS.between(q.getFecha(), m.getFecha()))))
                .limit(40)
                .map(m -> new CandidatoDto(m.getId(), m.getFecha().toString(), m.getConcepto(), m.getImporte(),
                        m.getImporte().compareTo(pago.getImporte()) == 0))
                .toList();
    }

    @PutMapping("/quedadas/{id}/pagos/{pagoId}/banco")
    @Transactional
    public QuedadaDto vincular(@PathVariable Long id, @PathVariable Long pagoId, @RequestBody Vincular d, Principal p) {
        Quedada q = buscar(id);
        PagoQuedada pago = pago(q, pagoId);
        boolean recibido = sentido(pago, p);
        if (pago.getBancoUsuario() != null && !pago.getBancoUsuario().equals(p.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Este Bizum ya lo ha comprobado la otra persona");
        }
        MovimientoBanco m = d == null || d.movimientoId() == null ? null
                : movimientosBanco.findByIdAndUsuario(d.movimientoId(), p.getName()).orElse(null);
        if (m == null) throw noEncontrado();
        Boolean entra = entrada(m);
        if (entra != null && entra != recibido) {
            throw error(recibido ? "Ese movimiento es una salida de dinero, no un Bizum recibido"
                    : "Ese movimiento es una entrada de dinero, no un Bizum enviado");
        }
        if (!m.getId().equals(pago.getBancoMovimientoId()) && pagos.movimientosUsados(p.getName()).contains(m.getId())) {
            throw error("Ese movimiento ya demuestra otro Bizum");
        }
        pago.setBancoMovimientoId(m.getId());
        pago.setBancoUsuario(p.getName());
        novedades.compartida(p.getName(), "CAMBIAR", "REPARTIR", "comprobó con su banco que " + pago.getDe() + " pasó "
                + Novedades.euros(pago.getImporte()) + " a " + pago.getPara() + " («" + q.getNombre() + "»)");
        return dto(quedadas.saveAndFlush(q), p);
    }

    @DeleteMapping("/quedadas/{id}/pagos/{pagoId}/banco")
    @Transactional
    public QuedadaDto desvincular(@PathVariable Long id, @PathVariable Long pagoId, Principal p) {
        Quedada q = buscar(id);
        PagoQuedada pago = pago(q, pagoId);
        if (pago.getBancoUsuario() != null && !pago.getBancoUsuario().equals(p.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede quitarlo quien lo comprobó");
        }
        pago.setBancoMovimientoId(null);
        pago.setBancoUsuario(null);
        return dto(q, p);
    }

    /** La quedada con la prueba del banco de cada Bizum, vista por quien pregunta. */
    private QuedadaDto dto(Quedada q, Principal p) {
        String yo = p == null ? null : p.getName();
        List<Long> ids = q.getPagos().stream().map(PagoQuedada::getBancoMovimientoId).filter(java.util.Objects::nonNull).toList();
        java.util.Map<Long, MovimientoBanco> movs = new java.util.HashMap<>();
        if (!ids.isEmpty()) movimientosBanco.findAllById(ids).forEach(m -> movs.put(m.getId(), m));
        java.util.Map<String, String> nombres = new java.util.HashMap<>();
        return QuedadaDto.de(q, pago -> {
            if (pago.getBancoMovimientoId() == null || pago.getBancoUsuario() == null) return null;
            MovimientoBanco m = movs.get(pago.getBancoMovimientoId());
            if (m == null || !m.getUsuario().equals(pago.getBancoUsuario())) return null;
            boolean mio = pago.getBancoUsuario().equals(yo);
            String quien = nombres.computeIfAbsent(pago.getBancoUsuario(),
                    u -> usuarios.findByUsername(u).map(Usuario::getNombre).orElse(u));
            return new Comprobante(quien, m.getFecha().toString(), mio ? m.getConcepto() : null, mio);
        });
    }

    private static PagoQuedada pago(Quedada q, Long pagoId) {
        return q.getPagos().stream().filter(x -> x.getId().equals(pagoId)).findFirst()
                .orElseThrow(RepartirApiController::noEncontrado);
    }

    /**
     * true si quien mira recibio este Bizum, false si lo hizo. Solo se puede comprobar con el banco
     * uno en el que sales tu (por tu nombre en la quedada) y si tienes el banco conectado.
     */
    private boolean sentido(PagoQuedada pago, Principal p) {
        if (!banco.permitido(p.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La conexión con el banco no está disponible para esta cuenta");
        }
        String nombre = usuarios.findByUsername(p.getName()).map(Usuario::getNombre).orElse(p.getName());
        if (pago.getPara().equalsIgnoreCase(nombre)) return true;
        if (pago.getDe().equalsIgnoreCase(nombre)) return false;
        throw error("Solo puedes comprobar los Bizums que has hecho o recibido tú");
    }

    /** Si entro dinero: lo guardado, o deducido de si se apunto como ingreso o como gasto. */
    private static Boolean entrada(MovimientoBanco m) {
        if (m.getEntrada() != null) return m.getEntrada();
        if (m.getIngresoId() != null) return true;
        if (m.getGastoId() != null) return false;
        return null;
    }

    // ================= Utilidades =================

    private void rellenar(Quedada q, GastoQuedada g, NuevoGasto d) {
        g.setConcepto(requerido(texto(d.concepto(), 60), "Escribe qué se pagó"));
        g.setImporte(importe(d.importe()));
        g.setPagador(persona(q, d.pagador()));
        Set<String> entre = new LinkedHashSet<>();
        for (String nombre : d.participantes() == null ? List.<String>of() : d.participantes()) {
            entre.add(persona(q, nombre));
        }
        if (entre.isEmpty()) {
            throw error("Elige entre quiénes se reparte");
        }
        g.getParticipantes().clear();
        g.getParticipantes().addAll(entre);
    }

    private Quedada buscar(Long id) {
        return quedadas.findById(id).orElseThrow(RepartirApiController::noEncontrado);
    }

    /** Devuelve el nombre tal como está guardado en la quedada, o un error si no viene. */
    private static String persona(Quedada q, String nombre) {
        String encontrado = nombre == null ? null : buscarNombre(q.getPersonas(), nombre.trim());
        if (encontrado == null) {
            throw error("Esa persona no está en la quedada");
        }
        return encontrado;
    }

    private static String buscarNombre(List<String> personas, String nombre) {
        String clave = nombre.toLowerCase(Locale.ROOT);
        return personas.stream().filter(x -> x.toLowerCase(Locale.ROOT).equals(clave)).findFirst().orElse(null);
    }

    private static LocalDate fecha(String valor) {
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            throw error("Fecha no válida");
        }
    }

    private static BigDecimal importe(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0 || valor.compareTo(MAXIMO) > 0 || !decimalesValidos(valor)) {
            throw error("Escribe un importe válido");
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Un importe con mas de 2 decimales (p. ej. 1e-600000000) se rechaza antes de redondearlo:
     * redondear un numero con millones de decimales dejaria el servidor calculando un buen rato.
     * Los ceros sobrantes (12.500) si valen.
     */
    private static boolean decimalesValidos(BigDecimal v) {
        return v.scale() <= 2 || v.stripTrailingZeros().scale() <= 2;
    }

    private static String texto(String valor, int max) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.trim().replaceAll("\\s+", " ");
        return limpio.length() > max ? limpio.substring(0, max) : limpio;
    }

    private static <T> T requerido(T valor, String mensaje) {
        if (valor == null) {
            throw error(mensaje);
        }
        return valor;
    }

    private static ResponseStatusException noEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontrado");
    }

    private static ResponseStatusException error(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
