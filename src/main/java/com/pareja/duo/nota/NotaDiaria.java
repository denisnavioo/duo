package com.pareja.duo.nota;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

/** La nota del dia que uno escribe y el otro lee. Una por persona y dia. */
@Entity
@Table(name = "notas_diarias", uniqueConstraints = @UniqueConstraint(columnNames = {"autor", "dia"}))
@Getter
@Setter
@NoArgsConstructor
public class NotaDiaria {

    public static final int MAX = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String autor;

    @Column(nullable = false)
    private LocalDate dia;

    @Column(nullable = false, length = MAX)
    private String texto;

    @Column(nullable = false)
    private LocalDateTime actualizada;
}
