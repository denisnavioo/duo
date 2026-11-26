package com.pareja.duo.ahorros;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

/**
 * Vista "Mi plan" de Ahorros: lo que tengo (saldos), lo que entra cada mes y como reparto
 * el dinero libre (lo que entra menos los gastos fijos) entre mis apartados.
 * Los gastos fijos son las mismas suscripciones de la vista "Mes a mes".
 * Todo es privado: cada uno solo ve y cambia lo suyo.
 */
@RestController
@RequestMapping("/api/ahorros/plan")
public class PlanDineroApiController {

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal MAXIMO = new BigDecimal("1000000");
    private static final int MAX_APARTADOS = 12;
    private static final int MAX_SALDOS = 20;

    /** Propuesta para empezar (no se guarda hasta que se toca algo). */
    private static final List<ApartadoDto> PROPUESTA = List.of(
            new ApartadoDto("Ahorro", new BigDecimal("30"), true),
            new ApartadoDto("Comida y casa", new BigDecimal("30"), false),
            new ApartadoDto("Ocio y planes", new BigDecimal("20"), false),
            new ApartadoDto("Caprichos", new BigDecimal("10"), false),
            new ApartadoDto("Imprevistos", new BigDecimal("10"), false));

    private static final int MAX_METAS = 10;

    private final PlanDineroRepository planes;
    private final SaldoCuentaRepository saldos;
    private final ApartadoRepository apartados;
    private final MetaAhorroRepository metas;
    private final Novedades novedades;

    public PlanDineroApiController(PlanDineroRepository planes, SaldoCuentaRepository saldos,
                                   ApartadoRepository apartados, MetaAhorroRepository metas, Novedades novedades) {
        this.planes = planes;
        this.saldos = saldos;
        this.apartados = apartados;
        this.metas = metas;
        this.novedades = novedades;
    }

    public record MetaDto(Long id, String nombre, BigDecimal cantidad, String fecha) {
        static MetaDto de(MetaAhorro m) {
            return new MetaDto(m.getId(), m.getNombre(), m.getCantidad(), m.getFecha().toString());
        }
    }

    public record SaldoDto(Long id, String nombre, BigDecimal cantidad, String actualizado) {
        static SaldoDto de(SaldoCuenta s) {
            return new SaldoDto(s.getId(), s.getNombre(), s.getCantidad(), s.getActualizado().toString());
        }
    }

    public record ApartadoDto(String nombre, BigDecimal porcentaje, boolean ahorro) {
        static ApartadoDto de(Apartado a) {
            return new ApartadoDto(a.getNombre(), a.getPorcentaje(), a.isAhorro());
        }
    }

    public record PlanDto(BigDecimal ingresoMensual, boolean sinIngresos, String vista, List<SaldoDto> saldos,
                          List<ApartadoDto> apartados, boolean apartadosGuardados, List<MetaDto> metas) {}

    @GetMapping
    @Transactional(readOnly = true)
    public PlanDto ver(Principal p) {
        String yo = p.getName();
        PlanDinero plan = planes.findByUsuario(yo).orElseGet(() -> new PlanDinero(yo));
        List<ApartadoDto> lista = apartados.findByUsuarioOrderByOrdenAsc(yo).stream().map(ApartadoDto::de).toList();
        boolean guardados = !lista.isEmpty();
        return new PlanDto(plan.getIngresoMensual(), Boolean.TRUE.equals(plan.getSinIngresos()), plan.getVista(),
                saldos.findByUsuarioOrderByIdAsc(yo).stream().map(SaldoDto::de).toList(),
                guardados ? lista : PROPUESTA, guardados,
                metas.findByUsuarioOrderByFechaAscIdAsc(yo).stream().map(MetaDto::de).toList());
    }

    // ================= Metas: tener X € para una fecha =================

    public record DatosMeta(String nombre, BigDecimal cantidad, String fecha) {}

