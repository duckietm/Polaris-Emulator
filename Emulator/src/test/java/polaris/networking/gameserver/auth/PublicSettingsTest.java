package polaris.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import polaris.Emulator;
import polaris.core.ConfigurationManager;

class PublicSettingsTest {

    @TempDir
    Path temporaryDirectory;

    private Object previousConfig;

    private void useConfig(String settings) throws Exception {
        Field configField = Emulator.class.getDeclaredField("config");
        configField.setAccessible(true);
        this.previousConfig = configField.get(null);
        Path configFile = this.temporaryDirectory.resolve("config.ini");
        Files.writeString(configFile, settings);
        configField.set(null, new ConfigurationManager(configFile.toString()));
    }

    @AfterEach
    void restoreConfig() throws Exception {
        Field configField = Emulator.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(null, this.previousConfig);
    }

    @Test
    void tellsTheLoginPageWhetherTurnstileIsOnAndItsPublicKey() throws Exception {
        useConfig(
                "login.turnstile.enabled=1\nlogin.turnstile.sitekey=site-key\nlogin.turnstile.secretkey=secret-key\n");

        JsonObject turnstile = AuthHttpHandler.publicSettings().getAsJsonObject("turnstile");

        assertTrue(turnstile.get("enabled").getAsBoolean());
        assertEquals("site-key", turnstile.get("sitekey").getAsString());
    }

    @Test
    void neverCarriesTheSecretKey() throws Exception {
        useConfig(
                "login.turnstile.enabled=1\nlogin.turnstile.sitekey=site-key\nlogin.turnstile.secretkey=secret-key\n");

        String payload = AuthHttpHandler.publicSettings().toString();

        assertFalse(payload.contains("secret-key"), payload);
        assertFalse(payload.contains("secret"), payload);
    }

    @Test
    void isOffWithAnEmptyKeyWhenTheHotelSetsNothing() throws Exception {
        useConfig("");

        JsonObject turnstile = AuthHttpHandler.publicSettings().getAsJsonObject("turnstile");

        assertFalse(turnstile.get("enabled").getAsBoolean());
        assertEquals("", turnstile.get("sitekey").getAsString());
    }
}
