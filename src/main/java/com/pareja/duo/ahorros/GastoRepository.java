package com.pareja.duo.ahorros;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GastoRepository extends JpaRepository<Gasto, Long> {

    List<Gasto> findByUsuarioAndFechaBetweenOrderByFechaDescIdDesc(String usuario, LocalDate desde, LocalDate hasta);

    Optional<Gasto> findByIdAndUsuario(Long id, String usuario);

    List<Gasto> findTop300ByUsuarioOrderByIdDesc(String usuario);
}
