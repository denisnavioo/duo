package com.pareja.duo.ahorros;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.pareja.duo.novedad.Novedades;

/**
 * API del apartado de Ahorros.
 * Ingresos, reparto, gastos, suscripciones y cuentas de ahorro son de cada uno: solo los ve su dueño.
 * Los objetivos de ahorro son comunes: los ven y aportan los dos.
 */
@RestController
@RequestMapping("/api/ahorros")
public class AhorrosApiController {

    private static final Set<String> FRECUENCIAS = Set.of("MENSUAL", "TRIMESTRAL", "ANUAL");
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal MAXIMO = new BigDecimal("1000000");

    private final IngresoRepository ingresos;
    private final RepartoRepository repartos;
    private final GastoRepository gastos;
    private final SuscripcionRepository suscripciones;
    private final MovimientoAhorroRepository movimientos;
    private final ObjetivoAhorroRepository objetivos;
    private final Novedades novedades;
    private final MovimientoBancoRepository movimientosBanco;

    public AhorrosApiController(IngresoRepository ingresos, RepartoRepository repartos, GastoRepository gastos,
                                SuscripcionRepository suscripciones, MovimientoAhorroRepository movimientos,
                                ObjetivoAhorroRepository objetivos, Novedades novedades, MovimientoBancoRepository movimientosBanco) {
        this.ingresos = ingresos;
        this.repartos = repartos;
        this.gastos = gastos;
        this.suscripciones = suscripciones;
        this.movimientos = movimientos;
        this.objetivos = objetivos;
        this.novedades = novedades;
        this.movimientosBanco = movimientosBanco;
    }

    // ================= Mes: ingresos, reparto y gastos =================

    public record IngresoDto(Long id, String concepto, BigDecimal importe) {
        static IngresoDto de(Ingreso i) {
            return new IngresoDto(i.getId(), i.getConcepto(), i.getImporte());
        }
    }

    public record RepartoDto(BigDecimal ahorro, BigDecimal variables, BigDecimal fijos, BigDecimal ocio, boolean guardado) {
        static RepartoDto de(Reparto r, boolean guardado) {
            return new RepartoDto(r.getAhorro(), r.getVariables(), r.getFijos(), r.getOcio(), guardado);
        }
    }

    public record GastoDto(Long id, String fecha, String categoria, String descripcion, BigDecimal importe, boolean origenBanco) {
        static GastoDto de(Gasto g, boolean origenBanco) {
            return new GastoDto(g.getId(), g.getFecha().toString(), g.getCategoria(), g.getDescripcion(), g.getImporte(), origenBanco);
        }
    }

    public record MesDto(String mes, List<IngresoDto> ingresos, RepartoDto reparto, List<GastoDto> gastos,
                         BigDecimal ahorradoEnElMes, String mesAnteriorConDatos, List<String> categorias) {}

    @GetMapping("/mes/{mes}")
    @Transactional(readOnly = true)
    public MesDto mes(@PathVariable String mes, Principal p) {
        YearMonth ym = mes(mes);
        String yo = p.getName();
        List<IngresoDto> listaIngresos = ingresos.findByUsuarioAndMesOrderByIdAsc(yo, ym.toString())
                .stream().map(IngresoDto::de).toList();
        RepartoDto reparto = repartos.findByUsuarioAndMes(yo, ym.toString())
                .map(r -> RepartoDto.de(r, true))
                .orElseGet(() -> RepartoDto.de(new Reparto(yo, ym.toString()), false));
        List<Gasto> gastosMes = gastos.findByUsuarioAndFechaBetweenOrderByFechaDescIdDesc(yo, ym.atDay(1), ym.atEndOfMonth());
        Set<Long> idsImportados = movimientosBanco.findByUsuarioAndGastoIdIn(yo, gastosMes.stream().map(Gasto::getId).toList())
                .stream().map(MovimientoBanco::getGastoId).collect(java.util.stream.Collectors.toSet());
        List<GastoDto> listaGastos = gastosMes.stream().map(g -> GastoDto.de(g, idsImportados.contains(g.getId()))).toList();

        BigDecimal ahorrado = movimientos.findByUsuarioOrderByFechaDescIdDesc(yo).stream()
                .filter(m -> YearMonth.from(m.getFecha()).equals(ym))
                .map(MovimientoAhorro::getCantidad)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Si este mes esta vacio, ofrecer copiar el ultimo mes con datos
        String anterior = null;
        if (listaIngresos.isEmpty() && !reparto.guardado()) {
            anterior = ultimoMesConDatos(yo, ym.toString());
        }

        Set<String> categorias = new LinkedHashSet<>(List.of(
                "Comida", "Cenas", "Compra", "Farmacia", "Skincare", "Maquillaje", "Ropa", "Regalos", "Viaje", "Transporte", "Ocio", "Bizum"));
        gastos.findTop300ByUsuarioOrderByIdDesc(yo).forEach(g -> categorias.add(g.getCategoria()));

        return new MesDto(ym.toString(), listaIngresos, reparto, listaGastos, ahorrado, anterior, List.copyOf(categorias));
    }

