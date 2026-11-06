package polaris.messages.outgoing.gamecenter;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * AIR Game2InArenaQueueMessageEvent (872): enough players are waiting but
 * every arena is busy (gamecenter.games.max.concurrent), so the lobby shows
 * "Place in queue: %position%" (snowwar.lobby_arena_queue_position). The
 * position counts the full matches ahead, from 1; -1 clears it.
 */
public class Game2InArenaQueueComposer extends MessageComposer {

    private final int position;

    public Game2InArenaQueueComposer(int position) {
        this.position = position;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.Game2InArenaQueueComposer);
        this.response.appendInt(this.position);
        return this.response;
    }

    public int getPosition() {
        return this.position;
    }
}
