/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.pareja.duo.logic;

import com.pareja.duo.entity.Tareas;
import com.pareja.duo.repository.TareasRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author Maxna
 */
@Service
public class TareasLogic {

    @Autowired
    private TareasRepository tareasRepository;
// Obtener todas las tareas

    public List<Tareas> listarTodas() {
        return tareasRepository.findAll();
    }

    // Guardar o actualizar una tarea
    public Tareas guardar(Tareas tarea) {
        return tareasRepository.save(tarea);
    }

    // Buscar una tarea por su ID
    public Tareas buscarPorId(long id) {
        return tareasRepository.findById(id).orElse(null);
    }

    // Eliminar una tarea por ID
    public void eliminar(long id) {
        tareasRepository.deleteById(id);
    }
}
