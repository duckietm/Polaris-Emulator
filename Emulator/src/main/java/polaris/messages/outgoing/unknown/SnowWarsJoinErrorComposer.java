package polaris.messages.outgoing.unknown;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;

/**
 * @deprecated SnowWar is implemented by the composers in {@code polaris.messages.outgoing.snowwar}; nothing
 *     sends this one. Kept for plugins.
 */
@Deprecated
public class SnowWarsJoinErrorComposer extends MessageComposer {
    public static final int ERROR_HAS_ACTIVE_INSTANCE = 6;
    public static final int ERROR_NO_FREE_GAMES_LEFT = 8;
    public static final int ERROR_DUPLICATE_MACHINE_ID = 2;

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(1437);
        this.response.appendInt(2);
        return this.response;
    }
}
