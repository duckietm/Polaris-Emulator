package polaris.messages.outgoing.guides;

import polaris.Emulator;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuideToolsComposer extends MessageComposer {
    private final boolean onDuty;

    public GuideToolsComposer(boolean onDuty) {
        this.onDuty = onDuty;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuideToolsComposer);
        this.response.appendBoolean(this.onDuty); // OnDuty
        this.response.appendInt(0); // Guides On Duty
        this.response.appendInt(Emulator.getGameEnvironment().getGuideManager().getGuidesCount()); // Helpers On Duty
        this.response.appendInt(
                Emulator.getGameEnvironment().getGuideManager().getGuardiansCount()); // Guardians On Duty
        return this.response;
    }

    public boolean isOnDuty() {
        return onDuty;
    }
}
