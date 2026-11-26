package com.pareja.duo.ahorros;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanDineroRepository extends JpaRepository<PlanDinero, Long> {
    Optional<PlanDinero> findByUsuario(String usuario);
}
