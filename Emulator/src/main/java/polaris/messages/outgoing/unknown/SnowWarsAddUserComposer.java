package polaris.messages.outgoing.unknown;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;

/**
 * @deprecated SnowWar is implemented by the composers in {@code polaris.messages.outgoing.snowwar}; nothing
 *     sends this one. Kept for plugins.
 */
@Deprecated
public class SnowWarsAddUserComposer extends MessageComposer {

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(1880);
        this.response.appendInt(3);
        this.response.appendString("Derpface");
        this.response.appendString("ca-1807-64.lg-275-78.hd-3093-1.hr-802-42.ch-3110-65-62.fa-1211-63");
        this.response.appendString("m");
        this.response.appendInt(-1); // Team Id
        this.response.appendInt(0); // Stars
        this.response.appendInt(0); // Points
        this.response.appendInt(10); // Points for next lvl
        this.response.appendBoolean(false);
        return this.response;
    }
}
