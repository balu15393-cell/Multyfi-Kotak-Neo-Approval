package com.example.multyfikotakneo;

import android.content.Context;
import org.json.JSONObject;

public final class KotakNeoSessionStore {
    private final SecureStore secure;

    public KotakNeoSessionStore(Context context) {
        secure = new SecureStore(context, "kotak_neo_session", "kotak_neo_session_key_v2");
    }

    public void save(KotakNeoSession session) throws Exception {
        if (session == null || !session.isComplete()) {
            throw new IllegalArgumentException("Kotak Neo session is incomplete.");
        }
        secure.save(session.toJson().toString());
    }

    public KotakNeoSession load() throws Exception {
        String raw = secure.load();
        if (raw.isEmpty()) return null;
        KotakNeoSession s = KotakNeoSession.fromJson(new JSONObject(raw));
        return s.isComplete() ? s : null;
    }

    public boolean hasSession() { return secure.hasValue(); }
    public void clear() { secure.clear(); }
}
