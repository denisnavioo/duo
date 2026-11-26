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

/** Un ingreso de un mes (ej. "Sueldo trabajo", 812,50 €). Solo lo ve su dueño. */
@Entity
@Table(name = "ingresos")
@Getter
@Setter
@NoArgsConstructor
public class Ingreso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String usuario;

    /** Mes en formato "aaaa-mm". */
    @Column(nullable = false, length = 7)
    private String mes;

    @Column(nullable = false, length = 60)
    private String concepto;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal importe;

    public Ingreso(String usuario, String mes, String concepto, BigDecimal importe) {
        this.usuario = usuario;
        this.mes = mes;
        this.concepto = concepto;
        this.importe = importe;
    }
}
