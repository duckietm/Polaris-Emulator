package com.eu.habbo.habbohotel.translations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.messages.incoming.translation.TranslationTextRequestEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Each translation was a blocking HTTP call on the shared runtime pool, with no rate limit, no
 * length limit and no switch: a slow endpoint and a client sending new text held every thread that
 * drives rooms and games.
 */
class TranslationRequestBoundsTest {

    /** A manager whose outbound call waits until released, like a slow endpoint. */
    private static final class SlowManager extends GoogleTranslateManager {
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicReference<String> thread = new AtomicReference<>();

        @Override
        public TranslationResponse translate(String text, String targetLanguage) {
            this.thread.set(Thread.currentThread().getName());
            try {
                this.release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return TranslationResponse.success(text, text, "", targetLanguage);
        }
    }

    private static MockedStatic<Emulator> config(boolean enabled) {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getBoolean(anyString(), anyBoolean())).thenReturn(enabled);
        MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getConfig).thenReturn(config);
        return emulator;
    }

    @Test
    void aDisabledFeatureAnswersAtOnce() {
        SlowManager manager = new SlowManager();
        List<GoogleTranslateManager.TranslationResponse> replies = new CopyOnWriteArrayList<>();

        try (MockedStatic<Emulator> ignored = config(false)) {
            manager.translateAsync("hello", "it", replies::add);
        }

        assertEquals(1, replies.size());
        assertFalse(replies.getFirst().isSuccess());
        assertEquals(null, manager.thread.get(), "no call goes out");
    }

    @Test
    void aLongTextIsRefused() {
        SlowManager manager = new SlowManager();
        List<GoogleTranslateManager.TranslationResponse> replies = new CopyOnWriteArrayList<>();

        try (MockedStatic<Emulator> ignored = config(true)) {
            manager.translateAsync("x".repeat(GoogleTranslateManager.MAX_TEXT_LENGTH + 1), "it", replies::add);
        }

        assertFalse(replies.getFirst().isSuccess());
        assertEquals(null, manager.thread.get());
    }

    @Test
    void callsRunOnTheirOwnBoundedPool() throws Exception {
        SlowManager manager = new SlowManager();
        List<GoogleTranslateManager.TranslationResponse> replies = new CopyOnWriteArrayList<>();

        try (MockedStatic<Emulator> ignored = config(true)) {
            for (int i = 0; i < 100; i++) {
                manager.translateAsync("text " + i, "it", replies::add);
            }
        }

        long refused = replies.stream().filter(reply -> !reply.isSuccess()).count();
        assertTrue(refused >= 100 - 2 - 32, "beyond two running and 32 waiting, a request is refused: " + refused);

        manager.release.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (replies.size() < 100 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(100, replies.size(), "every request is answered");
        assertTrue(manager.thread.get().startsWith("Polaris-Translate-"), manager.thread.get());
    }

    @Test
    void theRequestPacketIsRateLimited() {
        assertTrue(new TranslationTextRequestEvent().getRatelimit() > 0);
    }
}
