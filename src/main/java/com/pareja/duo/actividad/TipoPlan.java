package com.pareja.duo.actividad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tipo de plan que aparece en el desplegable "Plan" (Cena, Cine, ...). */
@Entity
@Table(name = "tipos_plan")
@Getter
@Setter
@NoArgsConstructor
public class TipoPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String nombre;

    /** Nombre del icono SVG (ej. "cuenco", "cine"). */
    @Column(nullable = false, length = 20)
    private String icono;

    /** Orden en el desplegable: los tipos base primero, "Otro" al final. */
    @Column(nullable = false)
    private int orden;

    /** true si lo creo uno de los dos (no es de los tipos iniciales). */
    @Column(nullable = false)
    private boolean propio;

    public TipoPlan(String nombre, String icono, int orden, boolean propio) {
        this.nombre = nombre;
        this.icono = icono;
        this.orden = orden;
        this.propio = propio;
    }
}
