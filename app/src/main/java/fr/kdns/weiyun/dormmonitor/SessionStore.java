package fr.kdns.weiyun.dormmonitor;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SessionStore {
    private static final String ALIAS = "dorm_session_key";
    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }
    public static void save(Context context, String cookie) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        context.getSharedPreferences("secure_session", 0).edit()
            .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
            .putString("cipher", Base64.encodeToString(cipher.doFinal(cookie.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP)).apply();
    }
    public static String get(Context context) {
        try {
            android.content.SharedPreferences p = context.getSharedPreferences("secure_session", 0);
            String iv = p.getString("iv", null), value = p.getString("cipher", null);
            if (iv == null || value == null) return "";
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(value, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }
    public static void clear(Context context) { context.getSharedPreferences("secure_session", 0).edit().clear().apply(); }
}
