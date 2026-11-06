package polaris.messages.outgoing.wired;

import polaris.hotel.wired.arrays.WiredCreatorToolsArrayInspection;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public final class WiredArrayInspectionDataComposer extends MessageComposer {
    private final WiredCreatorToolsArrayInspection inspection;

    public WiredArrayInspectionDataComposer(WiredCreatorToolsArrayInspection inspection) {
        this.inspection = inspection;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredArrayInspectionDataComposer);
        this.response.appendString(WiredManager.getGson().toJson(this.inspection));
        return this.response;
    }
}
