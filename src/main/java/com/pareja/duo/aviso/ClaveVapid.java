package com.pareja.duo.aviso;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Las llaves con las que Duo firma sus avisos (VAPID). Se crean solas la primera vez
 * y se guardan en la base de datos: si cambiaran, los moviles tendrian que volver a activar los avisos.
 */
@Entity
@Table(name = "clave_vapid")
@Getter
@Setter
@NoArgsConstructor
public class ClaveVapid {

    @Id
    private Long id = 1L;

    /** Clave publica sin comprimir (65 bytes), en base64url. */
    @Column(nullable = false, length = 120)
    private String publica;

    /** Clave privada PKCS#8, en base64url. */
    @Column(nullable = false, length = 400)
    private String privada;
}
