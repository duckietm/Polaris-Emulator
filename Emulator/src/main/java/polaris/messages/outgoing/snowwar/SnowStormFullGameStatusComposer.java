package polaris.messages.outgoing.snowwar;

import java.util.List;
import polaris.hotel.games.snowwar.objects.SnowWarGameObject;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * Complete snapshot of the game state (PROTOCOL.md 5016).
 */
public class SnowStormFullGameStatusComposer extends MessageComposer {

    private final int turn;
    private final int checksum;
    private final int totalSecondsLeft;
    private final List<SnowWarGameObject> objects;

    public SnowStormFullGameStatusComposer(
            int turn, int checksum, int totalSecondsLeft, List<SnowWarGameObject> objects) {
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
