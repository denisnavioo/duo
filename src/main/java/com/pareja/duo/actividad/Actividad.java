package com.pareja.duo.actividad;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Column;
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

/** Una actividad del calendario compartido. */
@Entity
@Table(name = "actividades")
@Getter
@Setter
@NoArgsConstructor
public class Actividad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String titulo;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime hora;

    /** Quien participa: el username de uno de los dos ("persona-a", "persona-b") o "juntos". */
    @Column(nullable = false, length = 20)
    private String quien;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tipo_id")
    private TipoPlan tipo;

    @Column(length = 80)
    private String lugar;

    /** Notas libres del plan (opcional). */
    @Column(length = 500)
    private String descripcion;

    /** Username de quien la creo. */
    @Column(nullable = false, length = 50)
    private String creadoPor;

    @Column(nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    /**
     * PENDIENTE: plan "juntos" que el otro aun no ha aceptado.
     * ACEPTADA: plan confirmado (o individual).
     * RECHAZADA: el otro lo ha denegado; solo lo ve quien lo creo, como aviso.
     */
    @Column(length = 12)
    private String estado = ACEPTADA;

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ACEPTADA = "ACEPTADA";
    public static final String RECHAZADA = "RECHAZADA";

    /** Las actividades creadas antes de existir el estado cuentan como aceptadas. */
    public String estadoEfectivo() {
        return estado == null ? ACEPTADA : estado;
    }
}
