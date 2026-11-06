package polaris.messages.outgoing.navigator;

import java.util.ArrayList;
import java.util.List;
import polaris.hotel.navigation.EventCategory;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class NewNavigatorEventCategoriesComposer extends MessageComposer {
    public static volatile List<EventCategory> CATEGORIES = new ArrayList<>();

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NewNavigatorEventCategoriesComposer);

        this.response.appendInt(NewNavigatorEventCategoriesComposer.CATEGORIES.size());

        for (EventCategory category : NewNavigatorEventCategoriesComposer.CATEGORIES) {
            category.serialize(this.response);
        }

        return this.response;
    }
}
