package com.pareja.duo.ahorros;

import java.time.OffsetDateTime;
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
@Table(name = "conexiones_banco", uniqueConstraints = @UniqueConstraint(columnNames = "usuario"))
@Getter @Setter @NoArgsConstructor
public class ConexionBanco {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 50) private String usuario;
    @Column(nullable = false, length = 80) private String banco;
    @Column(nullable = false, length = 80) private String sessionId;
    /** JSON de UIDs y etiquetas devueltos por Enable Banking; no incluye IBAN ni credenciales. */
    @Column(nullable = false, length = 4000) private String cuentasJson;
    private OffsetDateTime validoHasta;
    @Column(nullable = false, length = 20) private String estado = "CONECTADO";
    private OffsetDateTime ultimaSincronizacion;
    @Column(length = 200) private String ultimoError;
}
