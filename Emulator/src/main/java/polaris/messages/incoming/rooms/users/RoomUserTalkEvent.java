package polaris.messages.incoming.rooms.users;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomChatType;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.events.users.UserTalkEvent;

public class RoomUserTalkEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomUserTalkEvent.class);

    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null) return;

        if (!this.client.getUser().getUserStats().allowTalk()) return;

        RoomChatMessage message = new RoomChatMessage(this);

        if (message.getMessage().length() <= RoomChatMessage.MAXIMUM_LENGTH) {
            if (Emulator.getPluginManager()
                    .fireEvent(new UserTalkEvent(this.client.getUser(), message, RoomChatType.TALK))
                    .isCancelled()) {
                return;
            }

            room.talk(this.client.getUser(), message, RoomChatType.TALK);

            if (!message.isCommand) {
                if (RoomChatMessage.SAVE_ROOM_CHATS) {
                    Emulator.getThreading().run(message);
                }

                // A line the room refused (muted, flooding, in a mute area) mentions nobody.
                if (message.delivered) {
                    Emulator.getGameEnvironment()
                            .getMentionManager()
                            .process(this.client.getUser(), room, message.getMessage(), RoomChatType.TALK);
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