    @PostMapping("/metas")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public MetaDto crearMeta(@RequestBody DatosMeta d, Principal p) {
        String yo = p.getName();
        if (metas.countByUsuario(yo) >= MAX_METAS) {
            throw error("Como mucho " + MAX_METAS + " metas");
        }
        MetaAhorro m = new MetaAhorro();
        m.setUsuario(yo);
        rellenarMeta(m, d, true);
        novedades.privada(yo, "ANADIR", "AHORROS", "se puso la meta «" + m.getNombre() + "» (" + Novedades.euros(m.getCantidad()) + ")");
        return MetaDto.de(metas.save(m));
    }

    @PutMapping("/metas/{id}")
    @Transactional
    public MetaDto cambiarMeta(@PathVariable Long id, @RequestBody DatosMeta d, Principal p) {
        MetaAhorro m = metas.findByIdAndUsuario(id, p.getName()).orElseThrow(PlanDineroApiController::noEncontrado);
        rellenarMeta(m, d, false);
        novedades.privada(p.getName(), "CAMBIAR", "AHORROS", "cambió la meta «" + m.getNombre() + "»");
        return MetaDto.de(m);
    }

    @DeleteMapping("/metas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void borrarMeta(@PathVariable Long id, Principal p) {
        MetaAhorro m = metas.findByIdAndUsuario(id, p.getName()).orElseThrow(PlanDineroApiController::noEncontrado);
        novedades.privada(p.getName(), "QUITAR", "AHORROS", "quitó la meta «" + m.getNombre() + "»");
        metas.delete(m);
    }

    /** Al crear todo es obligatorio; al cambiar, lo que no venga se queda como estaba. */
    private static void rellenarMeta(MetaAhorro m, DatosMeta d, boolean nueva) {
        String nombre = texto(d.nombre(), 40);
        if (nombre != null || nueva) {
            m.setNombre(requerido(nombre, "nombre"));
        }
        if (d.cantidad() != null || nueva) {
            BigDecimal cantidad = importe(requerido(d.cantidad(), "cantidad"), false);
            if (cantidad.signum() == 0) {
                throw error("La meta tiene que ser de más de 0 €");
            }
            m.setCantidad(cantidad);
        }
        if (d.fecha() != null || nueva) {
            LocalDate fecha;
            try {
                fecha = LocalDate.parse(requerido(d.fecha(), "fecha"));
            } catch (java.time.format.DateTimeParseException e) {
                throw error("Fecha no válida");
            }
            if (fecha.isBefore(LocalDate.now())) {
                throw error("La fecha de la meta ya ha pasado");
            }
            if (fecha.isAfter(LocalDate.now().plusYears(10))) {
                throw error("Como mucho, a 10 años vista");
            }
            m.setFecha(fecha);
        }
    }

    // ================= Ingreso mensual y vista =================

    public record CambioPlan(BigDecimal ingresoMensual, String vista, Boolean sinIngresos) {}

    @PutMapping
    @Transactional
    public PlanDto cambiar(@RequestBody CambioPlan d, Principal p) {
        String yo = p.getName();
        PlanDinero plan = planes.findByUsuario(yo).orElseGet(() -> new PlanDinero(yo));
        if (d.vista() != null) {
            if (!PlanDinero.VISTA_PLAN.equals(d.vista()) && !PlanDinero.VISTA_MES.equals(d.vista())) {
                throw error("Vista no válida");
            }
            plan.setVista(d.vista());
        }
        if (d.ingresoMensual() != null) {
            BigDecimal nuevo = importe(d.ingresoMensual(), false);
            if (nuevo.compareTo(plan.getIngresoMensual()) != 0) {
                novedades.privada(yo, "CAMBIAR", "AHORROS", "puso su ingreso mensual en " + Novedades.euros(nuevo));
            }
            plan.setIngresoMensual(nuevo);
        }
        if (d.sinIngresos() != null && d.sinIngresos() != Boolean.TRUE.equals(plan.getSinIngresos())) {
            plan.setSinIngresos(d.sinIngresos());
            novedades.privada(yo, "CAMBIAR", "AHORROS", d.sinIngresos()
                    ? "marcó que ahora no tiene ingresos" : "volvió a contar con su ingreso mensual de " + Novedades.euros(plan.getIngresoMensual()));
        }
        planes.save(plan);
        return ver(p);
    }

    // ================= Lo que tengo =================

