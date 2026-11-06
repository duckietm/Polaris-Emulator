package polaris.messages.outgoing.snowwar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import polaris.hotel.games.snowwar.SnowWarAttributes;
import polaris.hotel.games.snowwar.SnowWarGame;
import polaris.hotel.games.snowwar.SnowWarGamePlayer;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * Final match results per team and player (PROTOCOL.md 5022).
 *
 * <p>Wire shape: {@code int secondsToResults, int teamCount, teams[teamId,
 * score, playerCount, players[userId, name, score]]}, then the official
 * Game2GameEnding extras appended as an optional tail so older clients keep
 * parsing: {@code int playerWithMostHits, int playerWithMostKills, int count,
 * stats[userId, figure, gender, snowballHits, kills, skillLevel]}.
 */
public class SnowStormOnGameEndingComposer extends MessageComposer {

    private final int secondsToResults;
    private final SnowWarGame game;
    private final Map<Integer, Integer> skillLevels;

    public SnowStormOnGameEndingComposer(int secondsToResults, SnowWarGame game) {
        this(secondsToResults, game, Collections.emptyMap());
    }

    public SnowStormOnGameEndingComposer(int secondsToResults, SnowWarGame game, Map<Integer, Integer> skillLevels) {
        this.secondsToResults = secondsToResults;
        this.game = game;
        this.skillLevels = skillLevels;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormOnGameEndingComposer);
        this.response.appendInt(this.secondsToResults);
        this.response.appendInt(this.game.getTeamCount());

        List<SnowWarGamePlayer> players = this.game.getActivePlayers();

        for (int teamId = 0; teamId < this.game.getTeamCount(); teamId++) {
            List<SnowWarGamePlayer> teamPlayers = new ArrayList<>();
            int teamScore = 0;

            for (SnowWarGamePlayer player : players) {
                if (player.getTeamId() == teamId) {
                    teamPlayers.add(player);
                    teamScore += player.getAttributes().getScore().get();
                }
            }

            this.response.appendInt(teamId);
            this.response.appendInt(teamScore);
            this.response.appendInt(teamPlayers.size());

            for (SnowWarGamePlayer player : teamPlayers) {
                this.response.appendInt(player.getUserId());
                this.response.appendString(player.getUser().getUserInfo().getUsername());
                this.response.appendInt(player.getAttributes().getScore().get());
            }
        }

        this.response.appendInt(playerWithMost(players, true));
        this.response.appendInt(playerWithMost(players, false));
        this.response.appendInt(players.size());

        for (SnowWarGamePlayer player : players) {
            SnowWarAttributes attributes = player.getAttributes();
            this.response.appendInt(player.getUserId());
            this.response.appendString(player.getUser().getUserInfo().getLook());
            this.response.appendString(
                    player.getUser().getUserInfo().getGender().name().toUpperCase());
            this.response.appendInt(attributes.getSnowballHits().get());
            this.response.appendInt(attributes.getKills().get());
            this.response.appendInt(this.skillLevels.getOrDefault(player.getUserId(), 1));
        }

        return this.response;
    }

    /** User id with the most hits (or kills); 0 when nobody scored any. */
    static int playerWithMost(List<SnowWarGamePlayer> players, boolean hits) {
        int bestUserId = 0;
        int best = 0;
        for (SnowWarGamePlayer player : players) {
            SnowWarAttributes attributes = player.getAttributes();
            int value = hits
                    ? attributes.getSnowballHits().get()
                    : attributes.getKills().get();
            if (value > best) {
                best = value;
                bestUserId = player.getUserId();
            }
        }
        return bestUserId;
    }
}
