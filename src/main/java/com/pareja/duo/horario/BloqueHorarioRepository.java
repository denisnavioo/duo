package com.pareja.duo.horario;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BloqueHorarioRepository extends JpaRepository<BloqueHorario, Long> {

    List<BloqueHorario> findAllByOrderByDiaAscInicioAsc();

    Optional<BloqueHorario> findByIdAndUsuario(Long id, String usuario);

    boolean existsByUsuario(String usuario);
}
