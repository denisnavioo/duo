package com.pareja.duo.ahorros;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Como se reparte el dinero de un mes, en porcentajes (como en el Excel). */
@Entity
@Table(name = "repartos", uniqueConstraints = @UniqueConstraint(columnNames = {"usuario", "mes"}))
@Getter
@Setter
@NoArgsConstructor
public class Reparto {

    public static final BigDecimal AHORRO = new BigDecimal("50");
    public static final BigDecimal VARIABLES = new BigDecimal("30");
    public static final BigDecimal FIJOS = new BigDecimal("10");
    public static final BigDecimal OCIO = new BigDecimal("10");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String usuario;

    @Column(nullable = false, length = 7)
    private String mes;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal ahorro = AHORRO;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal variables = VARIABLES;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal fijos = FIJOS;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal ocio = OCIO;

    public Reparto(String usuario, String mes) {
        this.usuario = usuario;
        this.mes = mes;
    }
}
