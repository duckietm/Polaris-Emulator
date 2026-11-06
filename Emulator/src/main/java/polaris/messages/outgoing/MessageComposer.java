package polaris.messages.outgoing;

import polaris.messages.ServerMessage;

public abstract class MessageComposer {

    private ServerMessage composed;
    protected final ServerMessage response;

    protected MessageComposer() {
        this.composed = null;
        this.response = new ServerMessage();
    }

    protected abstract ServerMessage composeInternal();

    public ServerMessage compose() {
        if (this.composed == null) {
            this.composed = this.composeInternal();
            if (this.composed != null) {
                if (this.composed.getComposer() == null) {
                    this.composed.setComposer(this);
                }
            }
        }

        return this.composed;
    }
}
