package polaris.messages.outgoing.friends;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RemoveFriendComposer extends MessageComposer {
    private final IntArrayList unfriendIds;

    public RemoveFriendComposer(IntArrayList unfriendIds) {
        this.unfriendIds = unfriendIds;
    }

    public RemoveFriendComposer(int i) {
        this.unfriendIds = new IntArrayList();
        this.unfriendIds.add(i);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UpdateFriendComposer);

        this.response.appendInt(0);
        this.response.appendInt(this.unfriendIds.size());
        for (int i = 0; i < this.unfriendIds.size(); i++) {
            this.response.appendInt(-1);
            this.response.appendInt(this.unfriendIds.getInt(i));
        }

        return this.response;
    }

    public IntArrayList getUnfriendIds() {
        return unfriendIds;
    }
}
