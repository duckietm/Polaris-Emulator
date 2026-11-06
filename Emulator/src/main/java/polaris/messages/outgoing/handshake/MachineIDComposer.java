package polaris.messages.outgoing.handshake;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class MachineIDComposer extends MessageComposer {

    private final String machineId;

    public MachineIDComposer(String machineId) {
        this.machineId = machineId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MachineIDComposer);
        this.response.appendString(this.machineId);
        return this.response;
    }

    public String getMachineId() {
        return machineId;
    }
}
