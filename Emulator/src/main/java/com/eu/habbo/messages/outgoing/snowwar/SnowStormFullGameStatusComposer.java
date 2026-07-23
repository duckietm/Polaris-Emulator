package com.eu.habbo.messages.outgoing.snowwar;

import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarGameObject;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

/**
 * Complete snapshot of the game state (PROTOCOL.md 5016).
 */
public class SnowStormFullGameStatusComposer extends MessageComposer {

    private final int turn;
    private final int checksum;
    private final int totalSecondsLeft;
    private final List<SnowWarGameObject> objects;

    public SnowStormFullGameStatusComposer(int turn, int checksum, int totalSecondsLeft, List<SnowWarGameObject> objects) {
        this.turn = turn;
        this.checksum = checksum;
        this.totalSecondsLeft = totalSecondsLeft;
        this.objects = objects;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormFullGameStatusComposer);
        this.response.appendInt(this.turn);
        this.response.appendInt(this.checksum);
        this.response.appendInt(this.totalSecondsLeft);
        this.response.appendInt(this.objects.size());

        for (SnowWarGameObject object : this.objects) {
            object.serialize(this.response);
        }

        return this.response;
    }
}
