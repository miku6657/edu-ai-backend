package com.edu.ai.system.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigCryptoServiceTest {

    private ConfigCryptoService createService() {

        String aesKey = java.util.Base64.getEncoder()
                .encodeToString(new byte[32]);

        byte[] hmacBytes = new byte[32];
        hmacBytes[0] = 1;

        String hmacKey = java.util.Base64.getEncoder()
                .encodeToString(hmacBytes);

        return new ConfigCryptoService(
                aesKey,
                hmacKey
        );
    }

    @Test
    void encryptAndDecryptShouldWork() {

        ConfigCryptoService service = createService();

        String original = "sk-test-123456789";

        String encrypted = service.encrypt(original);

        assertNotEquals(original, encrypted);

        assertEquals(
                original,
                service.decrypt(encrypted)
        );
    }

    @Test
    void sameKeyShouldHaveSameFingerprint() {

        ConfigCryptoService service = createService();

        String key = "sk-test-123456789";

        assertEquals(
                service.fingerprint(key),
                service.fingerprint(key)
        );
    }

    @Test
    void differentKeysShouldHaveDifferentFingerprints() {

        ConfigCryptoService service = createService();

        assertNotEquals(
                service.fingerprint("key-A"),
                service.fingerprint("key-B")
        );
    }

    @Test
    void encryptionShouldUseRandomIv() {

        ConfigCryptoService service = createService();

        String key = "sk-test-123456789";

        String encrypted1 = service.encrypt(key);
        String encrypted2 = service.encrypt(key);

        assertNotEquals(encrypted1, encrypted2);

        assertEquals(
                service.fingerprint(key),
                service.fingerprint(key)
        );
    }
}
