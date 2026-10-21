package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The text a placeholder expands to is capped: a stack that repeats a long value cannot grow a message without
 * bound, and the repeated spaces a player typed survive the trip.
 */
class WiredTextPlaceholderBudgetTest {
    private static final int CAP = intConstant("MAX_PLACEHOLDER_EXPANSION_LENGTH");
    private static final char PRESERVED_SPACE = ' ';

    @Test
    void everyOccurrenceOfATokenIsReplaced() throws Exception {
        assertEquals("Hi Bob and Bob!", replace("Hi $(n) and $(n)!", "$(n)", "Bob"));
    }

    @Test
    void aMissingReplacementRemovesTheToken() throws Exception {
        assertEquals("Hi !", replace("Hi $(n)!", "$(n)", null));
    }

    @Test
    void textWithoutTheTokenComesBackUntouched() throws Exception {
        String text = "nothing to expand";

        assertSame(text, replace(text, "$(n)", "Bob"));
    }

    @Test
    void anEmptyTextOrTokenIsLeftAlone() throws Exception {
        assertEquals("", replace("", "$(n)", "Bob"));
        assertEquals("abc", replace("abc", "", "Bob"));
        assertEquals(null, replace(null, "$(n)", "Bob"));
    }

    @Test
    void aLongValueRepeatedManyTimesNeverPassesTheCap() throws Exception {
        String value = "x".repeat(CAP / 2 + 1);

        String result = replace("$(n)-$(n)-$(n)", "$(n)", value);

        assertEquals(CAP, result.length());
        assertTrue(result.startsWith(value + "-"), "what fits before the cap is kept in order");
    }

    @Test
    void aValueLargerThanTheCapIsCutAtTheCap() throws Exception {
        String result = replace("a$(n)b", "$(n)", "y".repeat(CAP * 2));

        assertEquals(CAP, result.length());
        assertEquals('a', result.charAt(0));
        assertEquals('y', result.charAt(CAP - 1));
    }

    @Test
    void textAfterTheLastTokenIsCutToWhatTheCapLeaves() throws Exception {
        String tail = "t".repeat(CAP);

        String result = replace("$(n)" + tail, "$(n)", "ab");

        assertEquals(CAP, result.length());
        assertTrue(result.startsWith("ab" + "t"));
    }

    @Test
    void aRunOfSpacesKeepsItsLengthAndASingleSpaceStaysASpace() throws Exception {
        String nbsp = String.valueOf(PRESERVED_SPACE);

        assertEquals("a b", spaces("a b"));
        assertEquals("a" + nbsp.repeat(3) + "b", spaces("a   b"));
        assertEquals(nbsp.repeat(2), spaces("  "));
        assertEquals(" ", spaces(" "));
        assertEquals(null, spaces(null));
    }

    @Test
    void theTriggerTextIsTheWholeMessageForUserSaysAndEmptyForOtherEvents() throws Exception {
        WiredContext said = context(WiredEvent.Type.USER_SAYS, Optional.of("hello  world"), Map.of());
        WiredContext other = context(WiredEvent.Type.CUSTOM, Optional.of("hello"), Map.of());

        assertEquals("You said: hello  world", trigger(said, "You said: $(trigger_text)"));
        assertEquals("You said: ", trigger(other, "You said: $(trigger_text)"));
        assertEquals("plain", trigger(said, "plain"));
    }

    @Test
    void aLinkDataframeFillsItsNamedValuesOnly() throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("score", "42");
        values.put("who", "Ann");
        WiredContext dataframe = context(WiredEvent.Type.LINK_DATAFRAME_RECEIVED, Optional.empty(), values);
        WiredContext elsewhere = context(WiredEvent.Type.CUSTOM, Optional.empty(), values);

        assertEquals("Ann scored 42, $(rank)", links(dataframe, "$(who) scored $(score), $(rank)"));
        assertEquals("$(who) scored", links(elsewhere, "$(who) scored"));
    }

    private static WiredContext context(WiredEvent.Type type, Optional<String> text, Map<String, String> linkValues) {
        WiredEvent event = mock(WiredEvent.class);
        when(event.getType()).thenReturn(type);
        when(event.getText()).thenReturn(text);
        when(event.getLinkValues()).thenReturn(linkValues);
        WiredContext context = mock(WiredContext.class);
        when(context.event()).thenReturn(event);
        when(context.eventType()).thenReturn(type);
        return context;
    }

    private static String replace(String input, String token, String replacement) throws Exception {
        return (String) invoke(
                "replaceWithBudget",
                new Class<?>[] {String.class, String.class, String.class},
                input,
                token,
                replacement);
    }

    private static String spaces(String text) throws Exception {
        return (String) invoke("preserveRepeatedSpaces", new Class<?>[] {String.class}, text);
    }

    private static String trigger(WiredContext context, String text) throws Exception {
        return (String) invoke("applyTriggerText", new Class<?>[] {WiredContext.class, String.class}, context, text);
    }

    private static String links(WiredContext context, String text) throws Exception {
        return (String) invoke("applyLinkValues", new Class<?>[] {WiredContext.class, String.class}, context, text);
    }

    private static Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = WiredTextPlaceholderUtil.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static int intConstant(String name) {
        try {
            Field field = WiredTextPlaceholderUtil.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
