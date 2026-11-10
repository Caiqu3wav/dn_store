package com.dnstore.backend.service.shipping;

import com.dnstore.backend.config.MelhorEnvioProperties;
import com.dnstore.backend.exception.ShippingGatewayException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
@ConditionalOnProperty(name = "shipping.gateway", havingValue = "melhorEnvio")
public class TokenCipher {

    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenCipher(MelhorEnvioProperties properties) {
        try {
            byte[] decodedKey = Base64.getDecoder().decode(properties.getTokenEncryptionKey());
            if (decodedKey.length != 32) {
                throw new IllegalStateException("MELHOR_ENVIO_TOKEN_ENCRYPTION_KEY must decode to 32 bytes.");
            }
            this.key = new SecretKeySpec(decodedKey, "AES");
            Arrays.fill(decodedKey, (byte) 0);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("MELHOR_ENVIO_TOKEN_ENCRYPTION_KEY must be valid base64.", exception);
        }
    }

    public byte[] encrypt(String value, String associatedData) {
        byte[] nonce = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt Melhor Envio token.");
        }
    }

    public String decrypt(byte[] value, String associatedData) {
        if (value == null || value.length <= NONCE_LENGTH) {
            throw new ShippingGatewayException("Stored Melhor Envio credentials are invalid.");
        }
        byte[] nonce = Arrays.copyOfRange(value, 0, NONCE_LENGTH);
        byte[] encrypted = Arrays.copyOfRange(value, NONCE_LENGTH, value.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new ShippingGatewayException("Stored Melhor Envio credentials cannot be decrypted.");
        }
    }
}
