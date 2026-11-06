package polaris.messages.incoming.rooms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source contract of the official enforce-category flow: a room created or
 * saved without a usable category triggers RoomCategoryUpdateMessage (3896)
 * after the regular reply, and the dialog answer (1265) is owner-gated and
 * validated before the room is touched.
 */
class EnforceCategoryContractTest {

    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/polaris/messages/incoming/" + path));
    }

    @Test
    void roomSettingsSaveEnforcesACategoryAfterSaving() throws Exception {
        String source = source("rooms/RoomSettingsSaveEvent.java");
        int invalid = source.indexOf("enforceCategory = true");
        int saved = source.indexOf("new RoomSettingsSavedComposer(room)");
        int enforced = source.indexOf("ShowEnforceRoomCategoryDialogComposer.SELECTION_ROOM_SETTINGS");

        assertTrue(invalid > -1, "an unusable category marks the save for enforcement");
        assertTrue(saved > invalid && enforced > saved, "3896 follows the RoomSettingsSaved reply");
    }

    @Test
    void roomCreationFallsBackToACategoryAndEnforcesAChoice() throws Exception {
        String source = source("navigator/RequestCreateRoomEvent.java");
        int fallback = source.indexOf("fallbackCategory(this.client.getUser())");
        int created = source.indexOf("new RoomCreatedComposer(room)");
        int enforced = source.indexOf("ShowEnforceRoomCategoryDialogComposer.SELECTION_ROOM_CREATED");

        assertTrue(fallback > -1, "an unusable category is replaced by a fallback the user may use");
        assertTrue(created > fallback && enforced > created, "3896 follows the RoomCreated reply");
    }

    @Test
    void categoryUpdateChecksOwnerAndInputsBeforeMutatingTheRoom() throws Exception {
        String source = source("rooms/UpdateRoomCategoryAndTradeSettingsEvent.java");
        int owner = source.indexOf("room.isOwner(this.client.getUser())");
        int trade = source.indexOf("tradeMode > MAX_TRADE_MODE");
        int category = source.indexOf("hasCategory(categoryId, this.client.getUser())");
        int mutation = source.indexOf("room.setCategory(categoryId)");

        assertTrue(owner > -1 && trade > owner && category > trade, "owner, trade mode and category are validated");
        assertTrue(mutation > category, "the room is only updated after every check");
        assertTrue(
                source.indexOf("new RoomSettingsSavedComposer(room)") > mutation, "the client gets RoomSettingsSaved");
    }
}
