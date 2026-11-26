package com.pareja.duo.ahorros;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.pareja.duo.ahorros.EnableBankingCliente.BancoApiException;
import com.pareja.duo.novedad.Novedades;

@Service
public class ServicioBanco {
    private static final ZoneId ZONA_DUO = ZoneId.of("Europe/Madrid");
    public record Estado(boolean disponible, boolean conectado, String banco, String estado,
                         String validoHasta, String ultimaSincronizacion, String ultimoError,
                         List<CuentaEstadoDto> cuentas, boolean permitido) {}
    public record CuentaDto(String uid, String nombre) {}
    public record CuentaEstadoDto(String nombre) {}
    public record ReglaDto(Long id, String contiene, String accion, String categoria) {}
    public record RevisarDto(Long id, String fecha, String concepto, BigDecimal importe, String categoria) {}
    public record AltaRegla(String contiene, String accion, String categoria) {}
    public record Revision(String accion, String categoria, boolean crearRegla, String contiene) {}

    private final BancoCliente banco;
    private final ConexionBancoRepository conexiones;
    private final MovimientoBancoRepository movimientos;
    private final ReglaCategoriaRepository reglas;
    private final GastoRepository gastos;
    private final IngresoRepository ingresos;
    private final SaldoCuentaRepository saldos;
    private final Novedades novedades;
    private final JsonMapper json;
    private final String redirectUrl;
    private final String nombreBanco;
    private final java.util.Set<String> usuariosPermitidos;

