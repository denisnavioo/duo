package com.pareja.duo.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador interno de la persona: "persona-a" o "persona-b". */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** Nombre que se muestra en la web (ej. "Ana"). */
    @Column(nullable = false, length = 50)
    private String nombre;

    /** Foto de perfil (ya recortada y reducida en el navegador, maximo 1 MB). */
    @Lob
    @Column(length = 1_048_576)
    private byte[] foto;

    /** Tipo de la foto: image/jpeg, image/png o image/webp. */
    @Column(length = 20)
    private String fotoTipo;

    /** Sube cada vez que cambia la foto, para que el navegador no muestre la antigua. */
    private Long fotoVersion;

    /** Ultima vez que abrio la campanita de novedades. */
    private java.time.LocalDateTime ultimaVistaNovedades;

    public Usuario(String username, String nombre) {
        this.username = username;
        this.nombre = nombre;
    }

    public boolean isTieneFoto() {
        return foto != null && foto.length > 0;
    }

    /** Primera letra del nombre, para cuando no hay foto. */
    public String getInicial() {
        return nombre == null || nombre.isBlank() ? "?" : nombre.substring(0, 1).toUpperCase();
    }
}
