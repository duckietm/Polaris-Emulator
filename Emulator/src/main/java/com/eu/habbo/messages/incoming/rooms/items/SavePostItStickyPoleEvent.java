package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.CommandHandler;
import com.eu.habbo.habbohotel.items.PostItColor;
import com.eu.habbo.habbohotel.items.interactions.InteractionPostIt;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

public class SavePostItStickyPoleEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SavePostItStickyPoleEvent.class);

    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();

        this.packet.readString();
        String color = this.packet.readString();
        if (itemId == -1234) {
            if (this.client.getHabbo().hasPermission("cmd_multi")) {
                String[] commands = this.packet.readString().split("\r");
                if (commands.length > RoomItemInputGuard.MAX_STICKY_POLE_COMMANDS)
                    return;

                for (String command : commands) {
                    command = RoomItemInputGuard.trimToMax(command.replace("<br>", "\r"), RoomItemInputGuard.MAX_STICKY_POLE_COMMAND_LENGTH);
                    if (command.isEmpty())
                        continue;

                    CommandHandler.handleCommand(this.client, command);
                }
            } else {
                LOGGER.info("Scripter Alert! {} | {}", this.client.getHabbo().getHabboInfo().getUsername(), this.packet.readString());
            }
        } else {
            String text = Emulator.getGameEnvironment().getWordFilter().filter(this.packet.readString().replace(((char) 9) + "", ""), this.client.getHabbo());

            if (text.length() > Emulator.getConfig().getInt("postit.charlimit"))
                return;

            if (!RoomItemInputGuard.isPositiveId(itemId))
                return;

            Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
            if (room == null)
                return;

            HabboItem sticky = room.getHabboItem(itemId);

            if (sticky instanceof InteractionPostIt) {
                if (sticky.getUserId() == this.client.getHabbo().getHabboInfo().getId()) {
                    sticky.setUserId(room.getOwnerId());

                    if (PostItColor.isCustomColor(color) || color.equalsIgnoreCase(PostItColor.YELLOW.hexColor)) {
                        color = PostItColor.randomColorNotYellow().hexColor;
                    }
                    if (!InteractionPostIt.STICKYPOLE_PREFIX_TEXT.isEmpty()) {
                        text = InteractionPostIt.STICKYPOLE_PREFIX_TEXT.replace("\\r", "\r").replace("%username%", this.client.getHabbo().getHabboInfo().getUsername()).replace("%timestamp%", LocalDate.now().toString()) + text;
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
