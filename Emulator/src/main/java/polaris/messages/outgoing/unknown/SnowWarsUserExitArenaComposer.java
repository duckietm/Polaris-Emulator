package polaris.messages.outgoing.unknown;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;

/**
 * @deprecated SnowWar is implemented by the composers in {@code polaris.messages.outgoing.snowwar}; nothing
 *     sends this one. Kept for plugins.
 */
@Deprecated
public class SnowWarsUserExitArenaComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(3811);
        this.response.appendInt(1); // userId
        this.response.appendInt(1); // IDK ? TEAM?
        return this.response;
    }
}
