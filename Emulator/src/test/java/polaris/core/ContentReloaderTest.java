package polaris.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContentReloaderTest {

    @Test
    void runsTheNamedTargetWhateverItsSpelling() {
        List<String> ran = new ArrayList<>();
        ContentReloader reloader =
                new ContentReloader(Map.of("petdata", () -> ran.add("pets"), "dailytasks", () -> ran.add("tasks")));

        assertTrue(reloader.reload("Pet_Data"));
        assertTrue(reloader.reload("daily tasks"));
        assertEquals(List.of("pets", "tasks"), ran);
    }

    @Test
    void refusesAnUnknownOrMissingTarget() {
        ContentReloader reloader = new ContentReloader(Map.of("polls", () -> {}));

        assertFalse(reloader.reload("catalog"));
        assertFalse(reloader.reload(null));
        assertFalse(reloader.has(""));
    }

    @Test
    void listsItsTargetsSorted() {
        ContentReloader reloader =
                new ContentReloader(Map.of("polls", () -> {}, "achievements", () -> {}, "navigator", () -> {}));

        assertEquals(List.of("achievements", "navigator", "polls"), List.copyOf(reloader.targets()));
    }
}
