package com.pareja.duo.aviso;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SuscripcionPushRepository extends JpaRepository<SuscripcionPush, Long> {

    List<SuscripcionPush> findByUsuario(String usuario);

    Optional<SuscripcionPush> findByEndpoint(String endpoint);
}
