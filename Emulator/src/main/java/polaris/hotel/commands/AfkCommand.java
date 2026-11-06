package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;

public class AfkCommand extends Command {

    private static final String DEFAULT_KEYS = "afk;absent;away";
    private static final String DEFAULT_AWAY =
            "You are now away. When you send a message you will automaticly will be back.";
    private static final String DEFAULT_BACK = "Welcome back!";

    public AfkCommand() {
        super(
                null,
                Emulator.getTexts()
                        .getValueQuietly("commands.keys.cmd_afk", DEFAULT_KEYS)
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        User user = gameClient.getUser();

        if (user == null || user.getRoomUnit() == null) {
            return true;
        }

        Room room = user.getUserInfo().getCurrentRoom();

        if (room == null) {
            return true;
        }

        if (user.getRoomUnit().isIdle()) {
            room.unIdle(user);
            user.whisper(
                    Emulator.getTexts().getValueQuietly("commands.generic.cmd_afk.back", DEFAULT_BACK),
                    RoomChatMessageBubbles.NORMAL);
        } else {
            room.idle(user);
            user.whisper(
                    Emulator.getTexts().getValueQuietly("commands.generic.cmd_afk.away", DEFAULT_AWAY),
                    RoomChatMessageBubbles.NORMAL);
        }

        return true;
    }
}
