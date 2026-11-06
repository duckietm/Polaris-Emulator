package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class UpdateYoutubePlaylistsCommand extends Command {
    public UpdateYoutubePlaylistsCommand() {
        super(
                "cmd_update_youtube_playlists",
                Emulator.getTexts()
                        .getValue("commands.keys.cmd_update_youtube_playlists")
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Emulator.getGameEnvironment().getItemManager().getYoutubeManager().load();

        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts().getValue("commands.succes.cmd_update_youtube_playlists"),
                        RoomChatMessageBubbles.ALERT);

        return true;
    }
}
