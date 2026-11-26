package com.pareja.duo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pareja.duo.actividad.TipoPlan;
import com.pareja.duo.actividad.TipoPlanRepository;
import com.pareja.duo.usuario.Usuario;
import com.pareja.duo.usuario.UsuarioRepository;

/**
 * Al arrancar crea, si todavia no existen:
 * - las dos personas de Duo ("persona-a", azul, y "persona-b", rosa), con el nombre
 *   que diga la configuracion (duo.persona-a.nombre / duo.persona-b.nombre). Luego cada
 *   uno puede cambiar su nombre desde Mi perfil.
 * - los tipos de plan iniciales del desplegable.
 */
@Component
public class DatosIniciales implements ApplicationRunner {

    public static final String PERSONA_A = "persona-a";
    public static final String PERSONA_B = "persona-b";

    private final UsuarioRepository usuarios;
    private final TipoPlanRepository tipos;
    private final String nombreA;
    private final String nombreB;

    public DatosIniciales(UsuarioRepository usuarios, TipoPlanRepository tipos,
                          @Value("${duo.persona-a.nombre:Ana}") String nombreA,
                          @Value("${duo.persona-b.nombre:Leo}") String nombreB) {
        this.usuarios = usuarios;
        this.tipos = tipos;
        this.nombreA = nombreA;
        this.nombreB = nombreB;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        crearSiNoExiste(PERSONA_A, nombreA);
        crearSiNoExiste(PERSONA_B, nombreB);

        if (tipos.count() == 0) {
            String[][] base = {
                {"Cena o comida", "cuenco"}, {"Cine o series", "cine"}, {"Escapada", "pin"},
                {"Paseo o playa", "ola"}, {"Deporte", "pesa"}, {"Cuidado personal", "unas"},
                {"Clase o estudio", "libro"}, {"Trabajo", "maletin"}, {"Médico", "reloj"},
                {"Celebración", "regalo"}
            };
            for (int i = 0; i < base.length; i++) {
                tipos.save(new TipoPlan(base[i][0], base[i][1], i * 10, false));
            }
            tipos.save(new TipoPlan("Otro", "destello", 999, false));
        }
    }

    private void crearSiNoExiste(String username, String nombre) {
        if (!usuarios.existsByUsername(username)) {
            String limpio = nombre == null || nombre.isBlank() ? username : nombre.trim();
            usuarios.save(new Usuario(username, limpio.length() > 30 ? limpio.substring(0, 30) : limpio));
        }
    }
}
