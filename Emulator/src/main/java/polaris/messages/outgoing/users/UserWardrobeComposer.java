package polaris.messages.outgoing.users;

import polaris.hotel.users.inventory.WardrobeComponent;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class UserWardrobeComposer extends MessageComposer {
    private final WardrobeComponent wardrobeComponent;

    public UserWardrobeComposer(WardrobeComponent wardrobeComponent) {
        this.wardrobeComponent = wardrobeComponent;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserWardrobeComposer);
        this.response.appendInt(1);
        this.response.appendInt(this.wardrobeComponent.getLooks().size());
        for (WardrobeComponent.WardrobeItem wardrobeItem :
                this.wardrobeComponent.getLooks().values()) {
            this.response.appendInt(wardrobeItem.getSlotId());
            this.response.appendString(wardrobeItem.getLook());
            this.response.appendString(wardrobeItem.getGender().name().toUpperCase());
        }
        return this.response;
    }

    public WardrobeComponent getWardrobeComponent() {
        return wardrobeComponent;
    }
}
