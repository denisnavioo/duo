package com.pareja.duo.nota;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotaDiariaRepository extends JpaRepository<NotaDiaria, Long> {

    Optional<NotaDiaria> findByAutorAndDia(String autor, LocalDate dia);

    Optional<NotaDiaria> findFirstByAutorNotAndDia(String autor, LocalDate dia);
}
