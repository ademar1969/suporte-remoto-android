package com.remoto.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.TextUtils;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Preferencias do app: servidor, nome do aparelho, IDs acessados, apelidos e senhas salvas. */
final class Store {
    static final int MAX_RECENTS = 30;
    private static final String KEY_ALIAS = "remoto_senhas";

    private final SharedPreferences sp;

    Store(Context ctx) {
        sp = ctx.getSharedPreferences("remoto", Context.MODE_PRIVATE);
    }

    /** Mesma regra do cliente desktop: sem caracteres de controle, no maximo 40, nunca vazio. */
    static String clean(String n) {
        String s = n == null ? "" : n.replaceAll("\\p{Cntrl}", "").trim();
        if (s.length() > 40) s = s.substring(0, 40).trim();
        return s.isEmpty() ? "Celular" : s;
    }

    // ---- servidor e nome do aparelho ----
    String host() { return sp.getString("host", ""); }

    int port() { return sp.getInt("port", Protocol.DEFAULT_PORT); }

    void setServer(String host, int port) {
        sp.edit().putString("host", host.trim()).putInt("port", port).apply();
    }

    String deviceName() { return sp.getString("name", clean(Build.MODEL)); }

    void setDeviceName(String name) { sp.edit().putString("name", clean(name)).apply(); }

    // ---- IDs acessados ----
    List<String> recents() {
        List<String> r = new ArrayList<>();
        for (String s : sp.getString("recents", "").split(",")) {
            if (!s.trim().isEmpty()) r.add(s.trim());
        }
        return r;
    }

    void addRecent(String id) {
        List<String> r = recents();
        r.remove(id);
        r.add(0, id);
        while (r.size() > MAX_RECENTS) r.remove(r.size() - 1);
        sp.edit().putString("recents", TextUtils.join(",", r)).apply();
    }

    /** Esquece o ID e tudo que se sabe dele (senha, apelido, nome). */
    void removeRecent(String id) {
        List<String> r = recents();
        r.remove(id);
        sp.edit().putString("recents", TextUtils.join(",", r))
                .remove("pw." + id).remove("nick." + id).remove("name." + id).apply();
    }

    // ---- nomes ----
    String nickname(String id) { return sp.getString("nick." + id, null); }

    void setNickname(String id, String nick) {
        String n = nick == null ? "" : nick.replaceAll("\\p{Cntrl}", "").trim();
        if (n.isEmpty()) sp.edit().remove("nick." + id).apply();
        else sp.edit().putString("nick." + id, clean(n)).apply();
    }

    String knownName(String id) { return sp.getString("name." + id, null); }

    void setKnownName(String id, String name) { sp.edit().putString("name." + id, clean(name)).apply(); }

    /** Apelido, ou o nome que o proprio computador informou, ou null. */
    String displayName(String id) {
        String n = nickname(id);
        return n != null ? n : knownName(id);
    }

    // ---- senhas salvas (AES-GCM com chave no Android Keystore: nao sai do aparelho) ----
    boolean hasPassword(String id) { return sp.contains("pw." + id); }

    String password(String id) {
        String stored = sp.getString("pw." + id, null);
        if (stored == null) return null;
        try {
            String[] parts = stored.split(":");
            if (parts.length != 2) return null;
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] ct = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null; // chave perdida (restauracao de backup, etc.): trata como sem senha
        }
    }

    void setPassword(String id, String password) {
        if (password == null || password.isEmpty()) {
            forgetPassword(id);
            return;
        }
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] ct = c.doFinal(password.getBytes(StandardCharsets.UTF_8));
            String stored = Base64.encodeToString(c.getIV(), Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(ct, Base64.NO_WRAP);
            sp.edit().putString("pw." + id, stored).apply();
        } catch (Exception e) {
            // sem Keystore utilizavel: melhor nao salvar do que salvar sem protecao
        }
    }

    void forgetPassword(String id) { sp.edit().remove("pw." + id).apply(); }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return kg.generateKey();
        }
        return (SecretKey) ks.getKey(KEY_ALIAS, null);
    }
}
