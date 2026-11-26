package com.pareja.duo.actividad;

import java.time.LocalDateTime;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Foto de un plan del calendario (ya reducida en el navegador). */
@Entity
@Table(name = "fotos_actividad")
@Getter
@Setter
@NoArgsConstructor
public class FotoActividad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actividad_id")
    private Actividad actividad;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, length = 2_097_152)
    private byte[] datos;

    /** image/jpeg, image/png o image/webp. */
    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false, length = 50)
    private String subidaPor;

    @Column(nullable = false)
    private LocalDateTime subidaEn = LocalDateTime.now();
}
