package polaris.messages.outgoing.users;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * ModifyCustomFilterResult (3333). The AIR 13 client adds the word to its list on {@link #ADDED}
 * and drops it on {@link #REMOVED}; any other result only refreshes the list it already has.
 */
public class CustomWordFilterModifyResultComposer extends MessageComposer {
    public static final int FAILED = 0;
    public static final int ADDED = 1;
    public static final int REMOVED = 3;

    private final int result;
    private final String word;

    public CustomWordFilterModifyResultComposer(int result, String word) {
        this.result = result;
        this.word = word == null ? "" : word;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CustomWordFilterModifyResultComposer);
        this.response.appendInt(this.result);
        this.response.appendString(this.word);
        return this.response;
    }
}
