package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import com.eu.habbo.database.SqlQueries;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Converting a room name to an id ran an unescaped LIKE ('%' or '_' scanned the whole table), built and
 * registered up to 25 rooms per request, and answered with invisible rooms as well.
 */
class RoomNameLookupTest {

    @Test
    void likeMetacharactersMatchThemselves() {
        assertEquals("100\\%", RoomManager.escapeLike("100%"));
        assertEquals("a\\_b", RoomManager.escapeLike("a_b"));
        assertEquals("back\\\\slash", RoomManager.escapeLike("back\\slash"));
        assertEquals("plain", RoomManager.escapeLike("plain"));
    }

    @Test
    void theIdOfAVisibleRoomIsReadWithoutLoadingIt() {
        AtomicReference<String> sql = new AtomicReference<>();

        try (MockedStatic<SqlQueries> queries = mockStatic(SqlQueries.class)) {
            queries.when(() -> SqlQueries.queryOne(anyString(), any(), eq("Lobby")))
                    .thenAnswer(invocation -> {
                        sql.set(invocation.getArgument(0));
                        return Optional.of(77);
                    });

            assertEquals(77, new RoomManager(false).findVisibleRoomIdByName("Lobby"));
        }

        assertTrue(sql.get().contains("SELECT id FROM rooms WHERE name = ? AND state <> 'invisible'"), sql.get());
    }

    @Test
    void theNavigatorAsksForTheIdOnly() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/navigator/ConvertGlobalRoomIdEvent.java"));

        assertTrue(source.contains("findVisibleRoomIdByName(target)"));
        assertFalse(source.contains("getRoomsWithName("), "no rooms are built for a name lookup");
    }
}
