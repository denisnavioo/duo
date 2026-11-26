package com.pareja.duo.ahorros;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "reglas_categoria_banco")
@Getter @Setter @NoArgsConstructor
public class ReglaCategoria {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 50) private String usuario;
    @Column(nullable = false, length = 80) private String contiene;
    @Column(nullable = false, length = 30) private String accion;
    @Column(length = 30) private String categoria;
}
