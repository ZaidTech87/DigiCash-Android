package com.digicash.app.qr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.digicash.app.qr.model.PaymentQrData;
import com.digicash.app.security.CryptoManager;
import com.digicash.app.security.PublicKeyInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class QRUtilsInstrumentedTest {

    private CryptoManager cryptoManager;
    private QRUtils qrUtils;

    @Before
    public void setUp() throws CryptoManager.CryptoOperationException {
        cryptoManager = new CryptoManager();
        cryptoManager.generateKeyPairIfNotExists();
        qrUtils = new QRUtils(cryptoManager);
    }

    @Test
    public void parseAndValidate_wellFormedPayment_succeeds() throws Exception {
        PublicKeyInfo info = cryptoManager.getPublicKeyInfo();
        long now = System.currentTimeMillis();
        PaymentQrData signed = qrUtils.createSignedPaymentQrData(
                cryptoManager.generateTransactionId(),
                info.getSha256Fingerprint(),
                "receiver-fingerprint-abc",
                500L,
                now,
                now + 60_000L,
                cryptoManager.generateSecureNonce(),
                info.getBase64PublicKey());

        String qrString = qrUtils.toQrString(signed);
        PaymentQrData parsed = qrUtils.parseAndValidatePaymentQr(qrString);
        assertEquals(signed.getTransactionId(), parsed.getTransactionId());
    }

    @Test
    public void parseAndValidate_malformedJson_isRejectedWithCorrectReason() {
        try {
            qrUtils.parseAndValidatePaymentQr("{ this is not valid json ][");
            fail("Expected QrValidationException");
        } catch (QRUtils.QrValidationException e) {
            assertEquals(QRUtils.QrValidationException.Reason.MALFORMED_JSON, e.getReason());
        }
    }

    @Test
    public void parseAndValidate_wrongTypeDiscriminator_isRejected() {
        String foreignJson = "{\"type\":\"SOME_OTHER_APP_QR\",\"protocolVersion\":1}";
        try {
            qrUtils.parseAndValidatePaymentQr(foreignJson);
            fail("Expected QrValidationException");
        } catch (QRUtils.QrValidationException e) {
            assertEquals(QRUtils.QrValidationException.Reason.WRONG_TYPE, e.getReason());
        }
    }

    @Test
    public void parseAndValidate_expiredPayment_isRejected() throws Exception {
        PublicKeyInfo info = cryptoManager.getPublicKeyInfo();
        long now = System.currentTimeMillis();
        long timestamp = now - 120_000L;
        long expiry = now - 60_000L; // already expired

        PaymentQrData expiredPayment = qrUtils.createSignedPaymentQrData(
                cryptoManager.generateTransactionId(),
                info.getSha256Fingerprint(),
                "receiver-fingerprint-abc",
                500L,
                timestamp,
                expiry,
                cryptoManager.generateSecureNonce(),
                info.getBase64PublicKey());

        String qrString = qrUtils.toQrString(expiredPayment);
        try {
            qrUtils.parseAndValidatePaymentQr(qrString);
            fail("Expected QrValidationException");
        } catch (QRUtils.QrValidationException e) {
            assertEquals(QRUtils.QrValidationException.Reason.EXPIRED, e.getReason());
        }
    }

    @Test
    public void createSignedPaymentQrData_zeroAmount_isRejectedAtCreationTime() throws Exception {
        PublicKeyInfo info = cryptoManager.getPublicKeyInfo();
        long now = System.currentTimeMillis();
        try {
            qrUtils.createSignedPaymentQrData(
                    cryptoManager.generateTransactionId(),
                    info.getSha256Fingerprint(),
                    "receiver-fingerprint-abc",
                    0L,
                    now,
                    now + 60_000L,
                    cryptoManager.generateSecureNonce(),
                    info.getBase64PublicKey());
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Amount validation happens BEFORE signing, so an invalid amount
            // never produces a scannable QR string in the first place.
        }
    }
}
