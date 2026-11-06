package polaris.hotel.commands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;

public class StaffOnlineCommand extends Command {
    public StaffOnlineCommand() {
        super(
                "cmd_staffonline",
                Emulator.getTexts().getValue("commands.keys.cmd_staffonline").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        int minRank = Emulator.getConfig().getInt("commands.cmd_staffonline.min_rank");

        if (params.length >= 2) {
            try {
                int i = Integer.parseInt(params[1]);

                if (i < 1) {
                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts().getValue("commands.error.cmd_staffonline.positive_only"),
                                    RoomChatMessageBubbles.ALERT);
                    return true;
                } else {
                    minRank = i;
                }
            } catch (Exception e) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_staffonline.numbers_only"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }
        }

        synchronized (Emulator.getGameEnvironment().getUserManager().getOnlineUsers()) {
            ArrayList<User> staffs = new ArrayList<>();

            for (Map.Entry<Integer, User> set : Emulator.getGameEnvironment()
                    .getUserManager()
                    .getOnlineUsers()
                    .entrySet()) {
                if (set.getValue().getUserInfo().getRank().getId() >= minRank) {
                    staffs.add(set.getValue());
                }
            }

            staffs.sort(new Comparator<User>() {
                @Override
                public int compare(User o1, User o2) {
                    return o1.getUserInfo().getId() - o2.getUserInfo().getId();
                }
            });

            StringBuilder message =
                    new StringBuilder(Emulator.getTexts().getValue("commands.generic.cmd_staffonline.staffs"));
            message.append("\r\n");

            for (User user : staffs) {
                message.append(user.getUserInfo().getUsername());
                message.append(": ");
                message.append(user.getUserInfo().getRank().getName());
                message.append("\r");
            }

            gameClient.getUser().alert(message.toString());
        }

        return true;
    }
}
