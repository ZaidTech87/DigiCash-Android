package com.digicash.app.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.digicash.app.databinding.DialogMyIdentityQrBinding;
import com.digicash.app.qr.QRUtils;
import com.digicash.app.qr.model.ReceiverIdentityQrData;
import com.digicash.app.security.CryptoManager;
import com.digicash.app.security.PublicKeyInfo;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Displays the current device's own Receiver Identity QR, derived
 * directly from its existing Android Keystore identity - no scanning,
 * no network access, and no new persisted data involved.
 *
 * The identity shown here is produced through exactly the same
 * CryptoManager + QRUtils path used everywhere else in the app:
 *  - CryptoManager.generateKeyPairIfNotExists() ensures this device's
 *    RSA identity key pair exists (idempotent - a no-op if it already does).
 *  - CryptoManager.getPublicKeyInfo() reads back the PUBLIC key and its
 *    SHA-256 fingerprint. The private key is never read, held, displayed,
 *    or serialized anywhere in this class.
 *  - QRUtils.buildReceiverIdentityQrData(...) + generateIdentityQrBitmap(...)
 *    build and render the identical ReceiverIdentityQrData /
 *    DIGICASH_IDENTITY_V1 JSON shape that ScannerActivity's
 *    SCAN_MODE_RECEIVER_IDENTITY already knows how to parse and validate
 *    on another device - no new QR format is introduced.
 *
 * All Keystore/QR work runs on a background executor; only the resulting
 * Bitmap and fingerprint text are posted back to the main thread.
 *
 * Every failure branch below logs the underlying exception via Log.e
 * before showing the generic user-facing message, so a real-device
 * failure (where the Keystore is backed by actual secure hardware rather
 * than the emulator's lenient software implementation) can be diagnosed
 * from Logcat instead of only showing a generic error with no trace.
 */
public class MyIdentityQrDialog extends DialogFragment {

    public static final String TAG = "MyIdentityQrDialog";

    private static final String LOG_TAG = "DigiCashIdentityQr";

    /** Rendered QR bitmap size in pixels (square) - matches GenerateQrDialog's payment QR size. */
    private static final int QR_BITMAP_SIZE_PIXELS = 600;

    private DialogMyIdentityQrBinding binding;
    private CryptoManager cryptoManager;
    private QRUtils qrUtils;
    private ExecutorService executorService;
    private Handler mainHandler;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cryptoManager = new CryptoManager();
        qrUtils = new QRUtils(cryptoManager);
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = DialogMyIdentityQrBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.buttonIdentityClose.setOnClickListener(v -> dismiss());

        showLoadingState();
        executorService.execute(this::generateIdentityInBackground);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executorService.shutdown();
        binding = null;
    }

    // =================================================================
    // Background identity + QR generation
    // =================================================================

    private void generateIdentityInBackground() {
        try {
            cryptoManager.generateKeyPairIfNotExists();
            PublicKeyInfo publicKeyInfo = cryptoManager.getPublicKeyInfo();

            // receiverId is this device's fingerprint (the same value used as
            // senderId/receiverId everywhere else in the app); receiverPublicKeyId
            // carries the full Base64 public key so a scanning device can verify
            // signatures against this identity without any prior key exchange -
            // identical convention to GenerateQrDialog's sender fields.
            ReceiverIdentityQrData identityData = qrUtils.buildReceiverIdentityQrData(
                    publicKeyInfo.getSha256Fingerprint(),
                    publicKeyInfo.getBase64PublicKey());

            Bitmap qrBitmap = qrUtils.generateIdentityQrBitmap(identityData, QR_BITMAP_SIZE_PIXELS);

            String fingerprint = publicKeyInfo.getSha256Fingerprint();
            postSuccess(qrBitmap, fingerprint);

        } catch (CryptoManager.CryptoOperationException e) {
            Log.e(LOG_TAG, "Identity QR generation failed: Keystore/crypto operation error", e);
            postError(getString(com.digicash.app.R.string.error_identity_generation_failed));
        } catch (QRUtils.QrGenerationException e) {
            Log.e(LOG_TAG, "Identity QR generation failed: QR bitmap rendering error", e);
            postError(getString(com.digicash.app.R.string.error_identity_generation_failed));
        } catch (IllegalArgumentException e) {
            // Defensive only - buildReceiverIdentityQrData would only throw this for
            // a blank fingerprint/public key, which CryptoManager never produces for
            // a successfully retrieved PublicKeyInfo.
            Log.e(LOG_TAG, "Identity QR generation failed: invalid identity data", e);
            postError(getString(com.digicash.app.R.string.error_identity_generation_failed));
        } catch (Exception e) {
            // Final safety net: guarantees this dialog can never crash the app on
            // any device, regardless of what unexpected failure a particular
            // device's Keystore/hardware/OS combination might produce.
            Log.e(LOG_TAG, "Identity QR generation failed: unexpected error", e);
            postError(getString(com.digicash.app.R.string.error_identity_generation_failed));
        }
    }

    // =================================================================
    // UI state (main thread)
    // =================================================================

    private void showLoadingState() {
        binding.progressIdentityGenerating.setVisibility(View.VISIBLE);
        binding.imageIdentityQr.setVisibility(View.GONE);
        binding.textIdentityLabel.setVisibility(View.GONE);
        binding.textIdentityFingerprint.setVisibility(View.GONE);
        binding.textIdentityExplanation.setVisibility(View.GONE);
        binding.textIdentityError.setVisibility(View.GONE);
    }

    private void postSuccess(Bitmap qrBitmap, String fingerprint) {
        mainHandler.post(() -> {
            if (binding == null) {
                // Dialog was dismissed while generation was in flight - nothing to update.
                return;
            }
            binding.progressIdentityGenerating.setVisibility(View.GONE);
            binding.textIdentityError.setVisibility(View.GONE);

            binding.imageIdentityQr.setImageBitmap(qrBitmap);
            binding.imageIdentityQr.setVisibility(View.VISIBLE);
            binding.textIdentityLabel.setVisibility(View.VISIBLE);
            binding.textIdentityFingerprint.setText(fingerprint);
            binding.textIdentityFingerprint.setVisibility(View.VISIBLE);
            binding.textIdentityExplanation.setVisibility(View.VISIBLE);
        });
    }

    private void postError(String message) {
        mainHandler.post(() -> {
            if (binding == null) {
                return;
            }
            binding.progressIdentityGenerating.setVisibility(View.GONE);
            binding.imageIdentityQr.setVisibility(View.GONE);
            binding.textIdentityLabel.setVisibility(View.GONE);
            binding.textIdentityFingerprint.setVisibility(View.GONE);
            binding.textIdentityExplanation.setVisibility(View.GONE);

            binding.textIdentityError.setText(message);
            binding.textIdentityError.setVisibility(View.VISIBLE);
        });
    }
}