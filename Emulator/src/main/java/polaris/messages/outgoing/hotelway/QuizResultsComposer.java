package polaris.messages.outgoing.hotelway;

import java.util.List;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class QuizResultsComposer extends MessageComposer {
    private final String unknownString;
    private final List<Integer> unknownIntegerList;

    public QuizResultsComposer(String unknownString, List<Integer> unknownIntegerList) {
        this.unknownString = unknownString;
        this.unknownIntegerList = unknownIntegerList;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.QuizResultsComposer);
        this.response.appendString(this.unknownString);
        this.response.appendInt(this.unknownIntegerList.size());
        for (Integer i : this.unknownIntegerList) {
            this.response.appendInt(i);
        }
        return this.response;
    }

    public String getUnknownString() {
        return unknownString;
    }

    public List<Integer> getUnknownIntegerList() {
        return unknownIntegerList;
    }
}
