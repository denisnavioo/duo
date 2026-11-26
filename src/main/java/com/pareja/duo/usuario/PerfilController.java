package com.pareja.duo.usuario;

import java.io.IOException;
import java.security.Principal;
import java.time.Duration;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.pareja.duo.novedad.Novedades;

/** Perfil de cada uno: foto y nombre visible. */
@RestController
public class PerfilController {

    private static final int MAX_FOTO = 1_048_576; // 1 MB

    private final UsuarioRepository usuarios;
    private final Novedades novedades;

    public PerfilController(UsuarioRepository usuarios, Novedades novedades) {
        this.usuarios = usuarios;
        this.novedades = novedades;
    }

    public record PerfilDto(String username, String nombre, String inicial, boolean tieneFoto, Long fotoVersion) {
        static PerfilDto de(Usuario u) {
            return new PerfilDto(u.getUsername(), u.getNombre(), u.getInicial(), u.isTieneFoto(), u.getFotoVersion());
        }
    }

    public record CambioNombre(String nombre) {}

    /** Foto de perfil de cualquiera de los dos (solo para usuarios con sesion iniciada). */
    @GetMapping("/usuarios/{username}/foto")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> foto(@PathVariable String username) {
        Usuario u = usuarios.findByUsername(username).filter(Usuario::isTieneFoto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(u.getFotoTipo()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(u.getFoto());
    }

    @GetMapping("/api/perfil")
    @Transactional(readOnly = true)
    public PerfilDto perfil(Principal p) {
        return PerfilDto.de(yo(p));
    }

    @PostMapping("/api/perfil/foto")
    @Transactional
    public PerfilDto subirFoto(@RequestParam("foto") MultipartFile archivo, Principal p) throws IOException {
        if (archivo.isEmpty() || archivo.getSize() > MAX_FOTO) {
            throw error("La foto tiene que ocupar menos de 1 MB");
        }
        byte[] datos = archivo.getBytes();
        String tipo = tipoDeImagen(datos);
        if (tipo == null) {
            throw error("Formato no válido: usa una imagen JPG, PNG o WebP");
        }
        Usuario u = yo(p);
        u.setFoto(datos);
        u.setFotoTipo(tipo);
        u.setFotoVersion(System.currentTimeMillis());
        novedades.compartida(u.getUsername(), "CAMBIAR", "PERFIL", "cambió su foto de perfil");
        return PerfilDto.de(u);
    }

    @DeleteMapping("/api/perfil/foto")
    @Transactional
    public PerfilDto quitarFoto(Principal p) {
        Usuario u = yo(p);
        u.setFoto(null);
        u.setFotoTipo(null);
        u.setFotoVersion(null);
        novedades.compartida(u.getUsername(), "QUITAR", "PERFIL", "quitó su foto de perfil");
        return PerfilDto.de(u);
    }

    @PutMapping("/api/perfil")
    @Transactional
    public PerfilDto cambiarNombre(@RequestBody CambioNombre d, Principal p) {
        String nombre = d.nombre() == null ? "" : d.nombre().trim();
        if (nombre.isEmpty() || nombre.length() > 30) {
            throw error("El nombre tiene que tener entre 1 y 30 letras");
        }
        Usuario u = yo(p);
        novedades.compartida(u.getUsername(), "CAMBIAR", "PERFIL", "cambió su nombre de «" + u.getNombre() + "» a «" + nombre + "»");
        u.setNombre(nombre);
        return PerfilDto.de(u);
    }

    // ---------- utilidades ----------

    private Usuario yo(Principal p) {
        return usuarios.findByUsername(p.getName()).orElseThrow();
    }

    /** Mira los primeros bytes del archivo para saber si de verdad es una imagen. */
    public static String tipoDeImagen(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static ResponseStatusException error(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
