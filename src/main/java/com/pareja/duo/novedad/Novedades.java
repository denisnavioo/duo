package com.pareja.duo.novedad;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Apunta en el registro lo que cada uno añade, quita o cambia. */
@Service
public class Novedades {

    private static final Locale ES = Locale.forLanguageTag("es-ES");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEE d MMM", ES);
    private static final String[] DIAS = {"", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"};

    private final NovedadRepository repo;

    public Novedades(NovedadRepository repo) {
        this.repo = repo;
    }

    /** Se apunta dentro de la misma transaccion: si la accion falla, no queda registro. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void compartida(String usuario, String accion, String seccion, String texto) {
        guardar(usuario, accion, seccion, texto, false);
    }

    /** Solo la vera quien lo hizo (ej. sus ingresos o gastos). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void privada(String usuario, String accion, String seccion, String texto) {
        guardar(usuario, accion, seccion, texto, true);
    }

    private void guardar(String usuario, String accion, String seccion, String texto, boolean privado) {
        Novedad n = new Novedad();
        n.setUsuario(usuario);
        n.setAccion(accion);
        n.setSeccion(seccion);
        n.setTexto(texto.length() > 200 ? texto.substring(0, 199) + "…" : texto);
        n.setPrivado(privado);
        repo.save(n);
    }

    // ---------- ayudas para escribir las frases ----------

    public static String dia(LocalDate fecha) {
        return fecha.format(DIA).replace(".", "");
    }

    public static String hora(LocalTime hora) {
        return hora.toString();
    }

    public static String diaSemana(int dia) {
        return dia >= 1 && dia <= 7 ? DIAS[dia] : "";
    }

    public static String euros(java.math.BigDecimal cantidad) {
        return String.format(ES, "%,.2f €", cantidad);
    }
}
