package com.pareja.duo.ahorros;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoBancoRepository extends JpaRepository<MovimientoBanco, Long> {
    Optional<MovimientoBanco> findByUsuarioAndHuella(String usuario, String huella);
    List<MovimientoBanco> findTop100ByUsuarioAndEstadoOrderByFechaDescIdDesc(String usuario, String estado);
    List<MovimientoBanco> findByUsuarioAndGastoIdIn(String usuario, Collection<Long> gastoIds);
    List<MovimientoBanco> findByUsuarioAndFechaBetweenOrderByFechaDescIdDesc(String usuario, java.time.LocalDate desde, java.time.LocalDate hasta);
    Optional<MovimientoBanco> findByIdAndUsuario(Long id, String usuario);
}
