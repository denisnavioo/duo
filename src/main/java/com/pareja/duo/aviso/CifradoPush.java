package com.pareja.duo.aviso;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifrado de los avisos Web Push (RFC 8291, "aes128gcm") y firma VAPID (RFC 8292),
 * solo con lo que trae Java: el servicio de avisos del movil no puede leer el contenido.
 */
public final class CifradoPush {

    private static final SecureRandom AZAR = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder D64 = Base64.getUrlDecoder();

    private CifradoPush() {
    }

    public static String b64(byte[] b) {
        return B64.encodeToString(b);
    }

    public static byte[] deB64(String s) {
        return D64.decode(s.replace('+', '-').replace('/', '_').replace("=", ""));
    }

    public static KeyPair nuevoPar() throws GeneralSecurityException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"), AZAR);
        return g.generateKeyPair();
    }

    /** Clave publica en el formato de Web Push: 0x04 || X || Y (65 bytes). */
    public static byte[] crudo(ECPublicKey k) {
        byte[] r = new byte[65];
        r[0] = 4;
        copiar32(k.getW().getAffineX(), r, 1);
        copiar32(k.getW().getAffineY(), r, 33);
        return r;
    }

    private static void copiar32(BigInteger n, byte[] destino, int desde) {
        byte[] b = n.toByteArray();
        int len = Math.min(b.length, 32);
        System.arraycopy(b, b.length - len, destino, desde + 32 - len, len);
    }

    public static ECPublicKey publicaDeCrudo(byte[] crudo) throws GeneralSecurityException {
        if (crudo.length != 65 || crudo[0] != 4) {
            throw new GeneralSecurityException("Clave publica no valida");
        }
        ECParameterSpec params = ((ECPublicKey) nuevoPar().getPublic()).getParams();
        ECPoint p = new ECPoint(new BigInteger(1, Arrays.copyOfRange(crudo, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(crudo, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(p, params));
    }

    public static PrivateKey privadaDePkcs8(byte[] pkcs8) throws GeneralSecurityException {
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }

    /** Cifra el mensaje para un navegador concreto (un solo registro, RFC 8188). */
    public static byte[] cifrar(byte[] mensaje, byte[] p256dh, byte[] auth) throws GeneralSecurityException {
        ECPublicKey delNavegador = publicaDeCrudo(p256dh);
        KeyPair nuestro = nuevoPar();
        byte[] nuestraPublica = crudo((ECPublicKey) nuestro.getPublic());

        byte[] salt = new byte[16];
        AZAR.nextBytes(salt);
        byte[][] claveYNonce = derivar(ecdh(nuestro.getPrivate(), delNavegador), auth, p256dh, nuestraPublica, salt);

        byte[] conDelimitador = Arrays.copyOf(mensaje, mensaje.length + 1);
        conDelimitador[mensaje.length] = 2; // ultimo (y unico) registro
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(claveYNonce[0], "AES"), new GCMParameterSpec(128, claveYNonce[1]));
        byte[] cifrado = c.doFinal(conDelimitador);

        return ByteBuffer.allocate(16 + 4 + 1 + 65 + cifrado.length)
                .put(salt).putInt(4096).put((byte) 65).put(nuestraPublica).put(cifrado).array();
    }

    static byte[] ecdh(PrivateKey privada, ECPublicKey publica) throws GeneralSecurityException {
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(privada);
        ka.doPhase(publica, true);
        return ka.generateSecret();
    }

    /** Devuelve {clave AES de 16 bytes, nonce de 12 bytes}. Lo usan cifrar y las pruebas. */
    static byte[][] derivar(byte[] secretoEcdh, byte[] auth, byte[] publicaNavegador, byte[] publicaServidor,
            byte[] salt) throws GeneralSecurityException {
        byte[] prkClave = hmac(auth, secretoEcdh);
        byte[] info = concatenar(ascii("WebPush: info"), new byte[] {0}, publicaNavegador, publicaServidor, new byte[] {1});
        byte[] ikm = hmac(prkClave, info);
        byte[] prk = hmac(salt, ikm);
        byte[] clave = Arrays.copyOf(hmac(prk, concatenar(ascii("Content-Encoding: aes128gcm"), new byte[] {0, 1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concatenar(ascii("Content-Encoding: nonce"), new byte[] {0, 1})), 12);
        return new byte[][] {clave, nonce};
    }

    /** Token VAPID (JWT ES256) que demuestra al servicio de avisos que el aviso viene de Duo. */
    public static String jwtVapid(String audiencia, String contacto, long caducaEnSegundos, PrivateKey privada)
            throws GeneralSecurityException {
        String cab = b64(ascii("{\"typ\":\"JWT\",\"alg\":\"ES256\"}"));
        String cuerpo = b64(("{\"aud\":\"" + audiencia + "\",\"exp\":" + caducaEnSegundos + ",\"sub\":\"" + contacto + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initSign(privada);
        s.update(ascii(cab + "." + cuerpo));
        return cab + "." + cuerpo + "." + b64(s.sign());
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] hmac(byte[] clave, byte[] datos) throws GeneralSecurityException {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(clave, "HmacSHA256"));
        return m.doFinal(datos);
    }

    private static byte[] concatenar(byte[]... partes) {
        int total = 0;
        for (byte[] p : partes) {
            total += p.length;
        }
        ByteBuffer b = ByteBuffer.allocate(total);
        for (byte[] p : partes) {
            b.put(p);
        }
        return b.array();
    }
}
