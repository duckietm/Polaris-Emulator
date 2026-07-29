package com.eu.habbo.messages.outgoing.snowwar;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

/**
 * Provisional lobby line-up broadcast during the pre-match countdown so the
 * client can show a "getting ready" screen with the waiting players already
 * split into their teams (Red / Blue), before the arena splash.
 *
 * <p>The real match (and its object ids) only exists once the countdown hits
 * zero, so this carries no object ids - the team split is computed the same
 * round-robin way {@code SnowWarGame} does at creation ({@code index %
 * teamCount}). Player records are written in the SnowWarPlayerData wire shape
 * (objectId, userId, teamId, name, look, gender) with objectId 0 so the client
 * can reuse the existing player parser.
 */
public class SnowStormLobbyTeamsComposer extends MessageComposer {

    private final List<Habbo> players;
    private final int teamCount;

    public SnowStormLobbyTeamsComposer(List<Habbo> players, int teamCount) {
        this.players = players;
        this.teamCount = teamCount;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormLobbyTeamsComposer);

        this.response.appendInt(this.teamCount);
        this.response.appendInt(this.players.size());

        int index = 0;
        for (Habbo habbo : this.players) {
            this.response.appendInt(0); // objectId - not assigned until match creation
            this.response.appendInt(habbo.getHabboInfo().getId());
            this.response.appendInt(index % this.teamCount);
            this.response.appendString(habbo.getHabboInfo().getUsername());
            this.response.appendString(habbo.getHabboInfo().getLook());
            this.response.appendString(habbo.getHabboInfo().getGender().name().toUpperCase());
            index++;
        }

        return this.response;
    }
}
