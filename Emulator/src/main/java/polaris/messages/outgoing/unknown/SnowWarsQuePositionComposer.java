package polaris.messages.outgoing.unknown;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;

/**
 * @deprecated SnowWar is implemented by the composers in {@code polaris.messages.outgoing.snowwar}; nothing
 *     sends this one. Kept for plugins.
 */
@Deprecated
public class SnowWarsQuePositionComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(2077);
        this.response.appendInt(1);
        return this.response;
    }
}
