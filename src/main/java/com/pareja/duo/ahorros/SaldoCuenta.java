package com.pareja.duo.ahorros;

import java.math.BigDecimal;
import java.time.LocalDate;

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

/** "Lo que tengo": cuanto dinero hay ahora mismo en cada cuenta (nomina, ahorro, efectivo...). */
@Entity
@Table(name = "saldos_cuenta", uniqueConstraints = @UniqueConstraint(columnNames = {"usuario", "bancoCuentaUid"}))
@Getter
@Setter
@NoArgsConstructor
public class SaldoCuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String usuario;

    @Column(nullable = false, length = 40)
    private String nombre;

    /** Puede ser negativo (una tarjeta de credito con deuda). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidad;

    @Column(nullable = false)
    private LocalDate actualizado;

    /** UID opaco de la cuenta bancaria enlazada, si el saldo se actualiza por PSD2. */
    @Column(length = 100)
    private String bancoCuentaUid;
}
