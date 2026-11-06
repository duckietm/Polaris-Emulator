package polaris.messages.outgoing.handshake;

import java.util.Map;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class IdentityAccountsComposer extends MessageComposer {
    private final Map<Integer, String> unknownMap;

    public IdentityAccountsComposer(Map<Integer, String> unknownMap) {
        this.unknownMap = unknownMap;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.IdentityAccountsComposer);
        this.response.appendInt(this.unknownMap.size());
        for (Map.Entry<Integer, String> entry : this.unknownMap.entrySet()) {
            this.response.appendInt(entry.getKey());
            this.response.appendString(entry.getValue());
        }
        return this.response;
    }

    public Map<Integer, String> getUnknownMap() {
        return unknownMap;
    }
}
