package com.digicash.app.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.HashSet;
import java.util.Set;

/**
 * Runs on a real device/emulator because AndroidKeyStore is only available
 * on the actual Android runtime, not in a plain JVM unit test.
 */
@RunWith(AndroidJUnit4.class)
public class CryptoManagerInstrumentedTest {

    private CryptoManager cryptoManager;

    @Before
    public void setUp() throws CryptoManager.CryptoOperationException {
        cryptoManager = new CryptoManager();
        cryptoManager.generateKeyPairIfNotExists();
    }

    @Test
    public void keyPairGeneration_createsRetrievableKeyPair() throws CryptoManager.CryptoOperationException {
        assertTrue(cryptoManager.isKeyPairPresent());
        PublicKey publicKey = cryptoManager.getPublicKey();
        assertNotNull(publicKey);
        assertEquals("RSA", publicKey.getAlgorithm());
    }

    @Test
    public void keyPairGeneration_isIdempotent() throws CryptoManager.CryptoOperationException {
        PublicKeyInfo first = cryptoManager.getPublicKeyInfo();
        cryptoManager.generateKeyPairIfNotExists();
        PublicKeyInfo second = cryptoManager.getPublicKeyInfo();
        assertEquals(first.getSha256Fingerprint(), second.getSha256Fingerprint());
    }

    @Test
    public void sign_thenVerify_withMatchingKey_succeeds() throws CryptoManager.CryptoOperationException {
        byte[] data = "digicash-test-payload".getBytes(StandardCharsets.UTF_8);
        String signature = cryptoManager.sign(data);
        PublicKey publicKey = cryptoManager.getPublicKey();
        assertTrue(cryptoManager.verify(data, signature, publicKey));
    }

    @Test
    public void verify_withTamperedData_isRejected() throws CryptoManager.CryptoOperationException {
        byte[] originalData = "original-payload".getBytes(StandardCharsets.UTF_8);
        byte[] tamperedData = "tampered-payload".getBytes(StandardCharsets.UTF_8);
        String signature = cryptoManager.sign(originalData);
        PublicKey publicKey = cryptoManager.getPublicKey();
        assertFalse(cryptoManager.verify(tamperedData, signature, publicKey));
    }

    @Test
    public void verify_withWrongPublicKey_isRejected() throws Exception {
        byte[] data = "some-payload".getBytes(StandardCharsets.UTF_8);
        String signature = cryptoManager.sign(data);

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair unrelatedKeyPair = generator.generateKeyPair();

        assertFalse(cryptoManager.verify(data, signature, unrelatedKeyPair.getPublic()));
    }

    @Test
    public void verify_withMalformedBase64Signature_isRejectedNotThrown() throws CryptoManager.CryptoOperationException {
        byte[] data = "payload".getBytes(StandardCharsets.UTF_8);
        PublicKey publicKey = cryptoManager.getPublicKey();
        assertFalse(cryptoManager.verify(data, "not-valid-base64-!!!", publicKey));
    }

    @Test
    public void generateSecureNonce_producesNonEmptyUniqueValues() {
        Set<String> nonces = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String nonce = cryptoManager.generateSecureNonce();
            assertNotNull(nonce);
            assertFalse(nonce.isEmpty());
            assertTrue("Nonce collision detected", nonces.add(nonce));
        }
    }

    @Test
    public void generateTransactionId_producesUniqueIdsWithExpectedPrefix() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String id = cryptoManager.generateTransactionId();
            assertTrue(id.startsWith(SecurityConstants.TRANSACTION_ID_PREFIX + "-"));
            assertTrue("Transaction ID collision detected", ids.add(id));
        }
    }
}
