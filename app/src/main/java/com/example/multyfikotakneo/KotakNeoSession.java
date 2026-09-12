package com.example.multyfikotakneo;

import org.json.JSONObject;

public final class KotakNeoSession {
    public final String tradeToken;
    public final String sid;
    public final String baseUrl;
    public final String serverId;
    public final long createdAt;

    public KotakNeoSession(String tradeToken, String sid, String baseUrl, String serverId, long createdAt) {
        this.tradeToken = clean(tradeToken);
        this.sid = clean(sid);
        this.baseUrl = trimSlash(clean(baseUrl));
        this.serverId = clean(serverId);
        this.createdAt = createdAt;
    }

    public boolean isComplete() {
        return !tradeToken.isEmpty() && !sid.isEmpty() && !baseUrl.isEmpty();
    }

    JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("tradeToken", tradeToken);
        o.put("sid", sid);
        o.put("baseUrl", baseUrl);
        o.put("serverId", serverId);
        o.put("createdAt", createdAt);
        return o;
    }

    static KotakNeoSession fromJson(JSONObject o) {
        return new KotakNeoSession(
                o.optString("tradeToken", ""),
                o.optString("sid", ""),
                o.optString("baseUrl", ""),
                o.optString("serverId", ""),
                o.optLong("createdAt", 0));
    }

    private static String clean(String s) { return s == null ? "" : s.trim(); }
    private static String trimSlash(String s) {
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
