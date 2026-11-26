package com.pareja.duo.repartir;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un plan con amigos donde cada uno paga cosas (ej. "Bolera del sábado").
 * Guarda quién vino, qué se pagó y los Bizums ya hechos, para saber quién debe a quién.
 * La ven y la editan los dos.
 */
@Entity
@Table(name = "quedadas")
@Getter
@Setter
@NoArgsConstructor
public class Quedada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Column(nullable = false)
    private LocalDate fecha;

    /** Tipo de plan para el icono: COMIDA, COPAS, OCIO, VIAJE, FIESTA, REGALO u OTRO. */
    @Column(length = 12)
    private String tipo;

    @Column(nullable = false, length = 50)
    private String creadoPor;

    /** Nombres de la gente que vino, en el orden en que se añadieron. */
    @ElementCollection
    @CollectionTable(name = "quedada_personas", joinColumns = @JoinColumn(name = "quedada_id"))
    @OrderColumn(name = "orden")
    @Column(name = "nombre", nullable = false, length = 30)
    private List<String> personas = new ArrayList<>();

    @OneToMany(mappedBy = "quedada", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<GastoQuedada> gastos = new ArrayList<>();

    @OneToMany(mappedBy = "quedada", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PagoQuedada> pagos = new ArrayList<>();
}
