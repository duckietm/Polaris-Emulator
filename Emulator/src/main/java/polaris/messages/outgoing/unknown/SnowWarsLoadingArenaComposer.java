package polaris.messages.outgoing.unknown;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;

/**
 * @deprecated SnowWar is implemented by the composers in {@code polaris.messages.outgoing.snowwar}; nothing
 *     sends this one. Kept for plugins.
 */
@Deprecated
public class SnowWarsLoadingArenaComposer extends MessageComposer {
    private final int count;

    public SnowWarsLoadingArenaComposer(int count) {
        this.count = count;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(3850);
        this.response.appendInt(this.count); // GameID?
        this.response.appendInt(0); // Count
        // this.response.appendInt(1); //ItemID to dispose?
        return this.response;
    }

    public int getCount() {
        return count;
    }
}
