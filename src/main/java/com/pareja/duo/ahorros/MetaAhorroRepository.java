package com.pareja.duo.ahorros;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MetaAhorroRepository extends JpaRepository<MetaAhorro, Long> {
    List<MetaAhorro> findByUsuarioOrderByFechaAscIdAsc(String usuario);
    Optional<MetaAhorro> findByIdAndUsuario(Long id, String usuario);
    long countByUsuario(String usuario);
}
