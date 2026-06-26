package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.rooms.RoomChatType;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.plugin.events.users.UserTalkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoomUserWhisperEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomUserWhisperEvent.class);

    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() == null)
            return;

        var chatMessage = new RoomChatMessage(this);

        if (chatMessage.getMessage().length() <= RoomChatMessage.MAXIMUM_LENGTH) {
            if (!this.client.getHabbo().getHabboStats().allowTalk() || chatMessage.getTargetHabbo() == null)
                return;

            if (Emulator.getPluginManager().fireEvent(new UserTalkEvent(this.client.getHabbo(), chatMessage, RoomChatType.WHISPER)).isCancelled()) {
                return;
            }

            this.client.getHabbo().getHabboInfo().getCurrentRoom().talk(this.client.getHabbo(), chatMessage, RoomChatType.WHISPER, true);

            if (RoomChatMessage.SAVE_ROOM_CHATS) {
                Emulator.getThreading().run(chatMessage);
            }
        } else {
            String reportMessage = Emulator.getTexts().getValue("scripter.warning.chat.length").replace("%username%", this.client.getHabbo().getHabboInfo().getUsername()).replace("%length%", chatMessage.getMessage().length() + "");
            ScripterManager.scripterDetected(this.client, reportMessage);
            LOGGER.info(reportMessage);
        }
    }
}
