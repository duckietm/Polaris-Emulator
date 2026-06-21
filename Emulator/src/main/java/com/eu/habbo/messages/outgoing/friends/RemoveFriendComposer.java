package com.eu.habbo.messages.outgoing.friends;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import it.unimi.dsi.fastutil.ints.IntArrayList;

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
