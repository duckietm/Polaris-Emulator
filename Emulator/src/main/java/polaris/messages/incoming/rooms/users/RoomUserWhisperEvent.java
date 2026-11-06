package polaris.messages.incoming.rooms.users;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomChatType;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.events.users.UserTalkEvent;

public class RoomUserWhisperEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomUserWhisperEvent.class);

    @Override
    public void handle() throws Exception {
        if (this.client.getUser().getUserInfo().getCurrentRoom() == null) return;

        RoomChatMessage chatMessage = new RoomChatMessage(this);

        if (chatMessage.getMessage().length() <= RoomChatMessage.MAXIMUM_LENGTH) {
            if (!this.client.getUser().getUserStats().allowTalk() || chatMessage.getTargetUser() == null) return;

            if (Emulator.getPluginManager()
                    .fireEvent(new UserTalkEvent(this.client.getUser(), chatMessage, RoomChatType.WHISPER))
                    .isCancelled()) {
                return;
            }

            this.client
                    .getUser()
                    .getUserInfo()
                    .getCurrentRoom()
                    .talk(this.client.getUser(), chatMessage, RoomChatType.WHISPER, true);

            if (RoomChatMessage.SAVE_ROOM_CHATS) {
                Emulator.getThreading().run(chatMessage);
            }
        } else {
            String reportMessage = Emulator.getTexts()
                    .getValue("scripter.warning.chat.length")
                    .replace("%username%", this.client.getUser().getUserInfo().getUsername())
                    .replace("%length%", chatMessage.getMessage().length() + "");
            ScripterManager.scripterDetected(this.client, reportMessage);
            LOGGER.info(reportMessage);
        }
    }
}
