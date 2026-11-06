package polaris.messages.outgoing.hotelview;

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

        this.response.appendInt(newsList.getNewsWidgets().size());

        for (NewsWidget widget : newsList.getNewsWidgets()) {
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