    /** Lo apuntado de verdad en varios meses seguidos: ingresos (null si no hay ninguno) y gastos variables. */
    public record TotalMesDto(String mes, BigDecimal ingresos, BigDecimal gastos) {}

    @GetMapping("/totales")
    @Transactional(readOnly = true)
    public List<TotalMesDto> totales(@RequestParam String desde, @RequestParam(defaultValue = "12") int meses, Principal p) {
        if (meses < 1 || meses > 24) {
            throw error("Entre 1 y 24 meses");
        }
        String yo = p.getName();
        YearMonth inicio = mes(desde);
        List<TotalMesDto> lista = new java.util.ArrayList<>();
        for (int i = 0; i < meses; i++) {
            YearMonth ym = inicio.plusMonths(i);
            List<Ingreso> delMes = ingresos.findByUsuarioAndMesOrderByIdAsc(yo, ym.toString());
            BigDecimal entra = delMes.isEmpty() ? null
                    : delMes.stream().map(Ingreso::getImporte).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal gastado = gastos.findByUsuarioAndFechaBetweenOrderByFechaDescIdDesc(yo, ym.atDay(1), ym.atEndOfMonth())
                    .stream().map(Gasto::getImporte).reduce(BigDecimal.ZERO, BigDecimal::add);
            lista.add(new TotalMesDto(ym.toString(), entra, gastado));
        }
        return lista;
    }

