package com.digicash.app.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.digicash.app.data.local.AppDatabase;
import com.digicash.app.data.local.entity.SyncStatus;
import com.digicash.app.data.local.entity.TransactionEntity;
import com.digicash.app.data.local.entity.TransactionType;
import com.digicash.app.data.local.entity.WalletEntity;
import com.digicash.app.databinding.DialogGenerateQrBinding;
import com.digicash.app.qr.QRUtils;
import com.digicash.app.qr.model.PaymentQrData;
import com.digicash.app.qr.model.ReceiverIdentityQrData;
import com.digicash.app.security.CryptoManager;
import com.digicash.app.security.PublicKeyInfo;
import com.digicash.app.utils.MoneyUtils;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dialog that lets the current user (the sender) enter an amount, then
 * builds, signs, atomically records, and displays a dynamic Payment QR
 * for a previously-obtained receiver identity.
 *
 * This dialog accepts receiver identity data via its arguments (a JSON
 * string matching ReceiverIdentityQrData) rather than scanning it itself.
 * If no receiver identity was supplied, or it fails to parse, the dialog
 * shows an error and disables generation rather than silently guessing a
 * receiver.
 *
 * At most one transaction can ever be created per dialog instance: the
 * Generate button is disabled immediately on click (before any async
 * work starts) and is never re-enabled after a successful generation,
 * only after a recoverable failure.
 */
public class GenerateQrDialog extends DialogFragment {

    public static final String TAG = "GenerateQrDialog";

    private static final String ARG_RECEIVER_IDENTITY_JSON = "arg_receiver_identity_json";

    /** Validity window for a generated payment QR before it is considered expired. */
    private static final long PAYMENT_QR_VALIDITY_MILLIS = 5 * 60 * 1000L; // 5 minutes

    /** Rendered QR bitmap size in pixels (square). */
    private static final int QR_BITMAP_SIZE_PIXELS = 600;

    private DialogGenerateQrBinding binding;
    private CryptoManager cryptoManager;
    private QRUtils qrUtils;
    private AppDatabase appDatabase;
    private ExecutorService executorService;
    private Handler mainHandler;
    private Gson gson;

    private ReceiverIdentityQrData receiverIdentity;
    private boolean isProcessing = false;
    private boolean paymentAlreadyGenerated = false;

    /**
     * Callback interface implemented by the hosting Activity so it can
     * refresh its own displayed balance/pending-count after this dialog
     * successfully records a new outgoing payment.
     */
    public interface OnPaymentGeneratedListener {
        void onPaymentGenerated();
    }

    @Nullable
    private OnPaymentGeneratedListener listener;

