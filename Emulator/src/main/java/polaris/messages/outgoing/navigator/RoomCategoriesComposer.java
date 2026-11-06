package polaris.messages.outgoing.navigator;

import java.util.List;
import polaris.hotel.rooms.RoomCategory;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomCategoriesComposer extends MessageComposer {
    private final List<RoomCategory> categories;

    public RoomCategoriesComposer(List<RoomCategory> categories) {
        this.categories = categories;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomCategoriesComposer);

        this.response.appendInt(this.categories.size());
        for (RoomCategory category : this.categories) {
            this.response.appendInt(category.getId());
            this.response.appendString(category.getCaption());
            this.response.appendBoolean(true); // Visible
            this.response.appendBoolean(false); // True = Disconnect?
            this.response.appendString(category.getCaption());

            if (category.getCaption().startsWith("${")) {
                this.response.appendString("");
            } else {
                this.response.appendString(category.getCaption());
            }

            this.response.appendBoolean(false);
        }

        return this.response;
    }

    public List<RoomCategory> getCategories() {
        return categories;
    }
}