    private String ultimoMesConDatos(String yo, String mes) {
        String a = repartos.findFirstByUsuarioAndMesLessThanOrderByMesDesc(yo, mes).map(Reparto::getMes).orElse(null);
        String b = ingresos.findFirstByUsuarioAndMesLessThanOrderByMesDesc(yo, mes).map(Ingreso::getMes).orElse(null);
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) >= 0 ? a : b;
    }

    /** Copia ingresos y reparto del ultimo mes con datos (como duplicar la hoja del Excel). */
    @PostMapping("/mes/{mes}/copiar")
    @Transactional
    public MesDto copiar(@PathVariable String mes, Principal p) {
        YearMonth ym = mes(mes);
        String yo = p.getName();
        String origen = ultimoMesConDatos(yo, ym.toString());
        if (origen == null) {
            throw error("No hay ningún mes anterior para copiar");
        }
        if (ingresos.findByUsuarioAndMesOrderByIdAsc(yo, ym.toString()).isEmpty()) {
            ingresos.findByUsuarioAndMesOrderByIdAsc(yo, origen)
                    .forEach(i -> ingresos.save(new Ingreso(yo, ym.toString(), i.getConcepto(), i.getImporte())));
        }
        if (repartos.findByUsuarioAndMes(yo, ym.toString()).isEmpty()) {
            repartos.findByUsuarioAndMes(yo, origen).ifPresent(r -> {
                Reparto nuevo = new Reparto(yo, ym.toString());
                nuevo.setAhorro(r.getAhorro());
                nuevo.setVariables(r.getVariables());
                nuevo.setFijos(r.getFijos());
                nuevo.setOcio(r.getOcio());
                repartos.save(nuevo);
            });
        }
        return mes(ym.toString(), p);
    }

    public record NuevoIngreso(String concepto, BigDecimal importe) {}

    @PostMapping("/mes/{mes}/ingresos")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public IngresoDto crearIngreso(@PathVariable String mes, @RequestBody NuevoIngreso d, Principal p) {
        String concepto = requerido(texto(d.concepto(), 60), "concepto");
        BigDecimal importe = importe(d.importe(), false);
        novedades.privada(p.getName(), "ANADIR", "AHORROS", "añadió el ingreso «" + concepto + "» (" + Novedades.euros(importe) + ")");
        return IngresoDto.de(ingresos.save(new Ingreso(p.getName(), mes(mes).toString(), concepto, importe)));
    }

    @DeleteMapping("/ingresos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void borrarIngreso(@PathVariable Long id, Principal p) {
        Ingreso i = ingresos.findByIdAndUsuario(id, p.getName()).orElseThrow(AhorrosApiController::noEncontrado);
        novedades.privada(p.getName(), "QUITAR", "AHORROS", "quitó el ingreso «" + i.getConcepto() + "» (" + Novedades.euros(i.getImporte()) + ")");
        ingresos.delete(i);
    }

    public record NuevoReparto(BigDecimal ahorro, BigDecimal variables, BigDecimal fijos, BigDecimal ocio) {}

    @PutMapping("/mes/{mes}/reparto")
    @Transactional
    public RepartoDto guardarReparto(@PathVariable String mes, @RequestBody NuevoReparto d, Principal p) {
        String m = mes(mes).toString();
        BigDecimal[] valores = {d.ahorro(), d.variables(), d.fijos(), d.ocio()};
        for (BigDecimal v : valores) {
            if (v == null || v.signum() < 0 || v.compareTo(CIEN) > 0 || !decimalesValidos(v)) {
                throw error("Cada porcentaje tiene que estar entre 0 y 100");
            }
        }
        Reparto r = repartos.findByUsuarioAndMes(p.getName(), m).orElseGet(() -> new Reparto(p.getName(), m));
        r.setAhorro(d.ahorro().setScale(2, RoundingMode.HALF_UP));
        r.setVariables(d.variables().setScale(2, RoundingMode.HALF_UP));
        r.setFijos(d.fijos().setScale(2, RoundingMode.HALF_UP));
        r.setOcio(d.ocio().setScale(2, RoundingMode.HALF_UP));
        return RepartoDto.de(repartos.save(r), true);
    }

    public record NuevoGasto(String fecha, String categoria, String descripcion, BigDecimal importe) {}

    @PostMapping("/gastos")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public GastoDto crearGasto(@RequestBody NuevoGasto d, Principal p) {
        Gasto g = new Gasto();
        g.setUsuario(p.getName());
        g.setFecha(fecha(d.fecha()));
        g.setCategoria(requerido(texto(d.categoria(), 30), "categoría"));
        g.setDescripcion(texto(d.descripcion(), 80));
        g.setImporte(importe(d.importe(), false));
        novedades.privada(p.getName(), "ANADIR", "AHORROS", "apuntó un gasto de " + Novedades.euros(g.getImporte()) + " en "
                + g.getCategoria() + (g.getDescripcion() == null ? "" : " («" + g.getDescripcion() + "»)"));
        return GastoDto.de(gastos.save(g), false);
    }

    @DeleteMapping("/gastos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void borrarGasto(@PathVariable Long id, Principal p) {
        Gasto g = gastos.findByIdAndUsuario(id, p.getName()).orElseThrow(AhorrosApiController::noEncontrado);
        novedades.privada(p.getName(), "QUITAR", "AHORROS", "quitó un gasto de " + Novedades.euros(g.getImporte()) + " en " + g.getCategoria());
        gastos.delete(g);
    }

    // ================= Suscripciones / gastos fijos =================

    public record SuscripcionDto(Long id, String nombre, BigDecimal precio, String frecuencia, String cobro,
                                 String siguienteCobro, boolean enUso, String nota, BigDecimal costeMensual) {
        static SuscripcionDto de(Suscripcion s) {
            return new SuscripcionDto(s.getId(), s.getNombre(), s.getPrecio(), s.getFrecuencia(), s.getCobro().toString(),
                    s.siguienteCobro(LocalDate.now()).toString(), s.isEnUso(), s.getNota(), s.costeMensual());
        }
    }

    public record NuevaSuscripcion(String nombre, BigDecimal precio, String frecuencia, String cobro, String nota) {}

    public record CambioSuscripcion(Boolean enUso) {}

    @GetMapping("/suscripciones")
    @Transactional(readOnly = true)
    public List<SuscripcionDto> suscripciones(Principal p) {
        return suscripciones.findByUsuarioOrderByNombreAsc(p.getName()).stream().map(SuscripcionDto::de).toList();
    }

    @PostMapping("/suscripciones")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public SuscripcionDto crearSuscripcion(@RequestBody NuevaSuscripcion d, Principal p) {
        String frecuencia = d.frecuencia() == null ? "MENSUAL" : d.frecuencia();
        if (!FRECUENCIAS.contains(frecuencia)) {
            throw error("Frecuencia no válida");
        }
        Suscripcion s = new Suscripcion();
        s.setUsuario(p.getName());
        s.setNombre(requerido(texto(d.nombre(), 40), "nombre"));
        s.setPrecio(importe(d.precio(), true));
        s.setFrecuencia(frecuencia);
        s.setCobro(fecha(d.cobro()));
        s.setNota(texto(d.nota(), 80));
        novedades.privada(p.getName(), "ANADIR", "AHORROS", "añadió la suscripción «" + s.getNombre() + "» (" + Novedades.euros(s.getPrecio()) + ")");
        return SuscripcionDto.de(suscripciones.save(s));
    }

    @PatchMapping("/suscripciones/{id}")
    @Transactional
    public SuscripcionDto cambiarSuscripcion(@PathVariable Long id, @RequestBody CambioSuscripcion d, Principal p) {
        Suscripcion s = suscripciones.findByIdAndUsuario(id, p.getName()).orElseThrow(AhorrosApiController::noEncontrado);
        if (d.enUso() != null) {
            s.setEnUso(d.enUso());
            novedades.privada(p.getName(), "CAMBIAR", "AHORROS", "marcó «" + s.getNombre() + "» como " + (d.enUso() ? "en uso" : "sin usar"));
        }
        return SuscripcionDto.de(s);
    }

    @DeleteMapping("/suscripciones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void borrarSuscripcion(@PathVariable Long id, Principal p) {
        Suscripcion s = suscripciones.findByIdAndUsuario(id, p.getName()).orElseThrow(AhorrosApiController::noEncontrado);
        novedades.privada(p.getName(), "QUITAR", "AHORROS", "quitó la suscripción «" + s.getNombre() + "»");
        suscripciones.delete(s);
    }

    // ================= Cuentas de ahorro =================

    public record MovimientoDto(Long id, String fecha, BigDecimal cantidad, String cuenta, String nota) {
        static MovimientoDto de(MovimientoAhorro m) {
            return new MovimientoDto(m.getId(), m.getFecha().toString(), m.getCantidad(), m.getCuenta(), m.getNota());
        }
    }

    public record NuevoMovimiento(String fecha, BigDecimal cantidad, String cuenta, String nota) {}

    @GetMapping("/movimientos")
    @Transactional(readOnly = true)
    public List<MovimientoDto> movimientos(Principal p) {
        return movimientos.findByUsuarioOrderByFechaDescIdDesc(p.getName()).stream().map(MovimientoDto::de).toList();
    }

    @PostMapping("/movimientos")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public MovimientoDto crearMovimiento(@RequestBody NuevoMovimiento d, Principal p) {
        if (d.cantidad() == null || d.cantidad().signum() == 0 || d.cantidad().abs().compareTo(MAXIMO) > 0
                || !decimalesValidos(d.cantidad())) {
            throw error("Escribe una cantidad (negativa si sacas dinero)");
        }
        MovimientoAhorro m = new MovimientoAhorro();
        m.setUsuario(p.getName());
        m.setFecha(fecha(d.fecha()));
        m.setCantidad(d.cantidad().setScale(2, RoundingMode.HALF_UP));
        m.setCuenta(requerido(texto(d.cuenta(), 40), "cuenta"));
        m.setNota(texto(d.nota(), 80));
        novedades.privada(p.getName(), m.getCantidad().signum() > 0 ? "ANADIR" : "QUITAR", "AHORROS",
                (m.getCantidad().signum() > 0 ? "metió " : "sacó ") + Novedades.euros(m.getCantidad().abs()) + " en «" + m.getCuenta() + "»");
        return MovimientoDto.de(movimientos.save(m));
    }

    @DeleteMapping("/movimientos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void borrarMovimiento(@PathVariable Long id, Principal p) {
        MovimientoAhorro m = movimientos.findByIdAndUsuario(id, p.getName()).orElseThrow(AhorrosApiController::noEncontrado);
        novedades.privada(p.getName(), "QUITAR", "AHORROS", "borró un movimiento de " + Novedades.euros(m.getCantidad()) + " de «" + m.getCuenta() + "»");
        movimientos.delete(m);
    }

    // ================= Objetivos en comun =================

    public record AportacionDto(Long id, String usuario, String fecha, BigDecimal cantidad) {}

    public record ObjetivoDto(Long id, String nombre, BigDecimal meta, String fechaLimite, String creadoPor,
                              BigDecimal aportado, List<AportacionDto> aportaciones) {
        static ObjetivoDto de(ObjetivoAhorro o) {
            List<AportacionDto> lista = o.getAportaciones().stream()
                    .map(a -> new AportacionDto(a.getId(), a.getUsuario(), a.getFecha().toString(), a.getCantidad()))
                    .toList();
            BigDecimal total = lista.stream().map(AportacionDto::cantidad).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new ObjetivoDto(o.getId(), o.getNombre(), o.getMeta(),
                    o.getFechaLimite() == null ? null : o.getFechaLimite().toString(), o.getCreadoPor(), total, lista);
        }
    }

    public record NuevoObjetivo(String nombre, BigDecimal meta, String fechaLimite) {}

    public record NuevaAportacion(BigDecimal cantidad, String fecha) {}

    @GetMapping("/objetivos")
    @Transactional(readOnly = true)
    public List<ObjetivoDto> objetivos() {
        return objetivos.findAllByOrderByIdAsc().stream().map(ObjetivoDto::de).toList();
    }

    @PostMapping("/objetivos")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ObjetivoDto crearObjetivo(@RequestBody NuevoObjetivo d, Principal p) {
        ObjetivoAhorro o = new ObjetivoAhorro();
        o.setNombre(requerido(texto(d.nombre(), 40), "nombre"));
        o.setMeta(importe(d.meta(), false));
        o.setFechaLimite(d.fechaLimite() == null || d.fechaLimite().isBlank() ? null : fecha(d.fechaLimite()));
        o.setCreadoPor(p.getName());
        novedades.compartida(p.getName(), "ANADIR", "AHORROS", "creó el objetivo juntos «" + o.getNombre() + "» (" + Novedades.euros(o.getMeta()) + ")");
        return ObjetivoDto.de(objetivos.save(o));
    }

    @PostMapping("/objetivos/{id}/aportaciones")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ObjetivoDto aportar(@PathVariable Long id, @RequestBody NuevaAportacion d, Principal p) {
        ObjetivoAhorro o = objetivos.findById(id).orElseThrow(AhorrosApiController::noEncontrado);
        if (d.cantidad() == null || d.cantidad().signum() == 0 || d.cantidad().abs().compareTo(MAXIMO) > 0
                || !decimalesValidos(d.cantidad())) {
            throw error("Escribe una cantidad");
        }
        Aportacion a = new Aportacion();
        a.setObjetivo(o);
        a.setUsuario(p.getName());
        a.setFecha(d.fecha() == null || d.fecha().isBlank() ? LocalDate.now() : fecha(d.fecha()));
        a.setCantidad(d.cantidad().setScale(2, RoundingMode.HALF_UP));
        o.getAportaciones().add(0, a);
        novedades.compartida(p.getName(), a.getCantidad().signum() > 0 ? "ANADIR" : "QUITAR", "AHORROS",
                (a.getCantidad().signum() > 0 ? "aportó " : "retiró ") + Novedades.euros(a.getCantidad().abs()) + " al objetivo «" + o.getNombre() + "»");
        return ObjetivoDto.de(objetivos.save(o));
    }

    @DeleteMapping("/objetivos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void borrarObjetivo(@PathVariable Long id, Principal p) {
        ObjetivoAhorro o = objetivos.findById(id).orElseThrow(AhorrosApiController::noEncontrado);
        novedades.compartida(p.getName(), "QUITAR", "AHORROS", "borró el objetivo juntos «" + o.getNombre() + "»");
        objetivos.delete(o);
    }

    // ================= Utilidades =================

    private static YearMonth mes(String valor) {
        try {
            return YearMonth.parse(valor);
        } catch (DateTimeParseException | NullPointerException e) {
            throw error("Mes no válido");
        }
    }

    private static LocalDate fecha(String valor) {
        try {
            return LocalDate.parse(requerido(valor, "fecha"));
        } catch (DateTimeParseException e) {
            throw error("Fecha no válida");
        }
    }

    private static BigDecimal importe(BigDecimal valor, boolean permitirCero) {
        if (valor == null || valor.signum() < 0 || (!permitirCero && valor.signum() == 0) || valor.compareTo(MAXIMO) > 0
                || !decimalesValidos(valor)) {
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
        String limpio = valor.trim();
        return limpio.length() > max ? limpio.substring(0, max) : limpio;
    }

    private static <T> T requerido(T valor, String campo) {
        if (valor == null) {
            throw error("Falta el campo: " + campo);
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
