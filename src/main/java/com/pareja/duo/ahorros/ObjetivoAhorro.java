package com.pareja.duo.ahorros;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Meta de ahorro en comun (ej. "Viaje juntos", 1.200 €). La ven y aportan los dos. */
@Entity
@Table(name = "objetivos_ahorro")
@Getter
@Setter
@NoArgsConstructor
public class ObjetivoAhorro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String nombre;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal meta;

    /** Fecha limite opcional. */
    private LocalDate fechaLimite;

    @Column(nullable = false, length = 50)
    private String creadoPor;

    @OneToMany(mappedBy = "objetivo", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("fecha DESC, id DESC")
    private List<Aportacion> aportaciones = new ArrayList<>();
}
