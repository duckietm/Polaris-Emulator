package polaris.messages.outgoing.users;

import java.util.List;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** CustomFilterResult (3883): the user's personal word filter list. */
public class CustomWordFilterWordsComposer extends MessageComposer {
    private final List<String> words;

    public CustomWordFilterWordsComposer(List<String> words) {
        this.words = words;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CustomWordFilterWordsComposer);
        this.response.appendInt(this.words.size());
        for (String word : this.words) {
            this.response.appendString(word);
        }
        return this.response;
    }
}
