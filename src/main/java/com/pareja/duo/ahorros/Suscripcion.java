package com.pareja.duo.ahorros;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

/** Gasto fijo o suscripcion (Netflix, transporte trimestral...). */
@Entity
@Table(name = "suscripciones")
@Getter
@Setter
@NoArgsConstructor
public class Suscripcion {

    /** Meses entre cobros segun la frecuencia. */
    public static int meses(String frecuencia) {
        return switch (frecuencia) {
            case "TRIMESTRAL" -> 3;
            case "ANUAL" -> 12;
            default -> 1;
        };
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String usuario;

    @Column(nullable = false, length = 40)
    private String nombre;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    /** MENSUAL, TRIMESTRAL o ANUAL. */
    @Column(nullable = false, length = 12)
    private String frecuencia = "MENSUAL";

    /** Un dia de cobro conocido; a partir de el se calcula el siguiente. */
    @Column(nullable = false)
    private LocalDate cobro;

    /** "¿Lo uso?" del Excel. */
    @Column(nullable = false)
    private boolean enUso = true;

    @Column(length = 80)
    private String nota;

    /** Proximo cobro a partir de hoy (incluido). */
    public LocalDate siguienteCobro(LocalDate hoy) {
        LocalDate fecha = cobro;
        int paso = meses(frecuencia);
        while (fecha.isBefore(hoy)) {
            fecha = fecha.plusMonths(paso);
        }
        return fecha;
    }

    /** Lo que cuesta al mes (ej. 45,50 € trimestrales = 15,17 €/mes). */
    public BigDecimal costeMensual() {
        return precio.divide(BigDecimal.valueOf(meses(frecuencia)), 2, RoundingMode.HALF_UP);
    }
}
