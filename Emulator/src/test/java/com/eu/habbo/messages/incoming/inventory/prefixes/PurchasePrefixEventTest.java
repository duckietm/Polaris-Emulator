package com.eu.habbo.messages.incoming.inventory.prefixes;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class PurchasePrefixEventTest {

    @Test
    void customPrefixShapeRulesRetainLegacyBehavior() throws Exception {
        PurchasePrefixEvent event = new PurchasePrefixEvent();
        Method containsControlChars =
                PurchasePrefixEvent.class.getDeclaredMethod("containsControlChars", String.class);
        Method isValidIcon = PurchasePrefixEvent.class.getDeclaredMethod("isValidIcon", String.class);
        Method isAllowedFont = PurchasePrefixEvent.class.getDeclaredMethod("isAllowedFont", String.class);
        Method isAllowedEffect = PurchasePrefixEvent.class.getDeclaredMethod("isAllowedEffect", String.class);
        for (Method method : new Method[] {containsControlChars, isValidIcon, isAllowedFont, isAllowedEffect}) {
            method.setAccessible(true);
        }

        assertAll(
                () -> assertFalse((boolean) containsControlChars.invoke(event, "safe prefix")),
                () -> assertTrue((boolean) containsControlChars.invoke(event, "bad\nprefix")),
                () -> assertTrue((boolean) isValidIcon.invoke(event, "star_2.gif")),
                () -> assertFalse((boolean) isValidIcon.invoke(event, "<script>")),
                () -> assertTrue((boolean) isAllowedFont.invoke(event, "pixel")),
                () -> assertFalse((boolean) isAllowedFont.invoke(event, "unknown")),
                () -> assertTrue((boolean) isAllowedEffect.invoke(event, "rainbow")),
                () -> assertFalse((boolean) isAllowedEffect.invoke(event, "unknown")));
    }
}
