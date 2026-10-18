package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSaveRefusedException;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Users By Name takes at most 20 names and 1000 characters, as the official editor. */
class WiredEffectUsersByNameLimitTest {

    private static String names(int count) {
        return IntStream.range(0, count).mapToObj(i -> "user" + i).collect(Collectors.joining("\n"));
    }

    private static void save(String text) {
        new WiredEffectUsersByName(1, 1, mock(Item.class), "", 0, 0)
                .saveData(new WiredSettings(new int[0], text, new int[0], 0), null);
    }

    @Test
    void twentyNamesSave() {
        assertDoesNotThrow(() -> save(names(20)));
    }

    @Test
    void twentyOneNamesAreRefused() {
        WiredSaveRefusedException refused = assertThrows(WiredSaveRefusedException.class, () -> save(names(21)));
        assertEquals("wiredfurni.error.usernames_count", refused.getMessage());
        assertEquals(java.util.Map.of("max_usernames", "20"), refused.getParameters());
    }

    @Test
    void moreThanAThousandCharactersAreRefused() {
        WiredSaveRefusedException refused = assertThrows(WiredSaveRefusedException.class, () -> save("a".repeat(1001)));
        assertEquals("wiredfurni.error.too_much_text", refused.getMessage());
        assertEquals(java.util.Map.of("limit", "1000"), refused.getParameters());
    }
}
