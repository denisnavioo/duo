package com.pareja.duo.ahorros;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IngresoRepository extends JpaRepository<Ingreso, Long> {

    List<Ingreso> findByUsuarioAndMesOrderByIdAsc(String usuario, String mes);

    Optional<Ingreso> findByIdAndUsuario(Long id, String usuario);

    Optional<Ingreso> findFirstByUsuarioAndMesLessThanOrderByMesDesc(String usuario, String mes);
}
