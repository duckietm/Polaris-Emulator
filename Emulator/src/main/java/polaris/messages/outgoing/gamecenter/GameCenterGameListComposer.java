package polaris.messages.outgoing.gamecenter;

import java.util.ArrayList;
import java.util.List;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * The games of the Game Center. Only the games switched on are listed (`gamecenter.snowwar.enabled`,
 * `gamecenter.fastfood.enabled`): a game that cannot be played is not shown.
 */
public class GameCenterGameListComposer extends MessageComposer {

    /** One game: its id, its name, its colours and where its pictures are. */
    record Game(int id, String name, String backgroundColor, String assetUrl) {}

    @Override
    protected ServerMessage composeInternal() {
        List<Game> games = games(Emulator.getConfig());

        this.response.init(Outgoing.GameCenterGameListComposer);
        this.response.appendInt(games.size());

        for (Game game : games) {
            this.response.appendInt(game.id());
            this.response.appendString(game.name());
            this.response.appendString(game.backgroundColor());
            this.response.appendString(""); // text colour
            this.response.appendString(game.assetUrl());
            this.response.appendString("");
        }

        return this.response;
    }

    static List<Game> games(ConfigurationManager config) {
        List<Game> games = new ArrayList<>();

        if (config.getBoolean("gamecenter.snowwar.enabled", true)) {
            games.add(new Game(0, "snowwar", "93d4f3", config.getValue("images.gamecenter.snowwar", "")));
        }

        if (config.getBoolean("gamecenter.fastfood.enabled", false)) {
            games.add(new Game(3, "basejump", "68bbd2", config.getValue("images.gamecenter.basejump", "")));
        }

        return games;
    }
}
