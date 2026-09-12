package com.example.multyfikotakneo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class KotakNeoScripResolver {
    public static final class Instrument {
        public final String token;
        public final String tradingSymbol;
        public final String symbol;

        Instrument(String token, String tradingSymbol, String symbol) {
            this.token = token;
            this.tradingSymbol = tradingSymbol;
            this.symbol = symbol;
        }
    }

    private static final String PREFS = "kotak_scrip_cache";
    private final Context context;

    public KotakNeoScripResolver(Context context) {
        this.context = context.getApplicationContext();
    }

    public Instrument resolveNseEquity(String consumerKey, KotakNeoSession session, String requestedSymbol) throws Exception {
        require(consumerKey, "Kotak Neo API token/consumer key is missing.");
        if (session == null || !session.isComplete()) {
            throw new IllegalStateException("Kotak Neo API session is missing. Log in with TOTP + MPIN first.");
        }
        String symbol = normalizeSymbol(requestedSymbol);
        if (symbol.isEmpty()) throw new IllegalArgumentException("Stock symbol is empty.");

        Instrument cached = loadCache(symbol);
        if (cached != null) return cached;

        String csvUrl = resolveNseCsvUrl(consumerKey, session.baseUrl);
        Instrument found = findInCsv(csvUrl, symbol);
        if (found == null) {
            throw new IllegalStateException("Kotak Neo scrip master did not contain NSE equity symbol " + symbol + ".");
        }
        saveCache(symbol, found);
        return found;
    }

    private String resolveNseCsvUrl(String consumerKey, String baseUrl) throws Exception {
        String[] paths = {
                "/script-details/1.0/masterscrip/file-paths",
                "/script-details/1.0/masterscript/file-paths"
        };
        Exception last = null;
        for (String path : paths) {
            try {
                String raw = getText(baseUrl + path, consumerKey, true);
                JSONObject root = new JSONObject(raw);
                JSONArray files = root.optJSONArray("filesPaths");
                if (files == null && root.optJSONObject("data") != null) files = root.optJSONObject("data").optJSONArray("filesPaths");
                if (files == null && root.optJSONObject("payload") != null) files = root.optJSONObject("payload").optJSONArray("filesPaths");
                if (files != null) {
                    for (int i = 0; i < files.length(); i++) {
                        String u = files.optString(i, "");
                        String lower = u.toLowerCase(Locale.ROOT);
                        if (lower.contains("nse_cm") && lower.endsWith(".csv")) return u;
                    }
                }
                throw new IllegalStateException("Kotak Neo scrip-master response did not include the NSE cash CSV URL.");
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IllegalStateException(last == null ? "Could not resolve Kotak Neo scrip master." : last.getMessage());
    }

    private Instrument findInCsv(String csvUrl, String requested) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(csvUrl).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("Accept", "text/csv,application/json,text/plain,*/*");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            conn.disconnect();
            throw new IllegalStateException("Kotak Neo NSE scrip-master download failed (HTTP " + code + ").");
        }
        InputStream in = conn.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String first = br.readLine();
        if (first == null) {
            br.close(); conn.disconnect();
            throw new IllegalStateException("Kotak Neo NSE scrip-master file was empty.");
        }

        // Some edge responses can wrap CSV text inside JSON. Handle that defensively.
        if (first.trim().startsWith("{")) {
            StringBuilder json = new StringBuilder(first);
            String line;
            while ((line = br.readLine()) != null) json.append('\n').append(line);
            br.close(); conn.disconnect();
            JSONObject o = new JSONObject(json.toString());
            String csv = o.optString("nse", "");
            if (csv.isEmpty()) throw new IllegalStateException("Kotak Neo returned JSON instead of the expected NSE CSV.");
            try (BufferedReader jr = new BufferedReader(new java.io.StringReader(csv))) {
                return scanCsv(jr, requested);
            }
        }

        Instrument out = scanCsvWithFirst(br, first, requested);
        br.close();
        conn.disconnect();
        return out;
    }

    private Instrument scanCsvWithFirst(BufferedReader br, String headerLine, String requested) throws Exception {
        List<String> headers = parseCsvLine(headerLine);
        Map<String, Integer> idx = headerIndex(headers);
        Instrument fallback = null;
        String line;
        while ((line = br.readLine()) != null) {
            List<String> row = parseCsvLine(line);
            Instrument candidate = candidate(row, idx, requested);
            if (candidate == null) continue;
            String group = value(row, idx, "pgroup");
            if ("EQ".equalsIgnoreCase(group)) return candidate;
            if (fallback == null) fallback = candidate;
        }
        return fallback;
    }

    private Instrument scanCsv(BufferedReader br, String requested) throws Exception {
        String header = br.readLine();
        return header == null ? null : scanCsvWithFirst(br, header, requested);
    }

    private static Instrument candidate(List<String> row, Map<String, Integer> idx, String requested) {
        String exSeg = value(row, idx, "pexchseg");
        if (!exSeg.isEmpty() && !"nse_cm".equalsIgnoreCase(exSeg)) return null;
        String token = value(row, idx, "psymbol");
        String symbolName = normalizeSymbol(value(row, idx, "psymbolname"));
        String trading = value(row, idx, "ptrdsymbol").trim();
        String tradingBase = normalizeSymbol(trading.replaceFirst("(?i)-EQ$", ""));
        if (!requested.equals(symbolName) && !requested.equals(tradingBase)) return null;
        if (token.isEmpty()) return null;
        if (trading.isEmpty()) trading = requested + "-EQ";
        return new Instrument(token, trading, requested);
    }

    private static Map<String, Integer> headerIndex(List<String> headers) {
        Map<String, Integer> out = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            out.put(headers.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        return out;
    }

    private static String value(List<String> row, Map<String, Integer> idx, String key) {
        Integer i = idx.get(key.toLowerCase(Locale.ROOT));
        return i == null || i < 0 || i >= row.size() ? "" : row.get(i).trim();
    }

    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"'); i++;
                } else quoted = !quoted;
            } else if (c == ',' && !quoted) {
                out.add(cell.toString()); cell.setLength(0);
            } else cell.append(c);
        }
        out.add(cell.toString());
        return out;
    }

    private Instrument loadCache(String symbol) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String prefix = cacheDay() + "_" + symbol + "_";
        String token = p.getString(prefix + "token", "");
        String ts = p.getString(prefix + "trading", "");
        return token.isEmpty() ? null : new Instrument(token, ts.isEmpty() ? symbol + "-EQ" : ts, symbol);
    }

    private void saveCache(String symbol, Instrument i) {
        String prefix = cacheDay() + "_" + symbol + "_";
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(prefix + "token", i.token)
                .putString(prefix + "trading", i.tradingSymbol)
                .apply();
    }

    private static String cacheDay() {
        return new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date());
    }

    static String getText(String endpoint, String consumerKey, boolean authHeader) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Accept", "application/json");
        if (authHeader) conn.setRequestProperty("Authorization", consumerKey.trim());
        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
        }
        conn.disconnect();
        if (code < 200 || code >= 300) {
            String msg = "Kotak Neo request failed (HTTP " + code + ").";
            try { msg = KotakNeoAuthClient.errorMessage(new JSONObject(sb.toString()), msg); } catch (Exception ignored) {}
            throw new IllegalStateException(msg);
        }
        return sb.toString();
    }

    private static String normalizeSymbol(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static void require(String v, String message) {
        if (v == null || v.trim().isEmpty()) throw new IllegalArgumentException(message);
    }
}
