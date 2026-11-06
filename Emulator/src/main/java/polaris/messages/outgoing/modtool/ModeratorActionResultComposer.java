package polaris.messages.outgoing.modtool;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * Official {@code ModeratorActionResultMessageEvent} (2335): the acknowledgement
 * the mod tool shows after an alert / ban / mute / kick / trade-lock, read as
 * {@code (int userId, boolean success)}.
 */
public class ModeratorActionResultComposer extends MessageComposer {
    private final int userId;
    private final boolean success;

    public ModeratorActionResultComposer(int userId, boolean success) {
        this.userId = userId;
        this.success = success;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ModToolComposerTwo);
        this.response.appendInt(this.userId);
        this.response.appendBoolean(this.success);
        return this.response;
    }

    public int getUserId() {
        return userId;
    }

    public boolean isSuccess() {
        return success;
    }
}
