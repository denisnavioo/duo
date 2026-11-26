package com.pareja.duo.ahorros;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ObjetivoAhorroRepository extends JpaRepository<ObjetivoAhorro, Long> {

    List<ObjetivoAhorro> findAllByOrderByIdAsc();
}
