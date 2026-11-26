package com.pareja.duo.aviso;

import java.security.GeneralSecurityException;
import java.security.KeyPair;

/** Da acceso a las pruebas a las partes internas del cifrado (para descifrar como el movil). */
public final class CifradoPushPruebas {

    private CifradoPushPruebas() {
    }

    public static byte[] ecdh(KeyPair movil, byte[] publicaServidor) throws GeneralSecurityException {
        return CifradoPush.ecdh(movil.getPrivate(), CifradoPush.publicaDeCrudo(publicaServidor));
    }

    public static byte[][] derivar(byte[] secreto, byte[] auth, byte[] p256dh, byte[] publicaServidor, byte[] salt)
            throws GeneralSecurityException {
        return CifradoPush.derivar(secreto, auth, p256dh, publicaServidor, salt);
    }
}
