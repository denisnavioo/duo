package com.pareja.duo.actividad;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoPlanRepository extends JpaRepository<TipoPlan, Long> {

    List<TipoPlan> findAllByOrderByOrdenAscIdAsc();

    boolean existsByNombreIgnoreCase(String nombre);
}
