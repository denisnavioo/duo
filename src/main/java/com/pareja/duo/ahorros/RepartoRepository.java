package com.pareja.duo.ahorros;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RepartoRepository extends JpaRepository<Reparto, Long> {

    Optional<Reparto> findByUsuarioAndMes(String usuario, String mes);

    Optional<Reparto> findFirstByUsuarioAndMesLessThanOrderByMesDesc(String usuario, String mes);
}
