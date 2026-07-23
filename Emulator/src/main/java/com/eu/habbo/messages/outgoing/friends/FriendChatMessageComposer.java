package com.eu.habbo.messages.outgoing.friends;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.Message;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class FriendChatMessageComposer extends MessageComposer {
    private final Message message;
    private final int toId;
    private final int fromId;
    private final String extraData;

    public FriendChatMessageComposer(Message message) {
        this(message, message.getFromId(), message.getFromId(), null);
    }

    public FriendChatMessageComposer(Message message, int toId, int fromId) {
        this(message, toId, fromId, null);
    }

    public FriendChatMessageComposer(Message message, int toId, int fromId, String extraData) {
        this.message = message;
        this.toId = toId;
        this.fromId = fromId;
        this.extraData = extraData;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FriendChatMessageComposer);
        this.response.appendInt(this.toId);
        this.response.appendString(this.message.getMessage());
        this.response.appendInt(Emulator.getIntUnixTimestamp() - this.message.getTimestamp());

        if (this.toId < 0) // group chat
        {
            String name = "AUTO_MODERATOR";
            String look = "lg-5635282-1193.hd-3091-1.sh-3089-73.cc-156282-64.hr-831-34.ha-1012-1186.ch-3050-62-62";
            if (this.fromId > 0) {
                Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(this.fromId);

                if (habbo != null) {
                    name = habbo.getHabboInfo().getUsername();
                    look = habbo.getHabboInfo().getLook();
                } else {
                    name = "UNKNOWN";
                }
            }
            this.response.appendString(name + "/" + look + "/" + this.fromId);
        } else if (this.extraData != null) {
            this.response.appendString(this.extraData);
        }

        return this.response;
    }

    public Message getMessage() {
        return message;
    }

    public int getToId() {
        return toId;
    }

    public int getFromId() {
        return fromId;
    }
}
