package polaris.messages.outgoing.furniture;

import java.util.Map;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class FurnitureAliasesComposer extends MessageComposer {
    private final Map<String, String> unknownMap;

    public FurnitureAliasesComposer(Map<String, String> unknownMap) {
        this.unknownMap = unknownMap;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FurnitureAliasesComposer);
        this.response.appendInt(this.unknownMap.size());
        for (Map.Entry<String, String> entry : this.unknownMap.entrySet()) {
            this.response.appendString(entry.getKey());
            this.response.appendString(entry.getValue());
        }
        return this.response;
    }

    public Map<String, String> getUnknownMap() {
        return unknownMap;
    }
}
