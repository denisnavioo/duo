package com.pareja.duo.ahorros;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {

    List<Suscripcion> findByUsuarioOrderByNombreAsc(String usuario);

    Optional<Suscripcion> findByIdAndUsuario(Long id, String usuario);
}
