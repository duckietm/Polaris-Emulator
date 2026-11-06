package polaris.messages.outgoing.habbicons;

import polaris.hotel.habbicons.HabbiconService;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public final class UserHabbiconsComposer extends MessageComposer {
    private final HabbiconService.Snapshot snapshot;

    public UserHabbiconsComposer(HabbiconService.Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserHabbiconsComposer);
        this.response.appendInt((int) snapshot.items().values().stream()
                .filter(HabbiconService.Item::collected)
                .count());
        for (HabbiconService.Item item : snapshot.items().values()) {
            if (item.collected()) {
                this.response.appendInt(item.id());
                this.response.appendInt(item.state());
            }
        }
        this.response.appendInt(snapshot.recent().size());
        for (int id : snapshot.recent()) {
            this.response.appendInt(id);
        }
        return this.response;
    }
}
