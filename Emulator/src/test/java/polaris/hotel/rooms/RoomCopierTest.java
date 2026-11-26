package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoomCopierTest {
    @Test
    void theCopyTakesTheSettingsAndLeavesWhatTheOriginalEarned() {
        List<String> columns = List.of(
                "id",
                "owner_id",
                "owner_name",
                "name",
                "model",
                "users",
                "guild_id",
                "score",
                "is_public",
                "is_staff_picked",
                "chat_mode",
                "promoted",
                "roller_speed",
                "is_forsale",
                "date_created",
                "some_column_a_hotel_added");

        assertEquals(
                List.of("name", "model", "chat_mode", "roller_speed", "some_column_a_hotel_added"),
                RoomCopier.copiedColumns(columns, RoomCopier.ROOM_COLUMNS_NOT_COPIED));
    }

    @Test
    void aBotKeepsEverythingButItsIdentity() {
        assertEquals(
                List.of("name", "figure", "bubble_id"),
                RoomCopier.copiedColumns(
                        List.of("id", "user_id", "room_id", "name", "figure", "bubble_id"),
                        RoomCopier.BOT_COLUMNS_NOT_COPIED));
    }

    @Test
    void aColumnNameThatIsNotAPlainNameIsLeftOut() {
        assertEquals(
                List.of("name"),
                RoomCopier.copiedColumns(List.of("name", "bad`name", "x; DROP"), RoomCopier.BOT_COLUMNS_NOT_COPIED));
        assertEquals("`a`, `b`", RoomCopier.quoted(List.of("a", "b")));
    }
}
