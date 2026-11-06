package polaris.messages.outgoing.wheel;

import java.util.List;
import polaris.hotel.wheel.WheelRecentWin;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

// "Latest winners" list: username + look (for the headshot) + prize label.
public class WheelRecentWinsComposer extends MessageComposer {
    private final List<WheelRecentWin> wins;

    public WheelRecentWinsComposer(List<WheelRecentWin> wins) {
        this.wins = wins;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WheelRecentWinsComposer);
        this.response.appendInt(this.wins.size());
        for (WheelRecentWin win : this.wins) {
            this.response.appendString(win.username);
            this.response.appendString(win.look);
            this.response.appendString(win.prizeLabel);
        }
        return this.response;
    }
}
