package com.eu.habbo.messages.incoming.inventory.nickicons;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class PurchaseNickIconEventTest {

    @Test
    void iconKeysRetainLegacyNormalizationAndValidation() throws Exception {
        PurchaseNickIconEvent event = new PurchaseNickIconEvent();
        Method normalize = PurchaseNickIconEvent.class.getDeclaredMethod("normalizeIconKey", String.class);
        normalize.setAccessible(true);

        assertAll(
                () -> assertEquals("sparkle", normalize.invoke(event, " Sparkle.GIF ")),
                () -> assertEquals("blue_icon-2", normalize.invoke(event, "blue_icon-2")),
                () -> assertEquals("", normalize.invoke(event, "../sparkle")),
                () -> assertEquals("", normalize.invoke(event, new Object[] {null})));
    }
}
