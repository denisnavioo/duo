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
 * Vista "Mi plan" de Ahorros: lo que entra cada mes (siempre lo mismo) y que vista prefiere ver cada uno.
 * Uno por persona; los saldos y los apartados van aparte.
 */
@Entity
@Table(name = "planes_dinero")
@Getter
@Setter
@NoArgsConstructor
public class PlanDinero {

    public static final String VISTA_PLAN = "PLAN";
    public static final String VISTA_MES = "MES";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String usuario;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal ingresoMensual = BigDecimal.ZERO;

    @Column(nullable = false, length = 8)
    private String vista = VISTA_MES;

    /**
     * "Ahora no cobro": mientras este marcado, el plan cuenta con 0 € al mes, pero se guarda
     * ingresoMensual para volver a el al desmarcarlo. Null en los planes de antes (= cobra).
     */
    private Boolean sinIngresos;

    public PlanDinero(String usuario) {
        this.usuario = usuario;
    }
}
