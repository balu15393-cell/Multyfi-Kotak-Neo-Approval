package com.example.multyfikotakneo;

import org.json.JSONObject;

public final class KotakNeoCredentials {
    public final String consumerKey;
    public final String mobileNumber;
    public final String ucc;

    public KotakNeoCredentials(String consumerKey, String mobileNumber, String ucc) {
        this.consumerKey = consumerKey == null ? "" : consumerKey.trim();
        this.mobileNumber = mobileNumber == null ? "" : mobileNumber.trim();
        this.ucc = ucc == null ? "" : ucc.trim();
    }

    public boolean isComplete() {
        return !consumerKey.isEmpty() && !mobileNumber.isEmpty() && !ucc.isEmpty();
    }

    JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("consumerKey", consumerKey);
        o.put("mobileNumber", mobileNumber);
        o.put("ucc", ucc);
        return o;
    }

    static KotakNeoCredentials fromJson(JSONObject o) {
        return new KotakNeoCredentials(
                o.optString("consumerKey", ""),
                o.optString("mobileNumber", ""),
                o.optString("ucc", ""));
    }
}
