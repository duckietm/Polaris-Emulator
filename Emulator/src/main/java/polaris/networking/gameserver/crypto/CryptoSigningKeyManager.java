package polaris.networking.gameserver.crypto;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;

public final class CryptoSigningKeyManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CryptoSigningKeyManager.class);
    private static final String KEY_PUBLIC = "crypto.ws.signing.public_key";
    private static final String KEY_PRIVATE = "crypto.ws.signing.private_key";
    private static volatile KeyPair cached;
    private static volatile String cachedPublicB64;

    private CryptoSigningKeyManager() {}

    public static KeyPair get() {
        KeyPair kp = cached;
        if (kp != null) return kp;

        synchronized (CryptoSigningKeyManager.class) {
            if (cached != null) return cached;

            String pubB64 = Emulator.getConfig().getValue(KEY_PUBLIC, "");
            String privB64 = Emulator.getConfig().getValue(KEY_PRIVATE, "");

            if (pubB64 != null && !pubB64.isEmpty() && privB64 != null && !privB64.isEmpty()) {
                try {
                    byte[] pubDer = Base64.getDecoder().decode(pubB64);
                    byte[] privDer = Base64.getDecoder().decode(privB64);
                    KeyFactory kf = KeyFactory.getInstance("EC");
                    PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(pubDer));
                    PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(privDer));
                    cached = new KeyPair(pub, priv);
                    cachedPublicB64 = pubB64;
                    return cached;
                } catch (Exception e) {
                    LOGGER.error("[ws-crypto] persisted signing key is corrupt, generating a new pair", e);
                }
            }

            try {
                KeyPair generated = WsSessionCrypto.generateSigningKeyPair();
                byte[] pubDer = WsSessionCrypto.encodePublicKeySpki(generated.getPublic());
                byte[] privDer = WsSessionCrypto.encodePrivateKeyPkcs8(generated.getPrivate());
                String newPubB64 = Base64.getEncoder().withoutPadding().encodeToString(pubDer);
                String newPrivB64 = Base64.getEncoder().withoutPadding().encodeToString(privDer);

                persist(KEY_PUBLIC, newPubB64);
                persist(KEY_PRIVATE, newPrivB64);
                Emulator.getConfig().update(KEY_PUBLIC, newPubB64);
                Emulator.getConfig().update(KEY_PRIVATE, newPrivB64);

                cached = generated;
                cachedPublicB64 = newPubB64;
                LOGGER.info("[ws-crypto] generated a new ECDSA P-256 signing keypair (persisted to emulator_settings)");
                return cached;
            } catch (Exception e) {
                throw new IllegalStateException("Cannot generate signing keypair", e);
            }
        }
    }

    public static String publicKeyBase64() {
        if (cachedPublicB64 == null) get();
        return cachedPublicB64;
    }

    private static void persist(String key, String value) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement("INSERT INTO emulator_settings (`key`, `value`) VALUES (?, ?) "
                                + "ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)")) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.executeUpdate();
        } catch (Exception e) {
            LOGGER.error(
                    "[ws-crypto] failed to persist " + key + " to emulator_settings (key stays in-memory only)", e);
        }
    }
}