    public ServicioBanco(BancoCliente banco, ConexionBancoRepository conexiones,
                         MovimientoBancoRepository movimientos, ReglaCategoriaRepository reglas,
                         GastoRepository gastos, IngresoRepository ingresos, SaldoCuentaRepository saldos,
                         Novedades novedades, JsonMapper json,
                         @Value("${duo.banco.redirect-url:http://localhost:8080/ahorros/banco/vuelta}") String redirectUrl,
                         @Value("${duo.banco.usuarios:persona-a,persona-b}") String usuariosPermitidos,
                         @Value("${duo.banco.nombre:}") String nombreBanco) {
        this.banco=banco; this.conexiones=conexiones; this.movimientos=movimientos;
        this.reglas=reglas; this.gastos=gastos; this.ingresos=ingresos; this.saldos=saldos;
        this.novedades=novedades; this.json=json; this.redirectUrl=redirectUrl;
        this.nombreBanco = nombreBanco.trim().toLowerCase(Locale.ROOT);
        this.usuariosPermitidos = java.util.Arrays.stream(usuariosPermitidos.split(","))
                .map(String::trim).filter(u -> !u.isEmpty()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** De momento la conexion con el banco solo esta abierta a algunas cuentas (duo.banco.usuarios). */
    public boolean permitido(String usuario) { return usuario != null && usuariosPermitidos.contains(usuario); }

    public boolean disponible() { return banco.disponible(); }
    public String redirectUrl() { return redirectUrl; }

    @Transactional(readOnly = true)
    public Estado estado(String usuario) {
        var c = conexiones.findByUsuario(usuario).orElse(null);
        List<CuentaEstadoDto> cuentas = c == null ? List.of() : cuentas(c.getCuentasJson()).stream().map(x -> new CuentaEstadoDto(x.nombre())).toList();
        return new Estado(disponible(), c != null && "CONECTADO".equals(c.getEstado()),
                c == null ? null : c.getBanco(), c == null ? "NO_CONECTADO" : c.getEstado(),
                c == null || c.getValidoHasta() == null ? null : c.getValidoHasta().toString(),
                c == null || c.getUltimaSincronizacion() == null ? null : c.getUltimaSincronizacion().toString(),
                c == null ? null : c.getUltimoError(), cuentas, permitido(usuario));
    }

    public String iniciar(String usuario, String state) {
        if (!disponible()) throw new IllegalStateException("La conexión bancaria no está configurada");
        if (nombreBanco.isEmpty()) throw new IllegalStateException("Falta indicar tu banco en DUO_BANCO_NOMBRE");
        JsonNode instituciones = banco.bancosEspaña().path("aspsps");
        String nombre = null;
        int maxSegundos = 180 * 24 * 60 * 60;
        for (JsonNode item : instituciones) {
            String candidato = item.path("name").asText("");
            if (candidato.toLowerCase(Locale.ROOT).contains(nombreBanco)) {
                nombre = candidato;
                int max = item.path("maximum_consent_validity").asInt(maxSegundos);
                if (max > 0) maxSegundos = Math.min(maxSegundos, max);
                break;
            }
        }
        if (nombre == null) throw new IllegalStateException("Enable Banking no ofrece ese banco en España en este momento");
        OffsetDateTime hasta = OffsetDateTime.now(ZONA_DUO).plusSeconds(maxSegundos);
        JsonNode respuesta = banco.iniciarAutorizacion(nombre, state, redirectUrl, hasta);
        String url = respuesta.path("url").asText("");
        if (!url.startsWith("https://")) throw new IllegalStateException("El proveedor no devolvió una dirección de autorización válida");
        return url;
    }

    @Transactional
    public void completar(String usuario, String code) { completar(usuario, code, null); }

    @Transactional
    public void completar(String usuario, String code, BancoCliente.Psu psu) {
        JsonNode sesion = banco.crearSesion(code);
        String sessionId = sesion.path("session_id").asText("");
        if (sessionId.isBlank()) throw new IllegalStateException("Enable Banking no devolvió una sesión válida");
        JsonNode lista = sesion.path("accounts");
        List<CuentaDto> cuentas = new ArrayList<>();
        for (JsonNode c : lista) {
            String uid = c.isTextual() ? c.asText() : c.path("uid").asText(c.path("account_id").asText(""));
            if (!uid.isBlank()) cuentas.add(new CuentaDto(uid, etiquetaCuenta(c)));
        }
        if (cuentas.isEmpty()) throw new IllegalStateException("No se recibieron cuentas autorizadas; en modo restringido vincula esta cuenta en el panel de Enable Banking");
        ConexionBanco conexion = conexiones.findByUsuario(usuario).orElseGet(ConexionBanco::new);
        if (conexion.getSessionId() != null && !conexion.getSessionId().equals(sessionId)) {
            try { banco.cerrarSesion(conexion.getSessionId()); } catch (RuntimeException ignored) { /* La nueva autorización sigue siendo válida */ }
        }
        conexion.setUsuario(usuario); conexion.setBanco(sesion.path("aspsp").path("name").asText("Banco"));
        conexion.setSessionId(sessionId); conexion.setCuentasJson(serializar(cuentas));
        conexion.setValidoHasta(parseFecha(sesion.path("access").path("valid_until").asText(null)));
        conexion.setEstado("CONECTADO"); conexion.setUltimoError(null);
        conexiones.save(conexion);
        novedades.privada(usuario, "ANADIR", "AHORROS", "conectó su cuenta bancaria de forma segura");
        try { sincronizar(usuario, psu); } catch (RuntimeException ignored) { /* La autorización queda guardada para reintentar */ }
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public int sincronizar(String usuario) { return sincronizar(usuario, null); }

    /** Con psu (el usuario esta delante) no cuenta para el limite de 4 consultas automaticas al dia. */
    @Transactional(noRollbackFor = RuntimeException.class)
    public synchronized int sincronizar(String usuario, BancoCliente.Psu psu) {
        ConexionBanco c = conexiones.findByUsuario(usuario).orElseThrow(() -> new IllegalStateException("Primero conecta tu cuenta bancaria"));
        if (!"CONECTADO".equals(c.getEstado())) throw new IllegalStateException("La conexión bancaria necesita renovarse");
        try {
            List<CuentaDto> cuentas = cuentas(c.getCuentasJson());
            LocalDate desde = c.getUltimaSincronizacion() == null ? LocalDate.now(ZONA_DUO).minusDays(90)
                    : c.getUltimaSincronizacion().toLocalDate().minusDays(5);
            int nuevos = 0;
            for (CuentaDto cuenta : cuentas) {
                String continuation = null;
                int paginas = 0;
                do {
                    JsonNode pagina = banco.movimientos(cuenta.uid(), desde, continuation, psu);
                    for (JsonNode tx : pagina.path("transactions")) if (importar(usuario, cuenta.uid(), tx)) nuevos++;
                    continuation = pagina.path("continuation_key").isMissingNode() || pagina.path("continuation_key").isNull()
                            ? null : pagina.path("continuation_key").asText(null);
                    if (++paginas > 100) throw new IllegalStateException("La paginación del banco excedió el límite esperado");
                } while (continuation != null && !continuation.isBlank());
                actualizarSaldo(usuario, cuenta, banco.saldos(cuenta.uid(), psu));
            }
            c.setUltimaSincronizacion(OffsetDateTime.now(ZONA_DUO)); c.setUltimoError(null); conexiones.save(c);
            if (nuevos > 0) novedades.privada(usuario, "ANADIR", "AHORROS", "sincronizó " + nuevos + " movimientos bancarios");
            return nuevos;
        } catch (RuntimeException e) {
            c.setUltimoError(e instanceof BancoApiException api && api.statusCode() == 429 ? LIMITE_DIARIO : mensajeSeguro(e));
            if (e instanceof BancoApiException api && List.of(401, 403, 410).contains(api.statusCode())) c.setEstado("REVISAR");
            conexiones.save(c);
            throw e;
        }
    }

    static final String LIMITE_DIARIO = "El banco ya no deja más consultas automáticas por hoy (máximo 4 al día). "
            + "Pulsa «Sincronizar ahora» para actualizar al momento.";

    /**
     * Sin el usuario delante el banco solo deja 4 consultas al dia por autorizacion, y cada sincronizacion
     * hace al menos dos (movimientos y saldo): por eso solo dos veces al dia. Al pulsar "Sincronizar ahora"
     * se actualiza al momento sin gastar de este cupo.
     */
    @Scheduled(cron = "0 20 7,19 * * *", zone = "Europe/Madrid")
    @Transactional(noRollbackFor = RuntimeException.class) // sincronizar() se llama desde dentro: su transaccion no se aplicaria
    public void sincronizacionAutomatica() {
        conexiones.findByEstado("CONECTADO").forEach(c -> {
            try { sincronizar(c.getUsuario()); }
            catch (RuntimeException ex) { /* El estado y el mensaje seguro quedan en la conexión. */ }
        });
    }

    @Transactional
    public void desconectar(String usuario) {
        ConexionBanco c = conexiones.findByUsuario(usuario).orElse(null);
        if (c == null) return;
        try { banco.cerrarSesion(c.getSessionId()); } catch (RuntimeException ignored) { /* revocar local sigue siendo posible */ }
        conexiones.delete(c);
        novedades.privada(usuario, "QUITAR", "AHORROS", "desconectó su cuenta bancaria");
    }

    @Transactional(readOnly = true)
    public List<ReglaDto> reglas(String usuario) { return reglas.findByUsuarioOrderByIdAsc(usuario).stream().map(ServicioBanco::dto).toList(); }

    @Transactional
    public ReglaDto crearRegla(String usuario, AltaRegla datos) {
        String coincide = texto(datos.contiene(), 80);
        String accion = validarAccion(datos.accion());
        String categoria = "CATEGORIA".equals(accion) ? requerido(texto(datos.categoria(), 30), "categoría") : null;
        ReglaCategoria r = new ReglaCategoria(); r.setUsuario(usuario); r.setContiene(coincide); r.setAccion(accion); r.setCategoria(categoria);
        novedades.privada(usuario, "ANADIR", "AHORROS", "añadió una regla para movimientos del banco");
        return dto(reglas.save(r));
    }

    @Transactional
    public ReglaDto actualizarRegla(String usuario, Long id, AltaRegla datos) {
        ReglaCategoria r = reglas.findByIdAndUsuario(id, usuario).orElseThrow(() -> new IllegalArgumentException("Regla no encontrada"));
        r.setContiene(texto(datos.contiene(), 80)); r.setAccion(validarAccion(datos.accion()));
        r.setCategoria("CATEGORIA".equals(r.getAccion()) ? requerido(texto(datos.categoria(), 30), "categoría") : null);
        novedades.privada(usuario, "CAMBIAR", "AHORROS", "cambió una regla de movimientos del banco");
        return dto(reglas.save(r));
    }

    @Transactional
    public void borrarRegla(String usuario, Long id) {
        ReglaCategoria r = reglas.findByIdAndUsuario(id, usuario).orElseThrow(() -> new IllegalArgumentException("Regla no encontrada"));
        reglas.delete(r); novedades.privada(usuario, "QUITAR", "AHORROS", "quitó una regla de movimientos del banco");
    }

    @Transactional(readOnly = true)
    public List<RevisarDto> porRevisar(String usuario) {
        return movimientos.findTop100ByUsuarioAndEstadoOrderByFechaDescIdDesc(usuario, "PENDIENTE").stream()
                .map(m -> new RevisarDto(m.getId(), m.getFecha().toString(), m.getConcepto(), m.getImporte(), "Sin categoría")).toList();
    }

    @Transactional
    public void revisar(String usuario, Long id, Revision revision) {
        MovimientoBanco m = movimientos.findById(id).filter(x -> x.getUsuario().equals(usuario))
                .orElseThrow(() -> new IllegalArgumentException("Movimiento no encontrado"));
        if ("IGNORAR".equals(revision.accion())) {
            m.setEstado("IGNORADO");
        } else {
            String categoria = "FIJO".equals(revision.accion()) ? "Gastos fijos" : texto(revision.categoria(), 30);
            if (!"CATEGORIA".equals(revision.accion()) && !"FIJO".equals(revision.accion())) throw new IllegalArgumentException("Acción no válida");
            Gasto gasto = m.getGastoId() == null ? null : gastos.findByIdAndUsuario(m.getGastoId(), usuario).orElse(null);
            if (gasto != null) { gasto.setCategoria(requerido(categoria, "categoría")); gastos.save(gasto); }
            m.setEstado("IMPORTADO");
        }
        movimientos.save(m);
        if (revision.crearRegla() && revision.contiene() != null && !revision.contiene().isBlank())
            crearRegla(usuario, new AltaRegla(revision.contiene(), revision.accion(), revision.categoria()));
    }

    private boolean importar(String usuario, String cuentaUid, JsonNode tx) {
        String referencia = tx.path("entry_reference").asText("");
        String txId = tx.path("transaction_id").asText("");
        LocalDate fecha = fechaMovimiento(tx);
        if (!"EUR".equalsIgnoreCase(tx.path("transaction_amount").path("currency").asText("EUR"))) return false;
        BigDecimal importe = new BigDecimal(tx.path("transaction_amount").path("amount").asText("0")).abs().setScale(2, RoundingMode.HALF_UP);
        if (importe.signum() == 0 || fecha == null) return false;
        String indicador = tx.path("credit_debit_indicator").asText("");
        boolean ingreso = "CRDT".equalsIgnoreCase(indicador);
        String concepto = concepto(tx);
        String estable = referencia.isBlank() ? cuentaUid + "|" + txId + "|" + fecha + "|" + importe.toPlainString() + "|" + concepto : cuentaUid + "|" + referencia;
        String huella = sha256(estable);
        if (movimientos.findByUsuarioAndHuella(usuario, huella).isPresent()) return false;
        MovimientoBanco m = new MovimientoBanco(); m.setUsuario(usuario); m.setHuella(huella);
        m.setIdTransaccion(referencia.isBlank() ? txId.isBlank() ? huella : txId : referencia);
        m.setCuentaUid(cuentaUid); m.setFecha(fecha); m.setImporte(importe); m.setConcepto(concepto); m.setEntrada(ingreso);
        ReglaCategoria regla = reglas.findByUsuarioOrderByIdAsc(usuario).stream()
                .filter(r -> concepto.toLowerCase(Locale.ROOT).contains(r.getContiene().toLowerCase(Locale.ROOT))).findFirst().orElse(null);
        if (regla != null && "IGNORAR".equals(regla.getAccion())) {
            m.setEstado("IGNORADO"); movimientos.save(m); return true;
        }
        if (ingreso) {
            Ingreso i = new Ingreso(usuario, YearMonth.from(fecha).toString(), concepto.substring(0, Math.min(60, concepto.length())), importe);
            i = ingresos.save(i); m.setIngresoId(i.getId()); m.setEstado("IMPORTADO");
        } else {
            Gasto g = new Gasto(); g.setUsuario(usuario); g.setFecha(fecha);
            g.setCategoria(regla != null && "CATEGORIA".equals(regla.getAccion()) ? regla.getCategoria()
                    : regla != null && "FIJO".equals(regla.getAccion()) ? "Gastos fijos" : "Sin categoría");
            g.setDescripcion(concepto.substring(0, Math.min(80, concepto.length()))); g.setImporte(importe);
            g = gastos.save(g); m.setGastoId(g.getId());
            m.setEstado(regla == null ? "PENDIENTE" : "IMPORTADO");
        }
        movimientos.save(m); return true;
    }

    private void actualizarSaldo(String usuario, CuentaDto cuenta, JsonNode respuesta) {
        JsonNode lista = respuesta.path("balances");
        JsonNode elegido = null;
        for (JsonNode b : lista) if ("CLAV".equals(b.path("balance_type").asText())) { elegido=b; break; }
        if (elegido == null && lista.size() > 0) elegido=lista.get(0);
        if (elegido == null) return;
        if (!"EUR".equalsIgnoreCase(elegido.path("balance_amount").path("currency").asText("EUR"))) return;
        BigDecimal cantidad = new BigDecimal(elegido.path("balance_amount").path("amount").asText("0")).setScale(2, RoundingMode.HALF_UP);
        SaldoCuenta saldo = saldos.findByUsuarioAndBancoCuentaUid(usuario, cuenta.uid()).orElseGet(SaldoCuenta::new);
        boolean nuevo = saldo.getId() == null;
        saldo.setUsuario(usuario); saldo.setBancoCuentaUid(cuenta.uid());
        if (nuevo) saldo.setNombre(cuenta.nombre());
        saldo.setCantidad(cantidad); saldo.setActualizado(LocalDate.now(ZONA_DUO)); saldos.save(saldo);
    }

    private List<CuentaDto> cuentas(String bruto) {
        try { var n=json.readTree(bruto); List<CuentaDto> r=new ArrayList<>(); n.forEach(x -> r.add(new CuentaDto(x.path("uid").asText(), x.path("nombre").asText("Cuenta bancaria")))); return r; }
        catch(Exception e) { return List.of(); }
    }
    private String serializar(List<CuentaDto> lista) { try { return json.writeValueAsString(lista); } catch(Exception e) { throw new IllegalStateException("No se pudieron guardar las cuentas autorizadas"); } }
    private static String etiquetaCuenta(JsonNode cuenta) { String nombre=cuenta.path("name").asText("").trim(); return nombre.isBlank() ? "Cuenta bancaria" : nombre.substring(0, Math.min(40,nombre.length())); }
    private static LocalDate fechaMovimiento(JsonNode tx) { for(String key:List.of("booking_date","value_date","transaction_date")){try {String v=tx.path(key).asText(""); if(!v.isBlank())return LocalDate.parse(v);}catch(Exception ignored){}} return null; }
    private static String concepto(JsonNode tx) {
        JsonNode info=tx.path("remittance_information"); String s="";
        if(info.isArray()) { List<String> partes=new ArrayList<>(); info.forEach(x->{if(x.isTextual()&&!x.asText().isBlank())partes.add(x.asText().trim());}); s=String.join(" · ",partes); }
        else if(info.isTextual()) s=info.asText().trim();
        if(s.isBlank()) s=tx.path("creditor").path("name").asText("");
        if(s.isBlank()) s=tx.path("debtor").path("name").asText("");
        if(s.isBlank()) s="Movimiento bancario";
        s=s.replaceAll("[\\p{Cntrl}]", " ").trim(); return s.substring(0,Math.min(160,s.length()));
    }
    private static OffsetDateTime parseFecha(String valor) { if(valor==null||valor.isBlank())return null; try{return OffsetDateTime.parse(valor);}catch(Exception e){try{return java.time.LocalDateTime.parse(valor).atZone(ZONA_DUO).toOffsetDateTime();}catch(Exception ignored){return null;}} }
    private static String sha256(String valor) { try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);} }
    private static String mensajeSeguro(RuntimeException e) { String m=e.getMessage(); if(m==null||m.isBlank())return "No se pudo sincronizar. Prueba más tarde o vuelve a conectar el banco."; return m.length()>180?m.substring(0,180):m; }
    private static ReglaDto dto(ReglaCategoria r) { return new ReglaDto(r.getId(),r.getContiene(),r.getAccion(),r.getCategoria()); }
    private static String validarAccion(String a) { if(!List.of("CATEGORIA","FIJO","IGNORAR").contains(a))throw new IllegalArgumentException("Acción de regla no válida"); return a; }
    private static String texto(String s,int max) { if(s==null||s.isBlank())throw new IllegalArgumentException("Falta un texto obligatorio"); s=s.trim(); return s.substring(0,Math.min(max,s.length())); }
    private static String requerido(String s,String campo) { if(s==null||s.isBlank())throw new IllegalArgumentException("Falta la "+campo); return s; }
}
