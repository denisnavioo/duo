package com.pareja.duo.ahorros;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReglaCategoriaRepository extends JpaRepository<ReglaCategoria, Long> {
    List<ReglaCategoria> findByUsuarioOrderByIdAsc(String usuario);
    Optional<ReglaCategoria> findByIdAndUsuario(Long id, String usuario);
}
