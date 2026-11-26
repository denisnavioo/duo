package com.pareja.duo.aviso;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un movil (o navegador) donde alguien ha activado los avisos. */
@Entity
@Table(name = "suscripciones_push")
@Getter
@Setter
@NoArgsConstructor
public class SuscripcionPush {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String usuario;

    /** Direccion del servicio de avisos del movil (Google, Apple, Mozilla...). */
    @Column(nullable = false, unique = true, length = 1000)
    private String endpoint;

    /** Clave publica del navegador (base64url) para cifrar el aviso. */
    @Column(nullable = false, length = 120)
    private String p256dh;

    /** Secreto del navegador (base64url). */
    @Column(nullable = false, length = 60)
    private String auth;

    @Column(nullable = false)
    private LocalDateTime creadaEn = LocalDateTime.now();
}
