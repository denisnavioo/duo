package com.pareja.duo.ahorros;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApartadoRepository extends JpaRepository<Apartado, Long> {
    List<Apartado> findByUsuarioOrderByOrdenAsc(String usuario);
    void deleteByUsuario(String usuario);
}
