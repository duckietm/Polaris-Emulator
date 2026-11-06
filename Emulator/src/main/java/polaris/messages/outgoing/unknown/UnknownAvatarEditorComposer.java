package polaris.messages.outgoing.unknown;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

// The header alias is deprecated in Outgoing; the composer stays for plugin compatibility.
@SuppressWarnings("deprecation")
/**
 * @deprecated nothing in the hotel sends this composer. Kept for plugins.
 */
@Deprecated
public class UnknownAvatarEditorComposer extends MessageComposer {
    private final int type;

    public UnknownAvatarEditorComposer(int type) {
        this.type = type;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UnknownAvatarEditorComposer);
        this.response.appendInt(this.type);
        return this.response;
    }

    public int getType() {
        return type;
    }
}
