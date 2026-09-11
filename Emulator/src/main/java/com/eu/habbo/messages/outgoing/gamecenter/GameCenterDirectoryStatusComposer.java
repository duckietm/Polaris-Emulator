package com.eu.habbo.messages.outgoing.gamecenter;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * The state of the game hub when it opens: whether the games can be played at all, how long the
 * player is still blocked from starting one, how many games they have played and how many free ones
 * they have left. The client hides the hub behind an "offline" panel for any status but
 * {@link #STATUS_OK}.
 */
public class GameCenterDirectoryStatusComposer extends MessageComposer {
    public static final int STATUS_OK = 0;

    public static final int STATUS_UNAVAILABLE = 2;

    private final int status;
    private final int blockLengthSeconds;
    private final int gamesPlayed;
    private final int freeGamesLeft;

    public GameCenterDirectoryStatusComposer(int status, int blockLengthSeconds, int gamesPlayed, int freeGamesLeft) {
        this.status = status;
        this.blockLengthSeconds = blockLengthSeconds;
        this.gamesPlayed = gamesPlayed;
        this.freeGamesLeft = freeGamesLeft;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UnknownComposer_1741);
        this.response.appendInt(this.status);
        this.response.appendInt(this.blockLengthSeconds);
        this.response.appendInt(this.gamesPlayed);
        this.response.appendInt(this.freeGamesLeft);
        return this.response;
    }
}