    public record DatosSaldo(String nombre, BigDecimal cantidad) {}

    @PostMapping("/saldos")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public SaldoDto crearSaldo(@RequestBody DatosSaldo d, Principal p) {
        String yo = p.getName();
        if (saldos.countByUsuario(yo) >= MAX_SALDOS) {
            throw error("Como mucho " + MAX_SALDOS + " cuentas");
        }
        SaldoCuenta s = new SaldoCuenta();
        s.setUsuario(yo);
        s.setNombre(requerido(texto(d.nombre(), 40), "nombre"));
        s.setCantidad(importe(d.cantidad(), true));
        s.setActualizado(LocalDate.now());
        novedades.privada(yo, "ANADIR", "AHORROS", "añadió la cuenta «" + s.getNombre() + "» (" + Novedades.euros(s.getCantidad()) + ")");
        return SaldoDto.de(saldos.save(s));
    }

    @PutMapping("/saldos/{id}")
    @Transactional
    public SaldoDto cambiarSaldo(@PathVariable Long id, @RequestBody DatosSaldo d, Principal p) {
        SaldoCuenta s = saldos.findByIdAndUsuario(id, p.getName()).orElseThrow(PlanDineroApiController::noEncontrado);
        String nombre = texto(d.nombre(), 40);
        if (nombre != null) {
            s.setNombre(nombre);
        }
        if (d.cantidad() != null) {
            s.setCantidad(importe(d.cantidad(), true));
        }
        s.setActualizado(LocalDate.now());
        novedades.privada(p.getName(), "CAMBIAR", "AHORROS", "actualizó la cuenta «" + s.getNombre() + "» (" + Novedades.euros(s.getCantidad()) + ")");
        return SaldoDto.de(s);
    }

    @DeleteMapping("/saldos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void borrarSaldo(@PathVariable Long id, Principal p) {
        SaldoCuenta s = saldos.findByIdAndUsuario(id, p.getName()).orElseThrow(PlanDineroApiController::noEncontrado);
        novedades.privada(p.getName(), "QUITAR", "AHORROS", "quitó la cuenta «" + s.getNombre() + "»");
        saldos.delete(s);
    }

    // ================= Apartados =================

    /** Se guarda la lista entera de una vez (es lo que se edita en pantalla). */
    @PutMapping("/apartados")
    @Transactional
    public List<ApartadoDto> guardarApartados(@RequestBody List<ApartadoDto> lista, Principal p) {
        if (lista == null || lista.isEmpty()) {
            throw error("Pon al menos un apartado");
        }
        if (lista.size() > MAX_APARTADOS) {
            throw error("Como mucho " + MAX_APARTADOS + " apartados");
        }
        String yo = p.getName();
        List<Apartado> nuevos = new ArrayList<>();
        BigDecimal suma = BigDecimal.ZERO;
        for (int i = 0; i < lista.size(); i++) {
            ApartadoDto a = lista.get(i);
            if (a == null) {
                throw error("Apartado no válido");
            }
            BigDecimal pct = porcentaje(a.porcentaje());
            suma = suma.add(pct);
            nuevos.add(new Apartado(yo, requerido(texto(a.nombre(), 30), "nombre del apartado"), pct, a.ahorro(), i));
        }
        if (suma.compareTo(CIEN) > 0) {
            throw error("Los apartados suman más del 100 %");
        }
        apartados.deleteByUsuario(yo);
        apartados.flush();
        return apartados.saveAll(nuevos).stream().map(ApartadoDto::de).toList();
    }

    // ================= Utilidades =================

    private static BigDecimal porcentaje(BigDecimal v) {
        if (v == null || v.signum() < 0 || v.compareTo(CIEN) > 0 || !decimalesValidos(v)) {
            throw error("Porcentaje no válido");
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** permitirNegativo: un saldo puede estar en negativo (tarjeta de credito). */
    private static BigDecimal importe(BigDecimal v, boolean permitirNegativo) {
        if (v == null || (!permitirNegativo && v.signum() < 0) || v.abs().compareTo(MAXIMO) > 0 || !decimalesValidos(v)) {
            throw error("Escribe un importe válido");
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

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
