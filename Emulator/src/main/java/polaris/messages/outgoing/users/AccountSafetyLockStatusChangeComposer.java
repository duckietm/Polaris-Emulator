package polaris.messages.outgoing.users;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class AccountSafetyLockStatusChangeComposer extends MessageComposer {
    public final int STATUS_ZERO = 0;
    public final int STATUS_ONE = 1;

    private final int status;

    public AccountSafetyLockStatusChangeComposer(int status) {
        this.status = status;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AccountSafetyLockStatusChangeComposer);
        this.response.appendInt(this.status);
        return this.response;
    }

    public int getStatus() {
        return status;
    }
}
