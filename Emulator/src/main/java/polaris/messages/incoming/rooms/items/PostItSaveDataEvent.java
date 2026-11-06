package polaris.messages.incoming.rooms.items;

import java.util.Locale;
import polaris.Emulator;
import polaris.hotel.items.PostItColor;
import polaris.hotel.items.interactions.InteractionPostIt;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

public class PostItSaveDataEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 100;
    }

    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        String color = this.packet.readString().toUpperCase(Locale.ROOT);
        String text = Emulator.getGameEnvironment()
                .getWordFilter()
                .filter(this.packet.readString().replace(((char) 9) + "", ""), this.client.getUser());

        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        if (text.length() > Emulator.getConfig().getInt("postit.charlimit")) {
            ScripterManager.scripterDetected(
                    this.client,
                    Emulator.getTexts()
                            .getValue("scripter.warning.sticky.size")
                            .replace(
                                    "%username%",
                                    this.client.getUser().getUserInfo().getUsername())
                            .replace("%amount%", text.length() + "")
                            .replace("%limit%", Emulator.getConfig().getInt("postit.charlimit") + ""));
            return;
        }

        if (PostItColor.isCustomColor(color)) {
            return;
        }

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        UserItem item = room.getUserItem(itemId);

        if (!(item instanceof InteractionPostIt)) return;

        // Rights in the room, or the note is the visitor's own: a visitor never edits someone else's note.
        if (!room.hasRights(this.client.getUser())
                && item.getUserId() != this.client.getUser().getUserInfo().getId()) return;

        if (color.isEmpty()) color = PostItColor.YELLOW.hexColor;

        // Removed on Oct 15th, 2024: The owner of this item should not be altered when editing the text of a post-it.
        // The original owner must always remain unchanged.
        // item.setUserId(room.getOwnerId());
        item.setExtradata(color + " " + text);
        item.needsUpdate(true);
        room.updateItem(item);
        Emulator.getThreading().run(item);
    }
}
