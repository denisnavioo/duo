package com.pareja.duo.ahorros;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoAhorroRepository extends JpaRepository<MovimientoAhorro, Long> {

    List<MovimientoAhorro> findByUsuarioOrderByFechaDescIdDesc(String usuario);

    Optional<MovimientoAhorro> findByIdAndUsuario(Long id, String usuario);
}
