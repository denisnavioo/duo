package com.pareja.duo.actividad;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FotoActividadRepository extends JpaRepository<FotoActividad, Long> {

    /** Solo los ids, sin cargar las imagenes: [idFoto, idActividad]. */
    @Query("select f.id, f.actividad.id from FotoActividad f order by f.id")
    List<Object[]> idsPorActividad();

    @Query("select f.id from FotoActividad f where f.actividad.id = :actividadId order by f.id")
    List<Long> idsDe(@Param("actividadId") Long actividadId);

    long countByActividadId(@Param("actividadId") Long actividadId);

    @Modifying
    @Query("delete from FotoActividad f where f.actividad.id = :actividadId")
    void borrarDe(@Param("actividadId") Long actividadId);
}
