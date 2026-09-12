package com.example.multyfikotakneo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private EditText capital, utilization, tolerance;
    private EditText consumerKey, mobile, ucc, totp, mpin;
    private Switch enabled;
    private CheckBox strict;
    private Switch liveExecution;
    private TextView status, result;
    private EditText testAlert, testLtp;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApprovalNotifier.createChannels(this);
        setContentView(buildUi());
        loadSettings();
        requestNotificationPermissionIfNeeded();
        updateStatus();
    }

    private View buildUi() {
        int pad = dp(16);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        root.addView(text("Multyfi → Kotak Neo Approval Assistant", 22, true));
        TextView subtitle = text(
                "Reads Multyfi notifications and prepares Kotak Neo MIS intraday entry/exit tickets. When live execution is enabled, your single APPROVE & SEND tap submits the prepared order through the Kotak Neo Trade API.", 14, false);
        subtitle.setPadding(0, dp(8), 0, dp(14));
        root.addView(subtitle);

        status = text("", 14, true);
        root.addView(status);

        Button access = button("1. Grant Notification Access");
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        root.addView(access);

        enabled = new Switch(this);
        enabled.setText("Monitor Multyfi notifications");
        enabled.setPadding(0, dp(10), 0, dp(4));
        root.addView(enabled);

        strict = new CheckBox(this);
        strict.setText("Strict intraday only (recommended)");
        strict.setChecked(true);
        root.addView(strict);

        liveExecution = new Switch(this);
        liveExecution.setText("One-tap Kotak Neo API execution (LIVE)");
        liveExecution.setPadding(0, dp(8), 0, dp(4));
        root.addView(liveExecution);
        TextView liveNote = text("OFF by default. When ON, tapping APPROVE & SEND or EXIT NOW & SEND submits a real MIS order. Kotak requires the API session and order request to come from the same whitelisted static IP.", 12, false);
        liveNote.setPadding(0, 0, 0, dp(6));
        root.addView(liveNote);

        root.addView(label("Capital per trade (₹)"));
        capital = number("50000");
        root.addView(capital);
        root.addView(label("Capital utilization (%)"));
        utilization = decimal("99.5");
        root.addView(utilization);
        root.addView(label("Entry tolerance (%)"));
        tolerance = decimal("0.20");
        root.addView(tolerance);

        Button saveTrading = button("2. Save Trade Settings");
        saveTrading.setOnClickListener(v -> saveTradeSettings());
        root.addView(saveTrading);

        TextView apiHeader = text("Kotak Neo Trade API login", 18, true);
        apiHeader.setPadding(0, dp(18), 0, dp(4));
        root.addView(apiHeader);

        root.addView(label("API Access Token / Consumer Key (encrypted)"));
        consumerKey = secret("Paste the token generated in Kotak Neo → More → Trade API");
        root.addView(consumerKey);

        root.addView(label("Registered mobile number"));
        mobile = new EditText(this);
        mobile.setSingleLine(true);
        mobile.setHint("10-digit number or +91XXXXXXXXXX");
        mobile.setInputType(InputType.TYPE_CLASS_PHONE);
        root.addView(mobile);

        root.addView(label("UCC / Client Code"));
        ucc = new EditText(this);
        ucc.setSingleLine(true);
        ucc.setHint("Your Kotak Neo UCC / Client Code");
        root.addView(ucc);

        Button saveCreds = button("3. Save Kotak Neo Credentials");
        saveCreds.setOnClickListener(v -> saveCredentials());
        root.addView(saveCreds);

        TextView credentialNote = text(
                "The API token/consumer key, mobile number and UCC are encrypted with Android Keystore. TOTP and MPIN below are used only for login and are not saved.",
                12, false);
        credentialNote.setPadding(0, dp(4), 0, dp(8));
        root.addView(credentialNote);

        root.addView(label("Current 6-digit TOTP"));
        totp = secret("Authenticator code");
        totp.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        root.addView(totp);

        root.addView(label("6-digit Kotak Neo MPIN"));
        mpin = secret("MPIN");
        mpin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        root.addView(mpin);

        Button login = button("4. Login Kotak Neo API");
        login.setOnClickListener(v -> loginKotak());
        root.addView(login);

        Button clearSession = button("Clear Kotak Neo API Session");
        clearSession.setOnClickListener(v -> {
            new KotakNeoSessionStore(this).clear();
            toast("Kotak Neo API session cleared.");
            updateStatus();
        });
        root.addView(clearSession);

        Button clearCredentials = button("Clear Saved Kotak Credentials");
        clearCredentials.setOnClickListener(v -> {
            new KotakNeoCredentialStore(this).clear();
            new KotakNeoSessionStore(this).clear();
            consumerKey.setText(""); mobile.setText(""); ucc.setText("");
            toast("Saved Kotak Neo credentials and session cleared.");
            updateStatus();
        });
        root.addView(clearCredentials);

        TextView testHeader = text("Test notification logic", 18, true);
        testHeader.setPadding(0, dp(18), 0, dp(4));
        root.addView(testHeader);

        testAlert = new EditText(this);
        testAlert.setMinLines(4);
        testAlert.setGravity(android.view.Gravity.TOP);
        testAlert.setText("MULTYFI INTRADAY\nBUY IRFC\nEntry: 82\nTarget: 85\nSL: 80.50");
        root.addView(testAlert);
        root.addView(label("Manual test LTP (₹)"));
        testLtp = decimal("82.05");
        root.addView(testLtp);

        Button test = button("5. Create Test Approval Notification");
        test.setOnClickListener(v -> runTest());
        root.addView(test);

        result = text("", 14, false);
        result.setTypeface(Typeface.MONOSPACE);
        result.setTextIsSelectable(true);
        result.setPadding(0, dp(12), 0, dp(16));
        root.addView(result);

        Button openNeo = button("Open Kotak Neo");
        openNeo.setOnClickListener(v -> TradeActionReceiverOpen.open(this));
        root.addView(openNeo);

        TextView safety = text(
                "Rules: Multyfi package only • MIS/NSE cash only • stop-loss required for entries • duplicate alerts blocked • " +
                        "urgent exits re-check the live Kotak Neo MIS position and pending exit orders immediately before submission • partial-exit wording is held for manual review • " +
                        "test notifications are always dry-run • live order submission requires the Kotak Neo whitelisted static-IP rule.", 12, false);
        safety.setPadding(0, dp(14), 0, dp(20));
        root.addView(safety);
        return scroll;
    }

    private void loadSettings() {
        SettingsRepo s = new SettingsRepo(this);
        enabled.setChecked(s.isEnabled());
        strict.setChecked(s.strictIntraday());
        liveExecution.setChecked(s.liveExecution());
        capital.setText(String.format(Locale.ROOT, "%.2f", s.capital()));
        utilization.setText(String.format(Locale.ROOT, "%.2f", s.utilizationPct()));
        tolerance.setText(String.format(Locale.ROOT, "%.2f", s.tolerancePct()));
        try {
            KotakNeoCredentials c = new KotakNeoCredentialStore(this).load();
            if (!c.mobileNumber.isEmpty()) mobile.setText(c.mobileNumber);
            if (!c.ucc.isEmpty()) ucc.setText(c.ucc);
            if (!c.consumerKey.isEmpty()) consumerKey.setHint("API token / consumer key is saved securely — paste a new one to replace it");
        } catch (Exception ignored) {}
    }

    private void saveTradeSettings() {
        try {
            double c = parsePositive(capital.getText().toString(), "Capital");
            double u = parsePositive(utilization.getText().toString(), "Utilization");
            double t = Double.parseDouble(tolerance.getText().toString().trim());
            if (u > 100) throw new IllegalArgumentException("Utilization cannot exceed 100%.");
            if (t < 0 || t > 5) throw new IllegalArgumentException("Tolerance must be 0–5%.");
            new SettingsRepo(this).save(enabled.isChecked(), c, u, t, strict.isChecked(), "NSE", liveExecution.isChecked());
            toast("Trade settings saved.");
            updateStatus();
        } catch (Exception e) { toast(message(e)); }
    }

    private void saveCredentials() {
        try {
            KotakNeoCredentialStore store = new KotakNeoCredentialStore(this);
            KotakNeoCredentials existing = store.load();
            String key = consumerKey.getText().toString().trim();
            if (key.isEmpty()) key = existing.consumerKey;
            KotakNeoCredentials c = new KotakNeoCredentials(key, mobile.getText().toString(), ucc.getText().toString());
            store.save(c);
            new KotakNeoSessionStore(this).clear(); // credentials changed/re-saved: force fresh login
            consumerKey.setText("");
            consumerKey.setHint("API token / consumer key saved securely");
            toast("Kotak Neo credentials saved. Now enter current TOTP + MPIN and log in.");
            updateStatus();
        } catch (Exception e) { toast(message(e)); }
    }

    private void loginKotak() {
        final String currentTotp = totp.getText().toString().trim();
        final String currentMpin = mpin.getText().toString().trim();
        result.setText("Logging in to Kotak Neo API…");
        executor.execute(() -> {
            try {
                KotakNeoCredentials c = new KotakNeoCredentialStore(this).load();
                KotakNeoSession session = KotakNeoAuthClient.login(c, currentTotp, currentMpin);
                new KotakNeoSessionStore(this).save(session);
                runOnUiThread(() -> {
                    totp.setText(""); mpin.setText("");
                    result.setText("Kotak Neo API session ready.\nBase URL: " + session.baseUrl);
                    toast("Kotak Neo API login successful.");
                    updateStatus();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    totp.setText(""); mpin.setText("");
                    result.setText("LOGIN FAILED: " + message(e));
                    toast(message(e));
                    updateStatus();
                });
            }
        });
    }

    private void runTest() {
        try {
            double c = parsePositive(capital.getText().toString(), "Capital");
            double u = parsePositive(utilization.getText().toString(), "Utilization");
            double t = Double.parseDouble(tolerance.getText().toString().trim());
            double ltp = parsePositive(testLtp.getText().toString(), "Test LTP");
            Signal signal = SignalParser.parse("Test Multyfi alert", testAlert.getText().toString(), strict.isChecked());
            TradeTicket ticket = TradeDecision.build(signal, ltp, c, u, t, "NSE");
            ticket.dryRun = true;
            TradeStore store = new TradeStore(this);
            store.saveTicket(ticket);
            store.appendLog("TEST_TICKET_PREPARED", ticket, ticket.reason);
            ApprovalNotifier.showApproval(this, ticket);
            result.setText(ticket.displayText());
            toast("Test approval notification created.");
        } catch (Exception e) {
            result.setText("BLOCKED: " + message(e));
            toast(message(e));
        }
    }

    private void updateStatus() {
        boolean listener = notificationListenerEnabled();
        boolean notifications = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boolean creds = new KotakNeoCredentialStore(this).hasCredentials();
        boolean session = new KotakNeoSessionStore(this).hasSession();
        status.setText("Notification access: " + (listener ? "ON" : "OFF") +
                "\nApp notifications: " + (notifications ? "ON" : "OFF") +
                "\nKotak Neo credentials: " + (creds ? "SAVED" : "MISSING") +
                "\nKotak Neo API session: " + (session ? "READY" : "LOGIN REQUIRED") +
                "\nOne-tap live execution: " + (new SettingsRepo(this).liveExecution() ? "ON" : "OFF"));
    }

    private boolean notificationListenerEnabled() {
        String enabledListeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return enabledListeners != null && enabledListeners.contains(getPackageName());
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2001);
        }
    }

    @Override protected void onResume() { super.onResume(); if (status != null) updateStatus(); }
    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }

    private Button button(String label) {
        Button b = new Button(this); b.setText(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4)); b.setLayoutParams(lp); return b;
    }
    private TextView text(String s, int sp, boolean bold) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(sp);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v;
    }
    private TextView label(String s) { TextView v = text(s, 13, true); v.setPadding(0, dp(10), 0, 0); return v; }
    private EditText number(String d) { EditText e = new EditText(this); e.setSingleLine(true); e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); e.setText(d); return e; }
    private EditText decimal(String d) { return number(d); }
    private EditText secret(String hint) { EditText e = new EditText(this); e.setSingleLine(true); e.setHint(hint); e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); return e; }
    private double parsePositive(String raw, String name) { double v = Double.parseDouble(raw.trim()); if (v <= 0) throw new IllegalArgumentException(name + " must be above zero."); return v; }
    private int dp(int x) { return (int) (x * getResources().getDisplayMetrics().density + 0.5f); }
    private void toast(String s) { Toast.makeText(this, s == null ? "Unknown error" : s, Toast.LENGTH_LONG).show(); }
    private static String message(Exception e) { return e == null || e.getMessage() == null || e.getMessage().trim().isEmpty() ? "Unknown error" : e.getMessage(); }

    public static final class TradeActionReceiverOpen {
        public static void open(Activity a) {
            Intent launch = a.getPackageManager().getLaunchIntentForPackage("com.kotak.neo");
            if (launch != null) a.startActivity(launch);
            else a.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://ntrade.kotakneo.com/")));
        }
    }
}
