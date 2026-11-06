package polaris.messages.incoming.rooms.users;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomChatType;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.events.users.UserTalkEvent;

public class RoomUserShoutEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomUserShoutEvent.class);

    @Override
    public void handle() throws Exception {
        if (this.client.getUser().getUserInfo().getCurrentRoom() == null) return;

        if (!this.client.getUser().getUserStats().allowTalk()) return;

        RoomChatMessage message = new RoomChatMessage(this);

        if (message.getMessage().length() <= RoomChatMessage.MAXIMUM_LENGTH) {
            if (Emulator.getPluginManager()
                    .fireEvent(new UserTalkEvent(this.client.getUser(), message, RoomChatType.SHOUT))
                    .isCancelled()) {
                return;
            }

            this.client
                    .getUser()
                    .getUserInfo()
                    .getCurrentRoom()
                    .talk(this.client.getUser(), message, RoomChatType.SHOUT);

            if (!message.isCommand) {
                if (RoomChatMessage.SAVE_ROOM_CHATS) {
                    Emulator.getThreading().run(message);
                }

                // A line the room refused (muted, flooding, in a mute area) mentions nobody.
                if (message.delivered) {
                    Emulator.getGameEnvironment()
                            .getMentionManager()
                            .process(
                                    this.client.getUser(),
                                    this.client.getUser().getUserInfo().getCurrentRoom(),
                                    message.getMessage(),
                                    RoomChatType.SHOUT);
                }
            }
        } else {
            String reportMessage = Emulator.getTexts()
                    .getValue("scripter.warning.chat.length")
                    .replace("%username%", this.client.getUser().getUserInfo().getUsername())
                    .replace("%length%", message.getMessage().length() + "");
            ScripterManager.scripterDetected(this.client, reportMessage);
            LOGGER.info(reportMessage);
        }
    }
}
