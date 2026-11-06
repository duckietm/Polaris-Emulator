package polaris.messages.outgoing.hotelway;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class QuizDataComposer extends MessageComposer {
    public final String name;
    public final int[] items;

    public QuizDataComposer(String name, int[] items) {
        this.name = name;
        this.items = items;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.QuizDataComposer);
        this.response.appendString(this.name);
        this.response.appendInt(this.items.length);
        for (int item : this.items) {
            this.response.appendInt(item);
        }
        return this.response;
    }

    public String getName() {
        return name;
    }

    public int[] getItems() {
        return items;
    }
}
