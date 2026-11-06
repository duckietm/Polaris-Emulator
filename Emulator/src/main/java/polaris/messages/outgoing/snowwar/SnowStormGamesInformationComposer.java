package polaris.messages.outgoing.snowwar;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class SnowStormGamesInformationComposer extends MessageComposer {

    private final int playersInQueue;
    private final int gamesPlayed;
    private final int minPlayers;
    private final boolean canEdit;

    public SnowStormGamesInformationComposer(int playersInQueue, int gamesPlayed, int minPlayers, boolean canEdit) {
        this.playersInQueue = playersInQueue;
        this.gamesPlayed = gamesPlayed;
        this.minPlayers = minPlayers;
        this.canEdit = canEdit;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormGamesInformationComposer);
        this.response.appendInt(this.playersInQueue);
        this.response.appendInt(this.gamesPlayed);
        // Minimum players needed to start (gamecenter.snowwar.players.min) so the
        // client can show an "awaiting players" state instead of a bare queue
        // count. Trailing int - old clients simply don't read it.
        this.response.appendInt(this.minPlayers);
        // Whether this user may build custom arenas (acc_snowwar_arena_build), so the
        // client can show the Edit button in the queue. Trailing boolean.
        this.response.appendBoolean(this.canEdit);
        return this.response;
    }
}
