package com.pareja.duo.repartir;

import java.math.BigDecimal;

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

/** Un Bizum ya hecho para saldar la deuda (ej. Ana le pasa 18,42 € a Nora). */
@Entity
@Table(name = "pagos_quedada")
@Getter
@Setter
@NoArgsConstructor
public class PagoQuedada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quedada_id")
    private Quedada quedada;

    @Column(name = "de_persona", nullable = false, length = 30)
    private String de;

    @Column(name = "a_persona", nullable = false, length = 30)
    private String para;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal importe;

    /** Movimiento del banco que demuestra este Bizum (de quien lo comprobó, que lo hizo o lo recibió). */
    @Column(name = "banco_movimiento_id")
    private Long bancoMovimientoId;

    /** Usuario de Duo dueño de ese movimiento del banco (solo el ve su concepto). */
    @Column(name = "banco_usuario", length = 50)
    private String bancoUsuario;
}
