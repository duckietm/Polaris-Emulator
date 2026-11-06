package polaris.messages.outgoing.hotelview;

import java.util.List;
import polaris.Emulator;
import polaris.hotel.hotelview.NewsList;
import polaris.hotel.hotelview.NewsWidget;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class NewsListComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NewsWidgetsComposer);
        NewsList newsList = Emulator.getGameEnvironment().getHotelViewManager().getNewsList();

        // One read: a reload swaps the list, and the count must describe the widgets written after it.
        List<NewsWidget> widgets = newsList.getNewsWidgets();

        this.response.appendInt(widgets.size());

        for (NewsWidget widget : widgets) {
            this.response.appendInt(widget.getId());
            this.response.appendString(widget.getTitle());
            this.response.appendString(widget.getMessage());
            this.response.appendString(widget.getButtonMessage());
            this.response.appendInt(widget.getType());
            this.response.appendString(widget.getLink());
            this.response.appendString(widget.getImage());
        }

        return this.response;
    }
}
