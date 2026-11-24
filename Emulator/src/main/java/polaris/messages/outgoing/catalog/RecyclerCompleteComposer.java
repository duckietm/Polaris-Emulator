package polaris.messages.outgoing.catalog;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RecyclerCompleteComposer extends MessageComposer {
    public static final int RECYCLING_COMPLETE = 1;
    public static final int RECYCLING_CLOSED = 2;
    /** AIR RecyclerFinishedMessageEvent.FINISHED_FAIL: the recycle did not happen, the items stay in the inventory. */
    public static final int RECYCLING_FAILED = 2;

    private final int code;
    private final int prizeId;

    public RecyclerCompleteComposer(int code) {
        this(code, 0);
    }

    /** A finished exchange names the box it gave. */
    public RecyclerCompleteComposer(int code, int prizeId) {
        this.code = code;
        this.prizeId = prizeId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RecyclerCompleteComposer);
        this.response.appendInt(this.code);
        this.response.appendInt(this.prizeId);
        return this.response;
    }

    public int getCode() {
        return code;
    }
}
