package polaris.messages.outgoing.unknown;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;

/**
 * @deprecated SnowWar is implemented by the composers in {@code polaris.messages.outgoing.snowwar}; nothing
 *     sends this one. Kept for plugins.
 */
@Deprecated
public class SnowWarsOnGameEnding extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(1893);
        this.response.appendInt(0); // idk

        // Game2GameResult
        this.response.appendBoolean(false);
        this.response.appendInt(0); // resultType
        this.response.appendInt(0); // result?

        this.response.appendInt(1); // Count
        // {
        // Game2TeamScoreData
        this.response.appendInt(1); // Team ID?
        this.response.appendInt(100); // Score

        this.response.appendInt(1); // Count
        // {
        // Game2TeamPlayerData
        this.response.appendString("Admin"); // username
        this.response.appendInt(1); // UserID
        this.response.appendString("ca-1807-64.lg-275-78.hd-3093-1.hr-802-42.ch-3110-65-62.fa-1211-62"); // Look
        this.response.appendString("m"); // GENDER
        this.response.appendInt(1337); // Score

        // Game2PlayerStatsData
        this.response.appendInt(1337);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        // }
        // }

        // Game2SnowWarGameStats
        this.response.appendInt(1337);
        this.response.appendInt(1338);

        return this.response;
    }
}
