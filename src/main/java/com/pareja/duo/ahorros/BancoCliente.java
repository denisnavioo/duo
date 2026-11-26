package com.pareja.duo.ahorros;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Abstracción del proveedor para mantener las pruebas aisladas de la red y del banco real. */
public interface BancoCliente {
    boolean disponible();
    JsonNode bancosEspaña();
    JsonNode iniciarAutorizacion(String nombreBanco, String state, String redirectUrl, OffsetDateTime validoHasta);
    JsonNode crearSesion(String code);
    /**
     * Quien esta delante cuando se consulta el banco (su IP y su navegador). Por PSD2, sin el usuario delante
     * el banco solo deja 4 consultas al dia; con estos datos la consulta cuenta como hecha por el usuario.
     * Null en la sincronizacion automatica.
     */
    record Psu(String ip, String agente) {}

    JsonNode movimientos(String cuentaUid, LocalDate desde, String continuationKey, Psu psu);
    JsonNode saldos(String cuentaUid, Psu psu);
    void cerrarSesion(String sessionId);
}
