package com.example.multyfikotakneo;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecureStore {
    private static final String K_IV = "iv";
    private static final String K_DATA = "data";

    private final Context context;
    private final String prefsName;
    private final String keyAlias;

    SecureStore(Context context, String prefsName, String keyAlias) {
        this.context = context.getApplicationContext();
        this.prefsName = prefsName;
        this.keyAlias = keyAlias;
    }

    void save(String value) throws Exception {
        if (value == null || value.trim().isEmpty()) {
            clear();
            return;
        }
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()
                .putString(K_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString(K_DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .apply();
    }

    String load() throws Exception {
        SharedPreferences p = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        String iv = p.getString(K_IV, null);
        String data = p.getString(K_DATA, null);
        if (iv == null || data == null) return "";
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
        byte[] plain = cipher.doFinal(Base64.decode(data, Base64.NO_WRAP));
        return new String(plain, StandardCharsets.UTF_8);
    }

    boolean hasValue() {
        return context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).contains(K_DATA);
    }

    void clear() {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(keyAlias)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(keyAlias, null)).getSecretKey();
        }
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return gen.generateKey();
    }
}
