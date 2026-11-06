package polaris.messages.outgoing.camera;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class CameraPublishWaitMessageComposer extends MessageComposer {
    public final boolean isOk;
    public final int cooldownSeconds;
    public final String extraDataId;

    public CameraPublishWaitMessageComposer(boolean isOk, int cooldownSeconds, String extraDataId) {
        this.isOk = isOk;
        this.cooldownSeconds = cooldownSeconds;
        this.extraDataId = extraDataId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CameraPublishWaitMessageComposer);

        this.response.appendBoolean(this.isOk);
        this.response.appendInt(this.cooldownSeconds);

        if (!this.extraDataId.isEmpty()) {
            this.response.appendString(this.extraDataId);
        }

        return this.response;
    }

    public boolean isOk() {
        return isOk;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public String getExtraDataId() {
        return extraDataId;
    }
}
