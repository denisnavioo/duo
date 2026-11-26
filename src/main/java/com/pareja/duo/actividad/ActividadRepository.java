package com.pareja.duo.actividad;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActividadRepository extends JpaRepository<Actividad, Long> {

    List<Actividad> findAllByOrderByFechaAscHoraAsc();

    List<Actividad> findByFechaOrderByHoraAsc(java.time.LocalDate fecha);
}
