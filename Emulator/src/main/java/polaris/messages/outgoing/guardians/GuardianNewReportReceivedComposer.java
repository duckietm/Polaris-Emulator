package polaris.messages.outgoing.guardians;

import polaris.Emulator;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuardianNewReportReceivedComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuardianNewReportReceivedComposer);
        this.response.appendInt(Emulator.getConfig().getInt("guardians.accept.timer"));
        return this.response;
    }
}
