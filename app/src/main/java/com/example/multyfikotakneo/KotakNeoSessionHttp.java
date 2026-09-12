package com.example.multyfikotakneo;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class KotakNeoSessionHttp {
    private KotakNeoSessionHttp() {}

    static String get(KotakNeoSession session, String path) throws Exception {
        if (session == null || !session.isComplete()) {
            throw new IllegalStateException("Kotak Neo API session is missing. Log in again with TOTP + MPIN.");
        }
        String endpoint = addServerId(session.baseUrl + path, session);
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Sid", session.sid);
        conn.setRequestProperty("Auth", session.tradeToken);
        int code = conn.getResponseCode();
        String raw = read(conn, code);
        conn.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException(apiError(raw,
                    "Kotak Neo API request failed (HTTP " + code + "). Log in again if the session expired."));
        }
        return raw;
    }

    static String postForm(KotakNeoSession session, String path, Map<String, String> form) throws Exception {
        if (session == null || !session.isComplete()) {
            throw new IllegalStateException("Kotak Neo API session is missing. Log in again with TOTP + MPIN.");
        }
        String endpoint = addServerId(session.baseUrl + path, session);
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (e.getValue() == null) continue;
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8.name()));
            body.append('=');
            body.append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8.name()));
        }
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Sid", session.sid);
        conn.setRequestProperty("Auth", session.tradeToken);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }
        int code = conn.getResponseCode();
        String raw = read(conn, code);
        conn.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException(apiError(raw,
                    "Kotak Neo order API failed (HTTP " + code + ")."));
        }
        return raw;
    }

    private static String addServerId(String endpoint, KotakNeoSession session) throws Exception {
        if (!session.serverId.isEmpty()) {
            endpoint += (endpoint.contains("?") ? "&" : "?") + "sId=" +
                    URLEncoder.encode(session.serverId, StandardCharsets.UTF_8.name());
        }
        return endpoint;
    }

    private static String read(HttpURLConnection conn, int code) throws Exception {
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String apiError(String raw, String fallback) {
        try {
            if (raw != null && !raw.trim().isEmpty()) {
                JSONObject o = new JSONObject(raw);
                for (String key : new String[]{"errMsg", "message", "errorMessage", "desc"}) {
                    String v = o.optString(key, "").trim();
                    if (!v.isEmpty()) return v;
                }
                return KotakNeoAuthClient.errorMessage(o, fallback);
            }
        } catch (Exception ignored) {}
        return fallback;
    }
}
