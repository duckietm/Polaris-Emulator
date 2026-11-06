package polaris.messages.incoming.rooms.items;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.commands.CommandHandler;
import polaris.hotel.items.PostItColor;
import polaris.hotel.items.interactions.InteractionPostIt;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

public class SavePostItStickyPoleEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SavePostItStickyPoleEvent.class);

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        this.packet.readString();
        String color = this.packet.readString().toUpperCase(java.util.Locale.ROOT);
        if (itemId == -1234) {
            if (this.client.getUser().hasPermission("cmd_multi")) {
                String[] commands = this.packet.readString().split("\r");
                if (commands.length > RoomItemInputGuard.MAX_STICKY_POLE_COMMANDS) return;

                for (String command : commands) {
                    command = RoomItemInputGuard.trimToMax(
                            command.replace("<br>", "\r"), RoomItemInputGuard.MAX_STICKY_POLE_COMMAND_LENGTH);
                    if (command.isEmpty()) continue;

                    CommandHandler.handleCommand(this.client, command);
                }
            } else {
                LOGGER.info(
                        "Scripter Alert! {} | {}",
                        this.client.getUser().getUserInfo().getUsername(),
                        this.packet.readString().replace('\r', ' ').replace('\n', ' '));
            }
        } else {
            String text = Emulator.getGameEnvironment()
                    .getWordFilter()
                    .filter(this.packet.readString().replace(((char) 9) + "", ""), this.client.getUser());

            if (text.length() > Emulator.getConfig().getInt("postit.charlimit")) return;

            if (!RoomItemInputGuard.isPositiveId(itemId)) return;

            Room room = this.client.getUser().getUserInfo().getCurrentRoom();
            if (room == null) return;

            UserItem sticky = room.getUserItem(itemId);

            if (sticky instanceof InteractionPostIt) {
                if (sticky.getUserId() == this.client.getUser().getUserInfo().getId()) {
                    sticky.setUserId(room.getOwnerId());

                    if (PostItColor.isCustomColor(color) || color.equalsIgnoreCase(PostItColor.YELLOW.hexColor)) {
                        color = PostItColor.randomColorNotYellow().hexColor;
                    }
                    if (!InteractionPostIt.STICKYPOLE_PREFIX_TEXT.isEmpty()) {
                        text = InteractionPostIt.STICKYPOLE_PREFIX_TEXT
                                        .replace("\\r", "\r")
                                        .replace(
                                                "%username%",
                                                this.client
                                                        .getUser()
                                                        .getUserInfo()
                                                        .getUsername())
                                        .replace("%timestamp%", LocalDate.now().toString())
                                + text;
                    }

                    sticky.setUserId(room.getOwnerId());
                    sticky.setExtradata(color + " " + text);
                    sticky.needsUpdate(true);
                    room.updateItem(sticky);
                    Emulator.getThreading().run(sticky);
                }
            }
        }
    }
}
