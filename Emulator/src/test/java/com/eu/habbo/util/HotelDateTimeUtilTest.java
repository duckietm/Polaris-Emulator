package com.eu.habbo.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HotelDateTimeUtilTest {

    @Test
    void strictTimestampParserRejectsLenientAndTrailingInput() throws Exception {
        Method parser =
                HotelDateTimeUtil.class.getDeclaredMethod(
                        "parseDateTimeStrict", String.class);

        assertEquals(
                LocalDateTime.of(2024, 2, 29, 1, 2, 3),
                parser.invoke(null, "2024-02-29 01:02:03"));

        assertParseFailure(parser, "2024-02-30 01:02:03");
        assertParseFailure(parser, "2024-02-29 01:02:03 trailing");
    }

    private static void assertParseFailure(Method parser, String value) {
        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> parser.invoke(null, value));
        assertInstanceOf(DateTimeParseException.class, exception.getCause());
    }
}
