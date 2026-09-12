package com.digicash.app.qr;

import android.graphics.Bitmap;

import com.digicash.app.qr.model.PaymentQrData;
import com.digicash.app.qr.model.ReceiverIdentityQrData;
import com.digicash.app.security.CryptoManager;
import com.digicash.app.security.SecurityConstants;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Central QR generation and parsing service for DIGICASH.
 *
 * Responsibilities:
 *  - Build a deterministic canonical signing payload for a payment, sign
 *    it via CryptoManager, and serialize the result into QR-ready text.
 *  - Convert Receiver Identity data into QR-ready text.
 *  - Render QR-ready text into a scannable Bitmap using ZXing.
 *  - Safely parse and structurally validate scanned QR text, rejecting
 *    malformed, mistyped, or expired content BEFORE any cryptographic
 *    signature check is attempted (that check is the receiver
 *    verification flow's responsibility, in ScannerActivity).
 *
 * This class has no dependency on Room, Retrofit, WorkManager, or camera
 * scanning code - it only deals with data-in/data-out around a QR string.
 */
public class QRUtils {

    /** Current supported protocol version for Payment QR payloads. */
    public static final int PAYMENT_PROTOCOL_VERSION = 1;

    /** Current supported protocol version for Receiver Identity QR payloads. */
    public static final int IDENTITY_PROTOCOL_VERSION = 1;

    /** Delimiter used when building the canonical (pre-signature) payment string. */
    private static final String CANONICAL_DELIMITER = "|";

    /** Escape character used to neutralize delimiter characters inside field values. */
    private static final String CANONICAL_ESCAPE = "\\";

    /**
     * Maximum tolerance for a payment timestamp being ahead of this
     * device's clock, to account for reasonable clock drift between
     * sender and receiver devices while still rejecting grossly
     * future-dated (and therefore invalid) timestamps.
     */
    private static final long CLOCK_SKEW_TOLERANCE_MILLIS = 5 * 60 * 1000L; // 5 minutes

    /** Matches transaction IDs produced by CryptoManager.generateTransactionId(). */
    private static final Pattern TRANSACTION_ID_PATTERN = Pattern.compile(
            "^" + Pattern.quote(SecurityConstants.TRANSACTION_ID_PREFIX) + "-\\d+-[A-Za-z0-9_-]+$");

    private final CryptoManager cryptoManager;
    private final Gson gson;

    public QRUtils(CryptoManager cryptoManager) {
        if (cryptoManager == null) {
            throw new IllegalArgumentException("cryptoManager must not be null");
        }
        this.cryptoManager = cryptoManager;
        this.gson = new Gson();
    }

    // =================================================================
    // Payment QR - creation (sender side)
    // =================================================================

    /**
     * Builds the deterministic canonical string that is signed for a
     * payment. The signature itself is NEVER included in this string -
     * only the fields that define what is actually being agreed to.
     * Field order is fixed and each field is delimiter-escaped so that
     * delimiter characters inside a field value cannot shift field
     * boundaries and change the effective meaning of the signed payload.
     *
     * Exposed as public so ScannerActivity's receiver-side verification
     * logic can rebuild the identical canonical string from a scanned
     * PaymentQrData and pass it to CryptoManager.verify().
     */
    public String buildCanonicalPaymentPayload(
            int protocolVersion,
            String transactionId,
            String senderId,
            String receiverId,
            long amountMinorUnits,
            long timestamp,
            long expiryTimestamp,
            String nonce,
            String senderPublicKeyId) {

        StringBuilder builder = new StringBuilder();
        builder.append(protocolVersion).append(CANONICAL_DELIMITER);
        builder.append(escapeCanonicalField(transactionId)).append(CANONICAL_DELIMITER);
        builder.append(escapeCanonicalField(senderId)).append(CANONICAL_DELIMITER);
        builder.append(escapeCanonicalField(receiverId)).append(CANONICAL_DELIMITER);
        builder.append(amountMinorUnits).append(CANONICAL_DELIMITER);
        builder.append(timestamp).append(CANONICAL_DELIMITER);
        builder.append(expiryTimestamp).append(CANONICAL_DELIMITER);
        builder.append(escapeCanonicalField(nonce)).append(CANONICAL_DELIMITER);
        builder.append(escapeCanonicalField(senderPublicKeyId));
        return builder.toString();
    }

    /**
     * Convenience overload that derives the canonical string from an
     * already-populated PaymentQrData instance (ignoring its signature
     * and type fields, which are never part of what gets signed).
     */
    public String buildCanonicalPaymentPayload(PaymentQrData data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        return buildCanonicalPaymentPayload(
                data.getProtocolVersion(),
                data.getTransactionId(),
                data.getSenderId(),
                data.getReceiverId(),
                data.getAmountMinorUnits(),
                data.getTimestamp(),
                data.getExpiryTimestamp(),
                data.getNonce(),
                data.getSenderPublicKeyId());
    }

    /**
     * Builds and signs a complete PaymentQrData instance ready to be
     * serialized and rendered as a QR code. All fields are validated up
     * front using the same rules the receiver side will later apply, so
     * a locally malformed payment can never be signed in the first place.
     *
     * @throws IllegalArgumentException if any field fails structural
     *         validation.
     * @throws CryptoManager.CryptoOperationException if signing fails.
     */
    public PaymentQrData createSignedPaymentQrData(
            String transactionId,
            String senderId,
            String receiverId,
            long amountMinorUnits,
            long timestamp,
            long expiryTimestamp,
            String nonce,
            String senderPublicKeyId) throws CryptoManager.CryptoOperationException {

        try {
            requireNonBlank(transactionId, "transactionId");
            requireNonBlank(senderId, "senderId");
            requireNonBlank(receiverId, "receiverId");
            requireNonBlank(nonce, "nonce");
            requireNonBlank(senderPublicKeyId, "senderPublicKeyId");
            requireValidTransactionIdFormat(transactionId);
            requirePositiveAmount(amountMinorUnits);
            requireValidTimestampOrdering(timestamp, expiryTimestamp);
        } catch (QrValidationException e) {
            // The shared private validators declare a checked QrValidationException
            // because they are also reused by the receiver-side parse/verify path
            // (parseAndValidatePaymentQr), where that checked exception is the
            // correct, documented contract. On this creation side, a validation
            // failure is a caller/programmer error in the data being signed - so
            // it is re-thrown as the unchecked IllegalArgumentException documented
            // on this method, rather than forcing every signing call site to catch
            // a checked exception that only ever fires on locally malformed input.
            throw new IllegalArgumentException(e.getMessage(), e);
        }

        String canonicalPayload = buildCanonicalPaymentPayload(
                PAYMENT_PROTOCOL_VERSION,
                transactionId,
                senderId,
                receiverId,
                amountMinorUnits,
                timestamp,
                expiryTimestamp,
                nonce,
                senderPublicKeyId);

        String signature = cryptoManager.sign(canonicalPayload.getBytes(StandardCharsets.UTF_8));

        return new PaymentQrData(
                PAYMENT_PROTOCOL_VERSION,
                transactionId,
                senderId,
                receiverId,
                amountMinorUnits,
                timestamp,
                expiryTimestamp,
                nonce,
                senderPublicKeyId,
                signature);
    }

    // =================================================================
    // Receiver Identity QR - creation
    // =================================================================

    public ReceiverIdentityQrData buildReceiverIdentityQrData(String receiverId, String receiverPublicKeyId) {
        try {
            requireNonBlank(receiverId, "receiverId");
            requireNonBlank(receiverPublicKeyId, "receiverPublicKeyId");
        } catch (QrValidationException e) {
            // Same rationale as createSignedPaymentQrData: on this creation side
            // a validation failure is a caller/programmer error, so it is
            // re-thrown as an unchecked IllegalArgumentException.
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        return new ReceiverIdentityQrData(IDENTITY_PROTOCOL_VERSION, receiverId, receiverPublicKeyId);
    }

    // =================================================================
    // Serialization to QR-ready text
    // =================================================================

    public String toQrString(PaymentQrData data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        return gson.toJson(data);
    }

    public String toQrString(ReceiverIdentityQrData data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        return gson.toJson(data);
    }

    // =================================================================
    // Bitmap rendering (ZXing)
    // =================================================================

    public Bitmap generatePaymentQrBitmap(PaymentQrData data, int sizePixels) throws QrGenerationException {
        return generateBitmapFromContent(toQrString(data), sizePixels);
    }

    public Bitmap generateIdentityQrBitmap(ReceiverIdentityQrData data, int sizePixels) throws QrGenerationException {
        return generateBitmapFromContent(toQrString(data), sizePixels);
    }

    private Bitmap generateBitmapFromContent(String content, int sizePixels) throws QrGenerationException {
        if (content == null || content.isEmpty()) {
            throw new QrGenerationException("Cannot generate a QR bitmap from empty content");
        }
        if (sizePixels <= 0) {
            throw new QrGenerationException("QR bitmap size must be a positive number of pixels");
        }
        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            return barcodeEncoder.encodeBitmap(content, BarcodeFormat.QR_CODE, sizePixels, sizePixels);
        } catch (WriterException e) {
            throw new QrGenerationException("Failed to encode QR bitmap", e);
        }
    }

    // =================================================================
    // Parsing & structural validation (receiver side, pre-signature-check)
    // =================================================================

    /**
     * Parses and structurally validates scanned QR text as a
     * PaymentQrData. Performs: JSON well-formedness, type discrimination,
     * protocol version check, required-field presence, transaction ID
     * format, amount validity, timestamp validity, and expiry.
     *
     * Deliberately does NOT verify the cryptographic signature - that is
     * ScannerActivity's responsibility, which has access to the sender's
     * key context and the transaction/nonce replay-check database. This
     * method only guarantees that a structurally sound, non-expired
     * PaymentQrData object comes out the other end, or a descriptive
     * exception is thrown instead.
     *
     * @throws QrValidationException on any structural or expiry failure.
     */
    public PaymentQrData parseAndValidatePaymentQr(String rawScannedContent) throws QrValidationException {
        String trimmed = safeTrim(rawScannedContent);
        if (trimmed.isEmpty()) {
            throw new QrValidationException(QrValidationException.Reason.MALFORMED_JSON, "Scanned QR content is empty");
        }

        PaymentQrData data;
        try {
            data = gson.fromJson(trimmed, PaymentQrData.class);
        } catch (JsonSyntaxException e) {
            throw new QrValidationException(QrValidationException.Reason.MALFORMED_JSON, "Scanned QR content is not valid JSON", e);
        }

        if (data == null) {
            throw new QrValidationException(QrValidationException.Reason.MALFORMED_JSON, "Scanned QR content parsed to no data");
        }

        if (!PaymentQrData.TYPE_DISCRIMINATOR.equals(data.getType())) {
            throw new QrValidationException(
                    QrValidationException.Reason.WRONG_TYPE,
                    "Scanned QR is not a DIGICASH payment QR");
        }

        if (data.getProtocolVersion() != PAYMENT_PROTOCOL_VERSION) {
            throw new QrValidationException(
                    QrValidationException.Reason.UNSUPPORTED_VERSION,
                    "Unsupported payment QR protocol version: " + data.getProtocolVersion());
        }

        requireNonBlank(data.getTransactionId(), "transactionId");
        requireNonBlank(data.getSenderId(), "senderId");
        requireNonBlank(data.getReceiverId(), "receiverId");
        requireNonBlank(data.getNonce(), "nonce");
        requireNonBlank(data.getSenderPublicKeyId(), "senderPublicKeyId");
        requireNonBlank(data.getSignature(), "signature");

        requireValidTransactionIdFormat(data.getTransactionId());
        requirePositiveAmount(data.getAmountMinorUnits());
        requireValidTimestampOrdering(data.getTimestamp(), data.getExpiryTimestamp());
        requireNotExpired(data.getExpiryTimestamp());
        requireDecodablePublicKey(data.getSenderPublicKeyId());

        return data;
    }

    /**
     * Parses and structurally validates scanned QR text as a
     * ReceiverIdentityQrData. Performs: JSON well-formedness, type
     * discrimination, protocol version check, required-field presence,
     * and public key decodability.
     *
     * @throws QrValidationException on any structural failure.
     */
    public ReceiverIdentityQrData parseAndValidateReceiverIdentityQr(String rawScannedContent) throws QrValidationException {
        String trimmed = safeTrim(rawScannedContent);
        if (trimmed.isEmpty()) {
            throw new QrValidationException(QrValidationException.Reason.MALFORMED_JSON, "Scanned QR content is empty");
        }

        ReceiverIdentityQrData data;
        try {
            data = gson.fromJson(trimmed, ReceiverIdentityQrData.class);
        } catch (JsonSyntaxException e) {
            throw new QrValidationException(QrValidationException.Reason.MALFORMED_JSON, "Scanned QR content is not valid JSON", e);
        }

        if (data == null) {
            throw new QrValidationException(QrValidationException.Reason.MALFORMED_JSON, "Scanned QR content parsed to no data");
        }

        if (!ReceiverIdentityQrData.TYPE_DISCRIMINATOR.equals(data.getType())) {
            throw new QrValidationException(
                    QrValidationException.Reason.WRONG_TYPE,
                    "Scanned QR is not a DIGICASH identity QR");
        }

        if (data.getProtocolVersion() != IDENTITY_PROTOCOL_VERSION) {
            throw new QrValidationException(
                    QrValidationException.Reason.UNSUPPORTED_VERSION,
                    "Unsupported identity QR protocol version: " + data.getProtocolVersion());
        }

        requireNonBlank(data.getReceiverId(), "receiverId");
        requireNonBlank(data.getReceiverPublicKeyId(), "receiverPublicKeyId");
        requireDecodablePublicKey(data.getReceiverPublicKeyId());

        return data;
    }

    // =================================================================
    // Internal validation helpers
    // =================================================================

    private String escapeCanonicalField(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace(CANONICAL_ESCAPE, CANONICAL_ESCAPE + CANONICAL_ESCAPE)
                .replace(CANONICAL_DELIMITER, CANONICAL_ESCAPE + CANONICAL_DELIMITER);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private void requireNonBlank(String value, String fieldName) throws QrValidationException {
        if (value == null || value.trim().isEmpty()) {
            throw new QrValidationException(
                    QrValidationException.Reason.MISSING_FIELD,
                    "Required field '" + fieldName + "' is missing or empty");
        }
    }

    private void requireValidTransactionIdFormat(String transactionId) throws QrValidationException {
        if (!TRANSACTION_ID_PATTERN.matcher(transactionId).matches()) {
            throw new QrValidationException(
                    QrValidationException.Reason.INVALID_TRANSACTION_ID,
                    "Transaction ID does not match the expected format: " + transactionId);
        }
    }

    private void requirePositiveAmount(long amountMinorUnits) throws QrValidationException {
        if (amountMinorUnits <= 0) {
            throw new QrValidationException(
                    QrValidationException.Reason.INVALID_AMOUNT,
                    "Amount must be a positive number of minor currency units, got: " + amountMinorUnits);
        }
    }

    private void requireValidTimestampOrdering(long timestamp, long expiryTimestamp) throws QrValidationException {
        if (timestamp <= 0) {
            throw new QrValidationException(
                    QrValidationException.Reason.INVALID_TIMESTAMP,
                    "Timestamp must be a positive epoch-millisecond value, got: " + timestamp);
        }
        if (expiryTimestamp <= timestamp) {
            throw new QrValidationException(
                    QrValidationException.Reason.INVALID_TIMESTAMP,
                    "Expiry timestamp must be after the transaction timestamp");
        }
        long now = System.currentTimeMillis();
        if (timestamp > now + CLOCK_SKEW_TOLERANCE_MILLIS) {
            throw new QrValidationException(
                    QrValidationException.Reason.INVALID_TIMESTAMP,
                    "Timestamp is too far in the future to be valid");
        }
    }

    private void requireNotExpired(long expiryTimestamp) throws QrValidationException {
        long now = System.currentTimeMillis();
        if (now > expiryTimestamp) {
            throw new QrValidationException(
                    QrValidationException.Reason.EXPIRED,
                    "Transaction has expired");
        }
    }

    private void requireDecodablePublicKey(String base64PublicKey) throws QrValidationException {
        try {
            cryptoManager.decodePublicKeyFromBase64(base64PublicKey);
        } catch (CryptoManager.CryptoOperationException | IllegalArgumentException e) {
            throw new QrValidationException(
                    QrValidationException.Reason.INVALID_PUBLIC_KEY,
                    "Public key data in QR is not a valid RSA public key",
                    e);
        }
    }

    // =================================================================
    // Exceptions
    // =================================================================

    /**
     * Thrown when scanned QR content fails structural validation - the
     * QR is malformed, of the wrong type, missing fields, or otherwise
     * cannot be treated as a valid DIGICASH payload. Never thrown for a
     * cryptographic signature mismatch, since signature verification is
     * out of scope for this class.
     */
    public static final class QrValidationException extends Exception {

        public enum Reason {
            MALFORMED_JSON,
            WRONG_TYPE,
            UNSUPPORTED_VERSION,
            MISSING_FIELD,
            INVALID_AMOUNT,
            INVALID_TRANSACTION_ID,
            INVALID_TIMESTAMP,
            EXPIRED,
            INVALID_PUBLIC_KEY
        }

        private final Reason reason;

        public QrValidationException(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public QrValidationException(Reason reason, String message, Throwable cause) {
            super(message, cause);
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }
    }

    /**
     * Thrown when a QR Bitmap could not be generated from otherwise-valid
     * content (e.g. ZXing encoding failure, invalid requested dimensions).
     */
    public static final class QrGenerationException extends Exception {

        public QrGenerationException(String message) {
            super(message);
        }

        public QrGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}