/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.pareja.duo.repository;

import com.pareja.duo.entity.Tareas;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author Maxna
 */
public interface TareasRepository extends JpaRepository<Tareas, Long>{
    
}
