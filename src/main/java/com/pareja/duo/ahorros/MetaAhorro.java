package com.pareja.duo.ahorros;

import java.math.BigDecimal;
import java.time.LocalDate;

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
 * Una meta personal de "Mi plan": tener cierta cantidad para una fecha (un coche, un viaje...).
 * Es privada. Si se consigue o no lo calcula la pagina con los ajustes del plan.
 */
@Entity
@Table(name = "metas_ahorro")
@Getter
@Setter
@NoArgsConstructor
public class MetaAhorro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String usuario;

    @Column(nullable = false, length = 40)
    private String nombre;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidad;

    @Column(nullable = false)
    private LocalDate fecha;
}
