package com.eu.habbo.habbohotel.modtool;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WordFilterReloadTest {
    @Test
    void reloadPublishesNewSetsInsteadOfClearingTheOnesOtherThreadsRead() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/modtool/WordFilter.java"));
        int reload = source.indexOf("public synchronized void reload()");
        int end = source.indexOf("public String normalise", reload);
        String body = source.substring(reload, end);

        assertFalse(body.contains(".clear()"), "clearing in place leaves the filter empty during the query");
        assertTrue(body.contains("this.words = all;"));
        assertTrue(body.contains("this.autoReportWords = autoReport;"));
        assertTrue(body.contains("this.hideMessageWords = hideMessage;"));
    }

    @Test
    void theSetsAreVolatileSoAReloadIsSeenByEveryThread() throws Exception {
        for (String name : new String[] {"words", "autoReportWords", "hideMessageWords"}) {
            Field field = WordFilter.class.getDeclaredField(name);

            assertTrue(Modifier.isVolatile(field.getModifiers()), name + " must be volatile");
        }
    }
}
