package polaris.messages.outgoing.polls;

import java.util.Map;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class QuestionFinishedComposer extends MessageComposer {
    private final int unknownInt;
    private final Map<String, Integer> unknownMap;

    public QuestionFinishedComposer(int unknownInt, Map<String, Integer> unknownMap) {
        this.unknownInt = unknownInt;
        this.unknownMap = unknownMap;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SimplePollAnswersComposer);
        this.response.appendInt(this.unknownInt);
        this.response.appendInt(this.unknownMap.size());
        for (Map.Entry<String, Integer> entry : this.unknownMap.entrySet()) {
            this.response.appendString(entry.getKey());
            this.response.appendInt(entry.getValue());
        }
        return this.response;
    }

    public int getUnknownInt() {
        return unknownInt;
    }

    public Map<String, Integer> getUnknownMap() {
        return unknownMap;
    }
}
