package com.eu.habbo.networking.gameserver.cms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.core.config.ConfigRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Guards that the CMS API stays mounted and its bootstrap settings stay declared.
 * These are cheap insurance against the handler being unplugged from the pipeline
 * or the config.ini toggles losing their typed registration during a refactor.
 *
 * <p>Only {@code cms.api.enabled} and {@code cms.api.allowed} live in config.ini;
 * the keys and tuning live in the {@code emulator_api} table (see
 * {@link CmsApiSettingsContractTest}).
 */
class CmsApiWiringContractTest {

    @Test
    void handlerIsMountedInTheGameServerPipeline() throws Exception {
        String initializer = Files.readString(
                Path.of("src/main/java/com/eu/habbo/networking/gameserver/WebSocketChannelInitializer.java"));
        assertTrue(
                initializer.contains("new CmsApiHandler()"), "CmsApiHandler must be added to the game server pipeline");
    }

    @Test
    void bootstrapKeysAreTypedStartupSettings() {
        Set<String> names = ConfigRegistry.standard().names();
        assertTrue(names.contains("cms.api.enabled"), "cms.api.enabled must be a typed startup key");
        assertTrue(names.contains("cms.api.allowed"), "cms.api.allowed must be a typed startup key");
    }

    @Test
    void apiIsDisabledByDefaultInConfigExample() throws Exception {
        String config = Files.readString(Path.of("../config example/config.ini.example"));
        // Ships disabled so upgrading hotels do not suddenly expose a new surface.
        assertTrue(config.contains("cms.api.enabled=false"), "cms.api.enabled must ship disabled in config.ini");
    }

    @Test
    void startupBannerIsLoggedWhenTheWebSocketListenerBinds() throws Exception {
        String gameServer =
                Files.readString(Path.of("src/main/java/com/eu/habbo/networking/gameserver/GameServer.java"));
        assertTrue(
                gameServer.contains("CmsApiHandler.logStartupStatus"),
                "GameServer must log the CMS API startup status when the WebSocket listener binds");
    }

    @Test
    void replayAndRateLimitStateIsSharedAcrossConnections() throws Exception {
        // A CmsApiHandler is created per channel, so the nonce cache and per-IP
        // rate limiters must be static — otherwise a fresh TCP connection resets
        // replay protection and rate limits (each request could reuse a nonce).
        String src =
                Files.readString(Path.of("src/main/java/com/eu/habbo/networking/gameserver/cms/CmsApiHandler.java"));
        assertTrue(
                src.contains("static final AtomicReference<AuthSnapshot>"),
                "Auth snapshot (nonce cache) must be shared statically across connections");
        assertTrue(
                src.contains("static final LoadingCache<String, RateLimiter>"),
                "Rate limiters must be shared statically across connections");
    }
}
