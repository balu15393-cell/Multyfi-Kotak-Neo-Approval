package com.example.multyfikotakneo;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class KotakNeoAuthClient {
    private static final String LOGIN_URL = "https://mis.kotaksecurities.com/login/1.0/tradeApiLogin";
    private static final String VALIDATE_URL = "https://mis.kotaksecurities.com/login/1.0/tradeApiValidate";

    private KotakNeoAuthClient() {}

    public static KotakNeoSession login(KotakNeoCredentials credentials, String totp, String mpin) throws Exception {
        if (credentials == null || !credentials.isComplete()) {
            throw new IllegalArgumentException("Save your Kotak Neo API token/consumer key, registered mobile number and UCC first.");
        }
        String cleanTotp = digits(totp, "TOTP");
        String cleanMpin = digits(mpin, "MPIN");
        if (cleanTotp.length() != 6) throw new IllegalArgumentException("Kotak Neo TOTP must be 6 digits.");
        if (cleanMpin.length() != 6) throw new IllegalArgumentException("Kotak Neo MPIN must be 6 digits.");

        JSONObject loginBody = new JSONObject();
        loginBody.put("mobileNumber", normalizeMobile(credentials.mobileNumber));
        loginBody.put("ucc", credentials.ucc);
        loginBody.put("totp", cleanTotp);

        JSONObject step1 = post(LOGIN_URL, credentials.consumerKey, null, null, loginBody);
        JSONObject viewData = requireSuccessData(step1, "Kotak Neo TOTP login");
        String viewToken = viewData.optString("token", "").trim();
        String viewSid = viewData.optString("sid", "").trim();
        if (viewToken.isEmpty() || viewSid.isEmpty()) {
            throw new IllegalStateException("Kotak Neo TOTP login succeeded but did not return a view token/session ID.");
        }

        JSONObject validateBody = new JSONObject();
        validateBody.put("mpin", cleanMpin);
        JSONObject step2 = post(VALIDATE_URL, credentials.consumerKey, viewSid, viewToken, validateBody);
        JSONObject tradeData = requireSuccessData(step2, "Kotak Neo MPIN validation");

        KotakNeoSession session = new KotakNeoSession(
                tradeData.optString("token", ""),
                tradeData.optString("sid", ""),
                tradeData.optString("baseUrl", ""),
                tradeData.optString("hsServerId", ""),
                System.currentTimeMillis());
        if (!session.isComplete()) {
            throw new IllegalStateException("Kotak Neo did not return a complete trade session. Please log in again.");
        }
        return session;
    }

    private static JSONObject post(String endpoint, String consumerKey, String sid, String auth,
                                   JSONObject body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", consumerKey.trim());
        conn.setRequestProperty("neo-fin-key", "neotradeapi");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        if (sid != null && !sid.isEmpty()) conn.setRequestProperty("sid", sid);
        if (auth != null && !auth.isEmpty()) conn.setRequestProperty("Auth", auth);

        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(data.length);
        try (OutputStream os = conn.getOutputStream()) { os.write(data); }

        int code = conn.getResponseCode();
        String raw = read(conn, code);
        conn.disconnect();
        if (raw.trim().isEmpty()) {
            throw new IllegalStateException("Kotak Neo login returned an empty response (HTTP " + code + ").");
        }
        JSONObject root = new JSONObject(raw);
        if (code < 200 || code >= 300) {
            throw new IllegalStateException(errorMessage(root, "Kotak Neo login failed (HTTP " + code + ")."));
        }
        return root;
    }

    private static JSONObject requireSuccessData(JSONObject root, String step) {
        JSONObject data = root.optJSONObject("data");
        String topStatus = root.optString("status", "");
        String dataStatus = data == null ? "" : data.optString("status", "");
        if (data == null || "error".equalsIgnoreCase(topStatus) || "failed".equalsIgnoreCase(dataStatus)) {
            throw new IllegalStateException(errorMessage(root, step + " failed."));
        }
        return data;
    }

    static String errorMessage(JSONObject root, String fallback) {
        if (root == null) return fallback;
        String m = root.optString("message", "").trim();
        if (!m.isEmpty()) return m;
        String em = root.optString("errorMessage", "").trim();
        if (!em.isEmpty()) return em;
        Object error = root.opt("error");
        if (error != null) {
            String e = String.valueOf(error).trim();
            if (!e.isEmpty() && !"null".equalsIgnoreCase(e)) return e;
        }
        return fallback;
    }

    private static String read(HttpURLConnection conn, int code) throws Exception {
        java.io.InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private static String digits(String value, String label) {
        String v = value == null ? "" : value.trim();
        if (!v.matches("\\d+")) throw new IllegalArgumentException(label + " must contain digits only.");
        return v;
    }

    private static String normalizeMobile(String mobile) {
        String m = mobile == null ? "" : mobile.trim().replace(" ", "");
        if (m.startsWith("+")) return m;
        if (m.startsWith("91") && m.length() == 12) return "+" + m;
        if (m.length() == 10 && m.matches("\\d{10}")) return "+91" + m;
        return m;
    }
}
