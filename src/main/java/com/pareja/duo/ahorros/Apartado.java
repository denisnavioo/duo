package com.pareja.duo.ahorros;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un apartado de la vida (comida, ocio, ahorro...) y que porcentaje del dinero libre
 * (lo que entra menos los gastos fijos) se le da cada mes.
 */
@Entity
@Table(name = "apartados")
@Getter
@Setter
@NoArgsConstructor
public class Apartado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String usuario;

    @Column(nullable = false, length = 30)
    private String nombre;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentaje;

    /** Si es dinero que se guarda (cuenta para la proyeccion de "lo que tendras"). */
    @Column(nullable = false)
    private boolean ahorro;

    @Column(nullable = false)
    private int orden;

    public Apartado(String usuario, String nombre, BigDecimal porcentaje, boolean ahorro, int orden) {
        this.usuario = usuario;
        this.nombre = nombre;
        this.porcentaje = porcentaje;
        this.ahorro = ahorro;
        this.orden = orden;
    }
}
