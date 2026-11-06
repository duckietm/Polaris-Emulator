package polaris.messages.outgoing.furniture;

import java.util.List;
import polaris.hotel.items.FurnidataEntry;
import polaris.hotel.items.FurnitureType;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class FurnitureDataReloadComposer extends MessageComposer {

    public static final int MODE_DELTA = 0;
    public static final int MODE_RELOAD_HINT = 1;

    private final int mode;
    private final List<FurnidataEntry> entries;

    public FurnitureDataReloadComposer(int mode, List<FurnidataEntry> entries) {
        this.mode = mode;
        this.entries = entries;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FurnitureDataReloadComposer);
        this.response.appendInt(this.mode);

        if (this.mode == MODE_DELTA) {
            this.response.appendInt(this.entries.size());
            for (FurnidataEntry e : this.entries) {
                this.response.appendString(e.type() == FurnitureType.FLOOR ? "S" : "I");
                this.response.appendInt(e.id());
                this.response.appendString(e.classname());
                this.response.appendString(e.name());
                this.response.appendString(e.description());
            }
        }

        return this.response;
    }
}
