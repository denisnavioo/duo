package com.pareja.duo.repartir;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PagoQuedadaRepository extends JpaRepository<PagoQuedada, Long> {

    /** Movimientos del banco de este usuario que ya demuestran algun Bizum (no se pueden usar dos veces). */
    @Query("select p.bancoMovimientoId from PagoQuedada p where p.bancoUsuario = ?1 and p.bancoMovimientoId is not null")
    List<Long> movimientosUsados(String usuario);
}
