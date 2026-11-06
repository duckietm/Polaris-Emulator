package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.rooms.RoomUserAction;
import polaris.hotel.users.DanceType;
import polaris.hotel.users.User;
import polaris.messages.outgoing.rooms.users.RoomUserActionComposer;
import polaris.messages.outgoing.rooms.users.RoomUserDanceComposer;

public class DanceCommand extends Command {
    public DanceCommand() {
        super(
                "cmd_dance",
                Emulator.getTexts().getValue("commands.keys.cmd_dance").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        User user = gameClient.getUser();
        if (user == null || user.getUserInfo().getCurrentRoom() == null) {
            return true;
        }

        if (params.length == 2) {
            int danceId;

            try {
                danceId = Integer.parseInt(params[1]);
            } catch (NumberFormatException e) {
                user.whisper(
                        Emulator.getTexts().getValue("commands.error.cmd_dance.invalid_dance"),
                        RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (danceId == RoomUserAction.SIX_SEVEN.getAction()) {
                sixSeven(user);
                return true;
            }

            if (danceId < 0 || danceId > 4) {
                user.whisper(
                        Emulator.getTexts().getValue("commands.error.cmd_dance.outside_bounds"),
                        RoomChatMessageBubbles.ALERT);
                return true;
            }

            user.getRoomUnit().setDanceType(DanceType.values()[danceId]);
            user.getUserInfo().getCurrentRoom().sendComposer(new RoomUserDanceComposer(user.getRoomUnit()).compose());

            String danceName = danceId == 0 ? "stop" : "dance " + danceId;
            user.whisper("You are now doing " + danceName + "!", RoomChatMessageBubbles.NORMAL);
        } else {
            user.whisper(Emulator.getTexts().getValue("commands.error.cmd_dance.usage"), RoomChatMessageBubbles.ALERT);
        }

        return true;
    }

    // :dance 67 plays the one-shot six-seven move; a running dance would hide it.
    private static void sixSeven(User user) {
        Room room = user.getUserInfo().getCurrentRoom();

        if (user.getRoomUnit().getDanceType() != DanceType.NONE) {
            user.getRoomUnit().setDanceType(DanceType.NONE);
            room.sendComposer(new RoomUserDanceComposer(user.getRoomUnit()).compose());
        }

        room.sendComposer(new RoomUserActionComposer(user.getRoomUnit(), RoomUserAction.SIX_SEVEN).compose());
    }
}
