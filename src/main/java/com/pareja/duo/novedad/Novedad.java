package com.pareja.duo.novedad;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Algo que uno de los dos ha añadido, quitado o cambiado (lo que sale en la campanita). */
@Entity
@Table(name = "novedades")
@Getter
@Setter
@NoArgsConstructor
public class Novedad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Quien lo hizo. */
    @Column(nullable = false, length = 50)
    private String usuario;

    /** ANADIR, QUITAR, CAMBIAR, ACEPTAR o RECHAZAR. */
    @Column(nullable = false, length = 10)
    private String accion;

    /** CALENDARIO, HORARIOS, AHORROS, REPARTIR o PERFIL. */
    @Column(nullable = false, length = 12)
    private String seccion;

    /** Frase sin el nombre, ej. "añadió «Cena» al calendario (vie 14 nov, 21:00)". */
    @Column(nullable = false, length = 200)
    private String texto;

    /** true si solo debe verla quien lo hizo (finanzas personales, contraseña). */
    @Column(nullable = false)
    private boolean privado;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();
}
