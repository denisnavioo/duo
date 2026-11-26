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

@Entity
@Table(name = "movimientos_banco", uniqueConstraints = @UniqueConstraint(columnNames = {"usuario", "huella"}))
@Getter @Setter @NoArgsConstructor
public class MovimientoBanco {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 50) private String usuario;
    @Column(nullable = false, length = 64) private String huella;
    @Column(nullable = false, length = 100) private String idTransaccion;
    @Column(nullable = false, length = 100) private String cuentaUid;
    @Column(nullable = false) private LocalDate fecha;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal importe;
    @Column(nullable = false, length = 160) private String concepto;
    @Column(nullable = false, length = 20) private String estado = "IMPORTADO";
    /** true si entro dinero, false si salio (null en los importados antes de guardarlo: se deduce de gasto/ingreso). */
    private Boolean entrada;
    private Long gastoId;
    private Long ingresoId;
}
