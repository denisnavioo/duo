package com.pareja.duo.repartir;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Algo que pagó una persona y se reparte entre los que participaron (ej. "1ª ronda", 7 €). */
@Entity
@Table(name = "gastos_quedada")
@Getter
@Setter
@NoArgsConstructor
public class GastoQuedada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quedada_id")
    private Quedada quedada;

    @Column(nullable = false, length = 60)
    private String concepto;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal importe;

    /** Quien lo pagó. */
    @Column(nullable = false, length = 30)
    private String pagador;

    /** Entre quienes se reparte (ej. sin Nora si no bebió). */
    @ElementCollection
    @CollectionTable(name = "gasto_quedada_participantes", joinColumns = @JoinColumn(name = "gasto_id"))
    @Column(name = "nombre", nullable = false, length = 30)
    private Set<String> participantes = new LinkedHashSet<>();
}
