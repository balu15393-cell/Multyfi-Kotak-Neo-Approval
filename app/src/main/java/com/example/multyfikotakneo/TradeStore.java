package com.example.multyfikotakneo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class TradeStore {
    private static final String PREFS = "ticket_store";
    private static final String KEY_PREFIX = "ticket_";
    private static final String SEEN_PREFIX = "seen_";
    private static final String EXEC_PREFIX = "executing_";
    private final Context context;
    private final SharedPreferences prefs;

    public TradeStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveTicket(TradeTicket t) throws Exception {
        prefs.edit().putString(KEY_PREFIX + t.id, t.toJson().toString()).apply();
    }

    public TradeTicket loadTicket(String id) throws Exception {
        String raw = prefs.getString(KEY_PREFIX + id, null);
        return raw == null ? null : TradeTicket.fromJson(new JSONObject(raw));
    }

    public void deleteTicket(String id) {
        prefs.edit().remove(KEY_PREFIX + id).remove(EXEC_PREFIX + id).apply();
    }

    public boolean claimExecution(String ticketId) {
        synchronized (TradeStore.class) {
            String key = EXEC_PREFIX + ticketId;
            if (prefs.getBoolean(key, false)) return false;
            prefs.edit().putBoolean(key, true).commit();
            return true;
        }
    }

    public void releaseExecution(String ticketId) {
        prefs.edit().remove(EXEC_PREFIX + ticketId).apply();
    }

    public boolean isDuplicateToday(String rawAlert) {
        try {
            String hash = sha256(rawAlert);
            String key = SEEN_PREFIX + day() + "_" + hash;
            if (prefs.getBoolean(key, false)) return true;
            prefs.edit().putBoolean(key, true).apply();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public void appendLog(String event, TradeTicket t, String details) {
        try {
            File f = new File(context.getFilesDir(), "trade_log.csv");
            boolean exists = f.exists();
            FileWriter w = new FileWriter(f, true);
            if (!exists) {
                w.write("timestamp,event,symbol,side,advisory_entry,ltp,order_type,order_price,quantity,capital,target,stop_loss,details\n");
            }
            w.write(csv(now()) + "," + csv(event) + "," + csv(t == null ? "" : t.symbol) + "," +
                    csv(t == null ? "" : t.side) + "," + csv(t == null ? "" : t.advisoryEntry) + "," +
                    csv(t == null ? "" : String.format(Locale.ROOT, "%.2f", t.ltp)) + "," +
                    csv(t == null ? "" : t.orderType) + "," +
                    csv(t == null || t.orderPrice == null ? "" : String.format(Locale.ROOT, "%.2f", t.orderPrice)) + "," +
                    csv(t == null ? "" : Integer.toString(t.quantity)) + "," +
                    csv(t == null ? "" : String.format(Locale.ROOT, "%.2f", t.capital)) + "," +
                    csv(t == null || t.target == null ? "" : String.format(Locale.ROOT, "%.2f", t.target)) + "," +
                    csv(t == null || t.stopLoss == null ? "" : String.format(Locale.ROOT, "%.2f", t.stopLoss)) + "," +
                    csv(details == null ? "" : details) + "\n");
            w.close();
        } catch (Exception ignored) {}
    }

    public File logFile() {
        return new File(context.getFilesDir(), "trade_log.csv");
    }

    private static String csv(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static String sha256(String s) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        byte[] b = d.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (byte x : b) out.append(String.format(Locale.ROOT, "%02x", x));
        return out.toString();
    }

    private static String day() {
        return new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date());
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date());
    }
}
