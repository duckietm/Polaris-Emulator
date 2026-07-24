package com.eu.habbo.messages.outgoing.snowwar;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarGame;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarGamePlayer;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.ArrayList;
import java.util.List;

/**
 * Final match results per team and player (PROTOCOL.md 5022).
 */
public class SnowStormOnGameEndingComposer extends MessageComposer {

    private final int secondsToResults;
    private final SnowWarGame game;

    public SnowStormOnGameEndingComposer(int secondsToResults, SnowWarGame game) {
        this.secondsToResults = secondsToResults;
        this.game = game;
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
                this.response.appendString(player.getHabbo().getHabboInfo().getUsername());
                this.response.appendInt(player.getAttributes().getScore().get());
            }
        }

        return this.response;
    }
}
