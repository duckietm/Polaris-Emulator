package polaris.messages.outgoing.unknown;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;

/**
 * @deprecated SnowWar is implemented by the composers in {@code polaris.messages.outgoing.snowwar}; nothing
 *     sends this one. Kept for plugins.
 */
@Deprecated
public class SnowWarsQuickJoinComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(913);
        this.response.appendInt(1);
        this.response.appendString("SnowStorm level " + 9);
        this.response.appendInt(0); // Stage ID
        this.response.appendInt(9);
        this.response.appendInt(2);
        this.response.appendInt(10);
        this.response.appendString("Admin");
        this.response.appendInt(0);
        this.response.appendInt(2);
        this.response.appendInt(1);
        this.response.appendString("Admin");
        this.response.appendString("ca-1807-64.lg-275-78.hd-3093-1.hr-802-42.ch-3110-65-62.fa-1211-62");
        this.response.appendString("m");
        this.response.appendInt(-1);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(2);
        this.response.appendString("Droppy");
        this.response.appendString("ca-1807-64.lg-275-78.hd-3093-1.hr-802-42.ch-3110-65-62.fa-1211-62");
        this.response.appendString("m");
        this.response.appendInt(-1);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        return this.response;
    }
}
