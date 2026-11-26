package com.pareja.duo.repartir;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuedadaRepository extends JpaRepository<Quedada, Long> {

    List<Quedada> findAllByOrderByFechaDescIdDesc();
}
