package com.pareja.duo.horario;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un bloque fijo del horario semanal (una clase, un turno de trabajo, las practicas...). */
@Entity
@Table(name = "bloques_horario")
@Getter
@Setter
@NoArgsConstructor
public class BloqueHorario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String usuario;

    /** 1 = lunes ... 7 = domingo. */
    @Column(nullable = false)
    private int dia;

    @Column(nullable = false)
    private LocalTime inicio;

    @Column(nullable = false)
    private LocalTime fin;

    /** CLASE, PRACTICAS, TRABAJO u OTRO. */
    @Column(nullable = false, length = 12)
    private String tipo;

    @Column(nullable = false, length = 40)
    private String titulo;

    /** Profesor, empresa, etc. */
    @Column(length = 60)
    private String detalle;

    /** Aula o sitio. */
    @Column(length = 30)
    private String lugar;

    public BloqueHorario(String usuario, int dia, String inicio, String fin, String tipo,
                         String titulo, String detalle, String lugar) {
        this.usuario = usuario;
        this.dia = dia;
        this.inicio = LocalTime.parse(inicio);
        this.fin = LocalTime.parse(fin);
        this.tipo = tipo;
        this.titulo = titulo;
        this.detalle = detalle;
        this.lugar = lugar;
    }
}
