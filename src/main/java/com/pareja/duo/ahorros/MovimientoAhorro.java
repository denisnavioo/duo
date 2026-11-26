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

/** Dinero que entra (o sale, si es negativo) de una cuenta de ahorro. */
@Entity
@Table(name = "movimientos_ahorro")
@Getter
@Setter
@NoArgsConstructor
public class MovimientoAhorro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String usuario;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidad;

    /** Cuenta destino (ej. "Cuenta Trade", "Cuenta BBVA"). */
    @Column(nullable = false, length = 40)
    private String cuenta;

    @Column(length = 80)
    private String nota;
}
