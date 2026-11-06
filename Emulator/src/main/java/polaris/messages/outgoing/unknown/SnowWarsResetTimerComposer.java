package polaris.messages.outgoing.unknown;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;

/**
 * @deprecated SnowWar is implemented by the composers in {@code polaris.messages.outgoing.snowwar}; nothing
 *     sends this one. Kept for plugins.
 */
@Deprecated
public class SnowWarsResetTimerComposer extends MessageComposer {
    // SnowStageRunning?
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(294);
        this.response.appendInt(100);
        return this.response;
    }
}
