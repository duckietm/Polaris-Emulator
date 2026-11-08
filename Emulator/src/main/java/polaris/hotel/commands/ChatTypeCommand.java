package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.messages.incoming.users.ChangeChatBubbleEvent;
import polaris.messages.outgoing.users.MeMenuSettingsComposer;

public class ChatTypeCommand extends Command {
    public ChatTypeCommand() {
        super(
                "cmd_chatcolor",
                Emulator.getTexts().getValue("commands.keys.cmd_chatcolor").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {

        if (params.length >= 2) {
            int chatColor;
            try {
                chatColor = Integer.parseInt(params[1]);
            } catch (Exception e) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_chatcolor.numbers"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (chatColor < 0) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_chatcolor.numbers"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            // The same rules as choosing a bubble in the client: banned numbers, bought styles, club styles.
            if (!ChangeChatBubbleEvent.canUse(gameClient.getUser(), chatColor)) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_chatcolor.banned"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            gameClient.getUser().getUserStats().chatColor = RoomChatMessageBubbles.getBubble(chatColor);
            gameClient.sendResponse(new MeMenuSettingsComposer(gameClient.getUser()));
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.succes.cmd_chatcolor.set")
                                    .replace(
                                            "%chat%",
                                            RoomChatMessageBubbles.getBubble(chatColor)
                                                    .name()
                                                    .replace("_", " ")
                                                    .toLowerCase()),
                            RoomChatMessageBubbles.ALERT);
            return true;
        } else {
            gameClient.getUser().getUserStats().chatColor = RoomChatMessageBubbles.NORMAL;
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.succes.cmd_chatcolor.reset"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }
    }
}
