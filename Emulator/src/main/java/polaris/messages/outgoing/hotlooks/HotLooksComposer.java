package polaris.messages.outgoing.hotlooks;

import java.util.List;
import polaris.hotel.hotlooks.HotLook;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** AIR 13 HotLooks: int count, then per look string gender ("M"/"F") and string figure. */
public class HotLooksComposer extends MessageComposer {
    private final List<HotLook> looks;

    public HotLooksComposer(List<HotLook> looks) {
        this.looks = looks;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HotLooksComposer);
        this.response.appendInt(this.looks.size());
        for (HotLook look : this.looks) {
            this.response.appendString(look.gender());
            this.response.appendString(look.figure());
        }
        return this.response;
    }
}
