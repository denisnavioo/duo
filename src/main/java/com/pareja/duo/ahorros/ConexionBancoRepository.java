package com.pareja.duo.ahorros;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConexionBancoRepository extends JpaRepository<ConexionBanco, Long> {
    Optional<ConexionBanco> findByUsuario(String usuario);
    List<ConexionBanco> findByEstado(String estado);
    Optional<ConexionBanco> findByUsuarioAndId(String usuario, Long id);
}
