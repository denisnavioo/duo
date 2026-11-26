package com.pareja.duo.aviso;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pareja.duo.actividad.Actividad;
import com.pareja.duo.actividad.ActividadRepository;
import com.pareja.duo.usuario.Usuario;
import com.pareja.duo.usuario.UsuarioRepository;

/** Cada tarde avisa en el movil de los planes del dia siguiente. */
@Service
public class RecordatorioPlanes {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioPlanes.class);
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final int MAX_LINEAS = 5;

    private final ActividadRepository actividades;
    private final UsuarioRepository usuarios;
    private final EnviadorPush enviador;

    public RecordatorioPlanes(ActividadRepository actividades, UsuarioRepository usuarios, EnviadorPush enviador) {
        this.actividades = actividades;
        this.usuarios = usuarios;
        this.enviador = enviador;
    }

    @Scheduled(cron = "${duo.avisos.cron:0 0 20 * * *}", zone = "Europe/Madrid")
    public void avisarDeManana() {
        Map<String, EnviadorPush.Aviso> avisos = avisosPara(LocalDate.now(MADRID).plusDays(1));
        avisos.forEach((usuario, aviso) -> {
            int n = enviador.enviar(usuario, aviso);
            log.info("Recordatorio de mañana para {}: {} movil(es)", usuario, n);
        });
    }

    /** Que aviso le toca a cada uno para ese dia: sus planes y los de los dos (los rechazados no). */
    @Transactional(readOnly = true)
    public Map<String, EnviadorPush.Aviso> avisosPara(LocalDate dia) {
        List<Usuario> todos = usuarios.findAll();
        List<Actividad> planes = actividades.findByFechaOrderByHoraAsc(dia).stream()
                .filter(a -> !Actividad.RECHAZADA.equals(a.estadoEfectivo()))
                .toList();
        Map<String, EnviadorPush.Aviso> avisos = new LinkedHashMap<>();
        for (Usuario u : todos) {
            List<Actividad> suyos = planes.stream()
                    .filter(a -> a.getQuien().equals(u.getUsername()) || a.getQuien().equals("juntos"))
                    .toList();
            if (suyos.isEmpty()) {
                continue;
            }
            String otro = todos.stream().filter(x -> !x.getUsername().equals(u.getUsername()))
                    .map(Usuario::getNombre).findFirst().orElse("los dos");
            avisos.put(u.getUsername(), aviso(suyos, otro));
        }
        return avisos;
    }

    private static EnviadorPush.Aviso aviso(List<Actividad> planes, String otro) {
        String titulo = planes.size() == 1 ? "Mañana: " + planes.get(0).getTitulo()
                : "Mañana tienes " + planes.size() + " planes";
        List<String> lineas = new ArrayList<>();
        for (Actividad a : planes.subList(0, Math.min(MAX_LINEAS, planes.size()))) {
            StringBuilder l = new StringBuilder(a.getHora().toString());
            l.append(" · ").append(a.getTitulo());
            if (a.getLugar() != null && !a.getLugar().isBlank()) {
                l.append(" · ").append(a.getLugar());
            }
            if ("juntos".equals(a.getQuien())) {
                l.append(" · con ").append(otro);
            }
            if (Actividad.PENDIENTE.equals(a.estadoEfectivo())) {
                l.append(" (sin aceptar)");
            }
            lineas.add(l.toString());
        }
        if (planes.size() > MAX_LINEAS) {
            lineas.add("y " + (planes.size() - MAX_LINEAS) + " más");
        }
        String url = planes.size() == 1 ? "/calendario?plan=" + planes.get(0).getId() : "/calendario";
        return new EnviadorPush.Aviso(titulo, String.join("\n", lineas), url);
    }
}
