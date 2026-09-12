package com.digicash.app.ui.scan;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.digicash.app.data.local.AppDatabase;
import com.digicash.app.data.local.entity.SyncStatus;
import com.digicash.app.data.local.entity.TransactionEntity;
import com.digicash.app.data.local.entity.TransactionType;
import com.digicash.app.data.local.entity.WalletEntity;
import com.digicash.app.databinding.ActivityScannerBinding;
import com.digicash.app.qr.QRUtils;
import com.digicash.app.qr.model.PaymentQrData;
import com.digicash.app.qr.model.ReceiverIdentityQrData;
import com.digicash.app.security.CryptoManager;
import com.digicash.app.security.PublicKeyInfo;
import com.digicash.app.ui.MainActivity;
import com.digicash.app.utils.MoneyUtils;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dual-purpose QR scanner for DIGICASH, selected by the
 * {@link #EXTRA_SCAN_MODE} Intent extra:
 *
 *  - {@link #SCAN_MODE_PAYMENT} (default, used with no extra present -
 *    preserves all pre-existing "Receive Money" behavior exactly):
 *    scans a sender's Payment QR, structurally validates it,
 *    cryptographically verifies its signature, and - only if every check
 *    passes - atomically credits the local wallet and inserts the
 *    transaction as PENDING.
 *
 *  - {@link #SCAN_MODE_RECEIVER_IDENTITY} (used by "Send Money"): scans a
 *    Receiver Identity QR, structurally validates it via the same
 *    QRUtils used everywhere else in the app, and returns the validated
 *    identity JSON to the calling Activity via
 *    {@link MainActivity#EXTRA_RECEIVER_IDENTITY_JSON} so it can open
 *    GenerateQrDialog. No wallet/database mutation happens in this mode -
 *    identity scanning is purely a read/validate/return step.
 *
 * Both modes share all camera-permission handling, ZXing scan launching,
 * and background-thread execution - only the interpretation of the
 * scanned content differs.
 *
 * Security posture enforced by this class (payment mode):
 *  - The QR content is NEVER treated as a balance update by itself. The
 *    only balance mutation that ever happens is AppDatabase.recordIncomingPayment(),
 *    which derives the credited amount solely from the verified
 *    PaymentQrData.amountMinorUnits AFTER signature verification succeeds.
 *  - The sender's public key is taken from the QR itself, but it is not
 *    blindly trusted: this class independently recomputes the SHA-256
 *    fingerprint of that key and requires it to equal the QR's own
 *    claimed senderId before any signature check is even attempted.
 *  - Every DB read/write runs on a background executor - never on the UI
 *    thread.
 *  - A single AtomicBoolean guards against the ZXing scan callback (or a
 *    user double-tap) triggering verification/crediting more than once
 *    for the same scan session, in either mode.
 */
public class ScannerActivity extends AppCompatActivity {

    /** Intent extra selecting which scan mode this Activity instance runs in. */
    public static final String EXTRA_SCAN_MODE = "com.digicash.app.EXTRA_SCAN_MODE";

    /** Default mode: scan and verify a sender's signed Payment QR (Receive Money). */
    public static final String SCAN_MODE_PAYMENT = "PAYMENT";

    /** Scan and validate a Receiver Identity QR, then return it (Send Money). */
    public static final String SCAN_MODE_RECEIVER_IDENTITY = "RECEIVER_IDENTITY";

    private ActivityScannerBinding binding;
    private CryptoManager cryptoManager;
    private QRUtils qrUtils;
    private AppDatabase appDatabase;
    private ExecutorService executorService;
    private Handler mainHandler;

    /** True when this Activity instance is running in Receiver-Identity-scan mode. */
    private boolean isReceiverIdentityMode = false;

    /** Guards against processing more than one scan result concurrently or repeatedly. */
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    private final ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScanContract(), this::onScanResult);

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    hidePermissionStatus();
                    launchScanner();
                } else {
                    showPermissionStatus(getString(com.digicash.app.R.string.permission_camera_denied));
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScannerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        cryptoManager = new CryptoManager();
        qrUtils = new QRUtils(cryptoManager);
        appDatabase = AppDatabase.getInstance(getApplicationContext());
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        String scanMode = getIntent().getStringExtra(EXTRA_SCAN_MODE);
        isReceiverIdentityMode = SCAN_MODE_RECEIVER_IDENTITY.equals(scanMode);

        if (isReceiverIdentityMode) {
            binding.textScannerTitle.setText(com.digicash.app.R.string.scanner_title_identity);
            binding.textScannerInstruction.setText(com.digicash.app.R.string.scanner_instruction_identity);
        }

        binding.buttonStartScan.setOnClickListener(v -> onStartScanClicked());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }

    // =================================================================
    // Permission + scan launch
    // =================================================================

    private void onStartScanClicked() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            hidePermissionStatus();
            launchScanner();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchScanner() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt(getString(com.digicash.app.R.string.scanner_instruction));
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity.class);
        scanLauncher.launch(options);
    }

    private void showPermissionStatus(String message) {
        binding.textPermissionStatus.setText(message);
        binding.textPermissionStatus.setVisibility(View.VISIBLE);
    }

    private void hidePermissionStatus() {
        binding.textPermissionStatus.setVisibility(View.GONE);
    }

    // =================================================================
    // Scan result handling
    // =================================================================

    private void onScanResult(ScanIntentResult result) {
        if (result == null || result.getContents() == null) {
            // User cancelled the scan - not an error, just return to the landing screen.
            return;
        }

        if (!isProcessing.compareAndSet(false, true)) {
            // A verification is already in flight for a previous scan result - ignore
            // this one rather than starting a second concurrent verification/credit.
            return;
        }

        String rawScannedContent = result.getContents();
        setProcessingUiVisible(true);

        if (isReceiverIdentityMode) {
            executorService.execute(() -> processReceiverIdentityScan(rawScannedContent));
        } else {
            executorService.execute(() -> verifyAndCreditPayment(rawScannedContent));
        }
    }

    private void setProcessingUiVisible(boolean visible) {
        binding.buttonStartScan.setEnabled(!visible);
        binding.progressProcessing.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.textProcessingLabel.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    // =================================================================
    // Verification pipeline (background thread) - shared exception type
    // =================================================================

    /**
     * Signals a safe-to-display, non-leaking failure reason for the UI,
     * used by both the payment-verification and receiver-identity-scan
     * pipelines. Deliberately carries only a resource-string message,
     * never a raw exception message or stack trace, so internal
     * validation details are never surfaced to the person scanning a
     * possibly malicious QR.
     */
    private static final class ScanRejectedException extends Exception {
        private final int messageResId;

        ScanRejectedException(int messageResId) {
            this.messageResId = messageResId;
        }

        int getMessageResId() {
            return messageResId;
        }
    }

    // =================================================================
    // Payment QR pipeline (Receive Money) - unchanged from prior behavior
    // =================================================================

    private void verifyAndCreditPayment(String rawScannedContent) {
        try {
            // Structural validation, protocol version, required fields,
            // transaction ID format, amount positivity, timestamp/expiry
            // sanity, and public-key decodability - all performed inside QRUtils.
            PaymentQrData paymentQrData;
            try {
                paymentQrData = qrUtils.parseAndValidatePaymentQr(rawScannedContent);
            } catch (QRUtils.QrValidationException e) {
                throw new ScanRejectedException(mapValidationReasonToMessageResId(e.getReason()));
            }

            // Determine local user identity (the current device's wallet owner).
            cryptoManager.generateKeyPairIfNotExists();
            PublicKeyInfo localPublicKeyInfo = cryptoManager.getPublicKeyInfo();
            String localUserId = localPublicKeyInfo.getSha256Fingerprint();

            // Ensure this payment is actually addressed to this device's user.
            if (!localUserId.equals(paymentQrData.getReceiverId())) {
                throw new ScanRejectedException(com.digicash.app.R.string.error_not_addressed_to_you);
            }

            // Obtain the sender's public key AS CLAIMED BY THE QR ITSELF.
            // This is not yet "trusted" - it is only trusted once the fingerprint
            // check immediately below and the signature check after it both pass.
            PublicKey senderPublicKey;
            try {
                senderPublicKey = cryptoManager.decodePublicKeyFromBase64(paymentQrData.getSenderPublicKeyId());
            } catch (CryptoManager.CryptoOperationException e) {
                throw new ScanRejectedException(com.digicash.app.R.string.error_qr_invalid_key);
            }

            // Binding check: the claimed senderId MUST equal the fingerprint of the
            // key that is about to be used for verification. Without this check, a
            // payload could claim an arbitrary senderId while actually being signed
            // by a completely unrelated key.
            String recomputedSenderFingerprint;
            try {
                recomputedSenderFingerprint = cryptoManager.generateFingerprint(senderPublicKey);
            } catch (CryptoManager.CryptoOperationException e) {
                throw new ScanRejectedException(com.digicash.app.R.string.error_unexpected);
            }
            if (!recomputedSenderFingerprint.equals(paymentQrData.getSenderId())
                    && !paymentQrData.getSenderPublicKeyId().equals(paymentQrData.getSenderId())) {
                // Accept either convention: senderId as a short fingerprint, or
                // senderId equal to the full public key string, depending on how the
                // sending device populated it. Reject only if NEITHER matches.
                throw new ScanRejectedException(com.digicash.app.R.string.error_identity_mismatch);
            }

            // Verify the SHA256withRSA signature over the canonical payload.
            String canonicalPayload = qrUtils.buildCanonicalPaymentPayload(paymentQrData);
            boolean signatureValid;
            try {
                signatureValid = cryptoManager.verify(
                        canonicalPayload.getBytes(StandardCharsets.UTF_8),
                        paymentQrData.getSignature(),
                        senderPublicKey);
            } catch (CryptoManager.CryptoOperationException e) {
                throw new ScanRejectedException(com.digicash.app.R.string.error_unexpected);
            }
            if (!signatureValid) {
                throw new ScanRejectedException(com.digicash.app.R.string.error_signature_invalid);
            }

            // Pre-check for an already-recorded transactionId. The authoritative
            // check happens atomically inside recordIncomingPayment; this pre-check
            // exists only to fail fast with a clear message before doing the wallet
            // lookup below.
            if (appDatabase.transactionDao().countByTransactionId(paymentQrData.getTransactionId()) > 0) {
                throw new ScanRejectedException(com.digicash.app.R.string.error_duplicate_transaction);
            }

            // Pre-check for nonce replay from this sender. Same rationale - the
            // atomic authority is inside recordIncomingPayment.
            if (appDatabase.transactionDao().countBySenderAndNonce(
                    paymentQrData.getSenderPublicKeyId(), paymentQrData.getNonce()) > 0) {
                throw new ScanRejectedException(com.digicash.app.R.string.error_replay_detected);
            }

            // Ensure a wallet row exists for this local user before crediting it.
            appDatabase.walletDao().insertWalletIfNotExists(new WalletEntity(localUserId, 0L));

            // Atomically insert the transaction AND credit the wallet. The credited
            // amount comes ONLY from paymentQrData.getAmountMinorUnits(), which has
            // already been verified as positive and as part of the signed payload -
            // never from any separate "balance" field (no such field exists anywhere
            // in PaymentQrData).
            TransactionEntity transactionEntity = new TransactionEntity(
                    paymentQrData.getTransactionId(),
                    paymentQrData.getSenderId(),
                    paymentQrData.getReceiverId(),
                    paymentQrData.getAmountMinorUnits(),
                    paymentQrData.getTimestamp(),
                    paymentQrData.getExpiryTimestamp(),
                    paymentQrData.getNonce(),
                    paymentQrData.getSenderPublicKeyId(),
                    paymentQrData.getSignature(),
                    SyncStatus.PENDING,
                    TransactionType.RECEIVED);

            try {
                appDatabase.recordIncomingPayment(transactionEntity);
            } catch (AppDatabase.DuplicateTransactionException e) {
                throw new ScanRejectedException(com.digicash.app.R.string.error_duplicate_transaction);
            } catch (AppDatabase.ReplayDetectedException e) {
                throw new ScanRejectedException(com.digicash.app.R.string.error_replay_detected);
            }

            String amountDisplay = MoneyUtils.formatPaiseToRupees(paymentQrData.getAmountMinorUnits());
            String senderDisplay = paymentQrData.getSenderId();
            postSuccess(amountDisplay, senderDisplay);

        } catch (ScanRejectedException e) {
            postFailure(getString(e.getMessageResId()));
        } catch (CryptoManager.CryptoOperationException e) {
            postFailure(getString(com.digicash.app.R.string.error_unexpected));
        } catch (RuntimeException e) {
            // Final safety net: any unexpected runtime failure during verification
            // must never crash the app or silently credit a wallet - it is always
            // treated as a rejected payment with a generic, non-leaking message.
            postFailure(getString(com.digicash.app.R.string.error_unexpected));
        }
    }

    private int mapValidationReasonToMessageResId(QRUtils.QrValidationException.Reason reason) {
        switch (reason) {
            case UNSUPPORTED_VERSION:
                return com.digicash.app.R.string.error_qr_unsupported_version;
            case MISSING_FIELD:
                return com.digicash.app.R.string.error_qr_incomplete;
            case INVALID_AMOUNT:
                return com.digicash.app.R.string.error_qr_invalid_amount;
            case INVALID_TRANSACTION_ID:
                return com.digicash.app.R.string.error_qr_invalid_txn_id;
            case INVALID_TIMESTAMP:
                return com.digicash.app.R.string.error_qr_invalid_timestamp;
            case EXPIRED:
                return com.digicash.app.R.string.error_qr_expired;
            case INVALID_PUBLIC_KEY:
                return com.digicash.app.R.string.error_qr_invalid_key;
            case MALFORMED_JSON:
            case WRONG_TYPE:
            default:
                return com.digicash.app.R.string.error_qr_invalid_generic;
        }
    }

    private void postSuccess(String amountDisplay, String senderDisplay) {
        mainHandler.post(() -> {
            setProcessingUiVisible(false);
            String message = getString(com.digicash.app.R.string.success_message_format, amountDisplay, senderDisplay);
            new AlertDialog.Builder(this)
                    .setTitle(com.digicash.app.R.string.dialog_title_success)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton(com.digicash.app.R.string.button_ok, (dialog, which) -> {
                        dialog.dismiss();
                        finish();
                    })
                    .show();
        });
    }

    // =================================================================
    // Receiver Identity QR pipeline (Send Money) - new
    // =================================================================

    /**
     * Validates a scanned Receiver Identity QR using the same QRUtils
     * method already used elsewhere in the app, then hands the
     * canonical, validated JSON back to whichever Activity launched this
     * one in identity mode. No database or wallet access happens here -
     * this pipeline only reads and validates; GenerateQrDialog performs
     * the actual signing, atomic debit, and Payment QR generation once
     * MainActivity opens it with this result.
     */
    private void processReceiverIdentityScan(String rawScannedContent) {
        try {
            ReceiverIdentityQrData identityData;
            try {
                identityData = qrUtils.parseAndValidateReceiverIdentityQr(rawScannedContent);
            } catch (QRUtils.QrValidationException e) {
                throw new ScanRejectedException(mapReceiverValidationReasonToMessageResId(e.getReason()));
            }

            // Re-serialize through QRUtils rather than forwarding the raw scanned
            // string verbatim, so the receiving dialog always gets a canonical
            // JSON shape matching ReceiverIdentityQrData exactly, independent of
            // any incidental whitespace/formatting in the originally scanned text.
            String receiverIdentityJson = qrUtils.toQrString(identityData);

            postReceiverIdentitySuccess(receiverIdentityJson);

        } catch (ScanRejectedException e) {
            postFailure(getString(e.getMessageResId()));
        } catch (RuntimeException e) {
            postFailure(getString(com.digicash.app.R.string.error_unexpected));
        }
    }

    /**
     * Only the QrValidationException.Reason values that
     * parseAndValidateReceiverIdentityQr can actually throw are mapped
     * explicitly (MALFORMED_JSON, WRONG_TYPE, UNSUPPORTED_VERSION,
     * MISSING_FIELD, INVALID_PUBLIC_KEY) - the amount/transaction-id/
     * timestamp/expiry reasons only apply to Payment QRs and are handled
     * by the default branch purely so the switch compiles exhaustively.
     */
    private int mapReceiverValidationReasonToMessageResId(QRUtils.QrValidationException.Reason reason) {
        switch (reason) {
            case UNSUPPORTED_VERSION:
                return com.digicash.app.R.string.error_receiver_qr_unsupported_version;
            case MISSING_FIELD:
                return com.digicash.app.R.string.error_receiver_qr_incomplete;
            case INVALID_PUBLIC_KEY:
                return com.digicash.app.R.string.error_receiver_qr_invalid_key;
            case MALFORMED_JSON:
            case WRONG_TYPE:
            default:
                return com.digicash.app.R.string.error_receiver_qr_invalid_generic;
        }
    }

    private void postReceiverIdentitySuccess(String receiverIdentityJson) {
        mainHandler.post(() -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra(MainActivity.EXTRA_RECEIVER_IDENTITY_JSON, receiverIdentityJson);
            setResult(Activity.RESULT_OK, resultIntent);
            finish();
        });
    }

    // =================================================================
    // Shared failure UI (both modes)
    // =================================================================

    private void postFailure(String message) {
        mainHandler.post(() -> {
            setProcessingUiVisible(false);
            isProcessing.set(false);
            int titleResId = isReceiverIdentityMode
                    ? com.digicash.app.R.string.dialog_title_receiver_invalid
                    : com.digicash.app.R.string.dialog_title_failed;
            new AlertDialog.Builder(this)
                    .setTitle(titleResId)
                    .setMessage(message)
                    .setCancelable(true)
                    .setPositiveButton(com.digicash.app.R.string.button_scan_again, (dialog, which) -> dialog.dismiss())
                    .show();
        });
    }
}