    public static GenerateQrDialog newInstance(String receiverIdentityJson) {
        GenerateQrDialog dialog = new GenerateQrDialog();
        Bundle args = new Bundle();
        args.putString(ARG_RECEIVER_IDENTITY_JSON, receiverIdentityJson);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onAttach(@NonNull android.content.Context context) {
        super.onAttach(context);
        if (context instanceof OnPaymentGeneratedListener) {
            listener = (OnPaymentGeneratedListener) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cryptoManager = new CryptoManager();
        qrUtils = new QRUtils(cryptoManager);
        appDatabase = AppDatabase.getInstance(requireContext().getApplicationContext());
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        gson = new Gson();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = DialogGenerateQrBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        parseReceiverIdentityFromArguments();
        updateReceiverInfoDisplay();

        binding.buttonGenerate.setOnClickListener(v -> onGenerateClicked());
        binding.buttonClose.setOnClickListener(v -> dismiss());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executorService.shutdown();
        binding = null;
    }

    // =================================================================
    // Receiver identity handling
    // =================================================================

    private void parseReceiverIdentityFromArguments() {
        Bundle args = getArguments();
        String json = args != null ? args.getString(ARG_RECEIVER_IDENTITY_JSON) : null;
        if (json == null || json.trim().isEmpty()) {
            receiverIdentity = null;
            return;
        }
        try {
            receiverIdentity = gson.fromJson(json, ReceiverIdentityQrData.class);
        } catch (JsonSyntaxException e) {
            receiverIdentity = null;
        }
    }

    private void updateReceiverInfoDisplay() {
        if (receiverIdentity == null || receiverIdentity.getReceiverId() == null
                || receiverIdentity.getReceiverId().trim().isEmpty()) {
            showError(getString(com.digicash.app.R.string.error_no_receiver));
            binding.buttonGenerate.setEnabled(false);
            return;
        }
        binding.textReceiverInfo.setText(getString(com.digicash.app.R.string.placeholder_receiver_info)
                .replace("-", receiverIdentity.getReceiverId()));
    }

    // =================================================================
    // Generate button flow
    // =================================================================

    private void onGenerateClicked() {
        if (isProcessing || paymentAlreadyGenerated) {
            return;
        }
        if (receiverIdentity == null) {
            showError(getString(com.digicash.app.R.string.error_no_receiver));
            return;
        }

        long amountMinorUnits;
        try {
            amountMinorUnits = MoneyUtils.rupeesStringToPaise(binding.editAmount.getText().toString());
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            return;
        }

        hideError();
        isProcessing = true;
        binding.buttonGenerate.setEnabled(false);
        binding.editAmount.setEnabled(false);

        String receiverId = receiverIdentity.getReceiverId();
        executorService.execute(() -> generatePaymentInBackground(receiverId, amountMinorUnits));
    }

    private void generatePaymentInBackground(String receiverId, long amountMinorUnits) {
        try {
            cryptoManager.generateKeyPairIfNotExists();
            PublicKeyInfo publicKeyInfo = cryptoManager.getPublicKeyInfo();
            String senderId = publicKeyInfo.getSha256Fingerprint();
            String senderPublicKeyBase64 = publicKeyInfo.getBase64PublicKey();

            appDatabase.walletDao().insertWalletIfNotExists(new WalletEntity(senderId, 0L));
            WalletEntity wallet = appDatabase.walletDao().getWalletByUserId(senderId);
            long currentBalance = wallet != null ? wallet.getBalanceMinorUnits() : 0L;

            if (amountMinorUnits > currentBalance) {
                postError(getString(com.digicash.app.R.string.error_insufficient_balance));
                return;
            }

            String transactionId = cryptoManager.generateTransactionId();
            String nonce = cryptoManager.generateSecureNonce();
            long timestamp = System.currentTimeMillis();
            long expiryTimestamp = timestamp + PAYMENT_QR_VALIDITY_MILLIS;

            PaymentQrData paymentQrData = qrUtils.createSignedPaymentQrData(
                    transactionId,
                    senderId,
                    receiverId,
                    amountMinorUnits,
                    timestamp,
                    expiryTimestamp,
                    nonce,
                    senderPublicKeyBase64);

            TransactionEntity transactionEntity = new TransactionEntity(
                    transactionId,
                    senderId,
                    receiverId,
                    amountMinorUnits,
                    timestamp,
                    expiryTimestamp,
                    nonce,
                    senderPublicKeyBase64,
                    paymentQrData.getSignature(),
                    SyncStatus.PENDING,
                    TransactionType.SENT);

            appDatabase.recordOutgoingPayment(transactionEntity);

            Bitmap qrBitmap = qrUtils.generatePaymentQrBitmap(paymentQrData, QR_BITMAP_SIZE_PIXELS);

            mainHandler.post(() -> {
                paymentAlreadyGenerated = true;
                isProcessing = false;
                binding.imageQrCode.setImageBitmap(qrBitmap);
                binding.imageQrCode.setVisibility(View.VISIBLE);
                binding.textQrInstruction.setVisibility(View.VISIBLE);
                binding.buttonGenerate.setVisibility(View.GONE);
                if (listener != null) {
                    listener.onPaymentGenerated();
                }
            });

        } catch (AppDatabase.InsufficientBalanceException e) {
            postError(getString(com.digicash.app.R.string.error_insufficient_balance));
        } catch (AppDatabase.DuplicateTransactionException
                | CryptoManager.CryptoOperationException
                | QRUtils.QrGenerationException e) {
            postError(getString(com.digicash.app.R.string.error_generation_failed));
        }
    }

    // =================================================================
    // UI helpers
    // =================================================================

    private void postError(String message) {
        mainHandler.post(() -> {
            isProcessing = false;
            binding.buttonGenerate.setEnabled(true);
            binding.editAmount.setEnabled(true);
            showError(message);
        });
    }

    private void showError(@Nullable String message) {
        if (message == null) {
            hideError();
            return;
        }
        binding.textError.setText(message);
        binding.textError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        binding.textError.setVisibility(View.GONE);
    }
}
