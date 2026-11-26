package com.pareja.duo.novedad;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NovedadRepository extends JpaRepository<Novedad, Long> {

    /** Lo que puede ver un usuario: todo lo compartido y lo privado suyo. */
    @Query("select n from Novedad n where n.privado = false or n.usuario = :yo order by n.fecha desc, n.id desc")
    List<Novedad> visiblesPara(String yo, Pageable pagina);

    /** Novedades del otro (no privadas) desde la ultima vez que se abrio la campanita. */
    @Query("select count(n) from Novedad n where n.usuario <> :yo and n.privado = false and n.fecha > :desde")
    long noLeidas(String yo, LocalDateTime desde);
}
