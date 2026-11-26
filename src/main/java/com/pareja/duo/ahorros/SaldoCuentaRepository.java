package com.pareja.duo.ahorros;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SaldoCuentaRepository extends JpaRepository<SaldoCuenta, Long> {
    List<SaldoCuenta> findByUsuarioOrderByIdAsc(String usuario);
    Optional<SaldoCuenta> findByIdAndUsuario(Long id, String usuario);
    long countByUsuario(String usuario);
    Optional<SaldoCuenta> findByUsuarioAndBancoCuentaUid(String usuario, String bancoCuentaUid);
}
