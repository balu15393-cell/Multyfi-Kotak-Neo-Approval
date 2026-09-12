package com.example.multyfikotakneo;

import android.content.Context;
import org.json.JSONObject;

public final class KotakNeoCredentialStore {
    private final SecureStore secure;

    public KotakNeoCredentialStore(Context context) {
        secure = new SecureStore(context, "kotak_neo_credentials", "kotak_neo_credentials_key_v2");
    }

    public void save(KotakNeoCredentials credentials) throws Exception {
        if (credentials == null || !credentials.isComplete()) {
            throw new IllegalArgumentException("Kotak Neo API token/consumer key, mobile number and UCC are required.");
        }
        secure.save(credentials.toJson().toString());
    }

    public KotakNeoCredentials load() throws Exception {
        String raw = secure.load();
        if (raw.isEmpty()) return new KotakNeoCredentials("", "", "");
        return KotakNeoCredentials.fromJson(new JSONObject(raw));
    }

    public boolean hasCredentials() { return secure.hasValue(); }
    public void clear() { secure.clear(); }
}
