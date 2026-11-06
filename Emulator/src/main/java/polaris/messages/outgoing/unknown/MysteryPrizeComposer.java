package polaris.messages.outgoing.unknown;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;

/**
 * @deprecated nothing in the hotel sends this composer. Kept for plugins.
 */
@Deprecated
public class MysteryPrizeComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(427);
        this.response.appendString("s");
        this.response.appendInt(230);
        return this.response;

        // s -> floorItem. -> itemId
        // i -> wallItem. -> itemId
        // e -> effect -> effectId
        // h -> HabboClub -> 0
    }
}
