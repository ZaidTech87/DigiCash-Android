package com.digicash.app.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.digicash.app.data.local.AppDatabase;
import com.digicash.app.data.local.entity.TransactionEntity;
import com.digicash.app.data.local.entity.WalletEntity;
import com.digicash.app.databinding.ActivityMainBinding;
import com.digicash.app.security.CryptoManager;
import com.digicash.app.security.PublicKeyInfo;
import com.digicash.app.sync.SyncManager;
import com.digicash.app.ui.scan.ScannerActivity;
import com.digicash.app.utils.MoneyUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main wallet dashboard for DIGICASH.
 *
 * All Room database access here runs off the main thread via a dedicated
 * single-thread executor, with results posted back to the UI thread via a
 * Handler bound to the main Looper.
 *
 * "Send Money" and "Receive Money" both launch the same ScannerActivity,
 * distinguished by the ScannerActivity.EXTRA_SCAN_MODE Intent extra:
 *  - Send Money launches it in SCAN_MODE_RECEIVER_IDENTITY, expecting a
 *    result back (via identityScannerLauncher) containing a validated
 *    ReceiverIdentityQrData JSON under EXTRA_RECEIVER_IDENTITY_JSON,
 *    which is then used to open GenerateQrDialog.
 *  - Receive Money launches it with no mode extra, which defaults to
 *    SCAN_MODE_PAYMENT - this preserves the original "Receive Money"
 *    behavior exactly (fire-and-forget startActivity, no result expected,
 *    since ScannerActivity itself performs the wallet credit).
 *
 * "My Identity QR" opens MyIdentityQrDialog, which derives and displays
 * this device's own Receiver Identity QR from its existing Android
 * Keystore identity (via CryptoManager) - no scanning involved.
 */
public class MainActivity extends AppCompatActivity implements GenerateQrDialog.OnPaymentGeneratedListener {

    private static final String MY_IDENTITY_ACTIVITY_CLASS_NAME =
            "com.digicash.app.ui.identity.MyQrCodeActivity";

    /**
     * Contract constant: ScannerActivity, when launched in
     * SCAN_MODE_RECEIVER_IDENTITY, returns the scanned receiver's
     * identity as a JSON string (matching ReceiverIdentityQrData's Gson
     * shape) under this extra key.
     */
    public static final String EXTRA_RECEIVER_IDENTITY_JSON = "com.digicash.app.EXTRA_RECEIVER_IDENTITY_JSON";

    private ActivityMainBinding binding;
    private CryptoManager cryptoManager;
    private AppDatabase appDatabase;
    private ExecutorService executorService;
    private Handler mainHandler;

    private final ActivityResultLauncher<Intent> identityScannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String receiverIdentityJson = result.getData().getStringExtra(EXTRA_RECEIVER_IDENTITY_JSON);
                    if (receiverIdentityJson != null && !receiverIdentityJson.trim().isEmpty()) {
                        openGenerateQrDialog(receiverIdentityJson);
                    } else {
                        Toast.makeText(this, "Scanner returned no receiver data", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        cryptoManager = new CryptoManager();
        appDatabase = AppDatabase.getInstance(getApplicationContext());
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        binding.buttonSendMoney.setOnClickListener(v -> onSendMoneyClicked());
        binding.buttonReceiveMoney.setOnClickListener(v -> onReceiveMoneyClicked());
        binding.buttonSyncServer.setOnClickListener(v -> onSyncServerClicked());
        binding.buttonMyIdentityQr.setOnClickListener(v -> onMyIdentityQrClicked());

        // Opportunistically enable background sync. Safe to call every launch -
        // SyncManager uses ExistingPeriodicWorkPolicy.KEEP internally.
        SyncManager.enableAutomaticSync(getApplicationContext());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshWalletState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }

    @Override
    public void onPaymentGenerated() {
        refreshWalletState();
    }

    // =================================================================
    // Button actions
    // =================================================================

    private void onSendMoneyClicked() {
        Intent intent = new Intent(this, ScannerActivity.class);
        intent.putExtra(ScannerActivity.EXTRA_SCAN_MODE, ScannerActivity.SCAN_MODE_RECEIVER_IDENTITY);
        try {
            identityScannerLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, com.digicash.app.R.string.scanner_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void onReceiveMoneyClicked() {
        Intent intent = new Intent(this, ScannerActivity.class);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, com.digicash.app.R.string.receive_screen_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void onSyncServerClicked() {
        SyncManager.triggerImmediateSync(getApplicationContext());
        binding.textSyncStatus.setText(com.digicash.app.R.string.sync_status_started);
    }

    private void onMyIdentityQrClicked() {
        MyIdentityQrDialog dialog = new MyIdentityQrDialog();
        dialog.show(getSupportFragmentManager(), MyIdentityQrDialog.TAG);
    }

    // =================================================================
    // Dialog launching
    // =================================================================

    private void openGenerateQrDialog(String receiverIdentityJson) {
        GenerateQrDialog dialog = GenerateQrDialog.newInstance(receiverIdentityJson);
        dialog.show(getSupportFragmentManager(), GenerateQrDialog.TAG);
    }

    // =================================================================
    // Wallet state refresh
    // =================================================================

    private void refreshWalletState() {
        executorService.execute(() -> {
            try {
                cryptoManager.generateKeyPairIfNotExists();
                PublicKeyInfo publicKeyInfo = cryptoManager.getPublicKeyInfo();
                String userId = publicKeyInfo.getSha256Fingerprint();

                appDatabase.walletDao().insertWalletIfNotExists(new WalletEntity(userId, 10000L));
                WalletEntity wallet = appDatabase.walletDao().getWalletByUserId(userId);
                long balance = wallet != null ? wallet.getBalanceMinorUnits() : 0L;

                List<TransactionEntity> pending = appDatabase.transactionDao().getPendingTransactions();
                int pendingCount = pending.size();

                mainHandler.post(() -> {
                    binding.textBalanceValue.setText(MoneyUtils.formatPaiseToRupees(balance));
                    binding.textPendingCount.setText(
                            getString(com.digicash.app.R.string.placeholder_pending_count)
                                    .replace("0", String.valueOf(pendingCount)));
                });
            } catch (CryptoManager.CryptoOperationException e) {
                mainHandler.post(() ->
                        Toast.makeText(this, "Failed to initialize wallet identity", Toast.LENGTH_SHORT).show());
            }
        });
    }
}