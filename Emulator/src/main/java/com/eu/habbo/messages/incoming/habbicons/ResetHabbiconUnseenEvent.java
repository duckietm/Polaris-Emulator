package com.eu.habbo.messages.incoming.habbicons;

import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.incoming.MessageHandler;
import java.util.ArrayList;
import java.util.List;

public final class ResetHabbiconUnseenEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (packet.readInt() != 8) {
            return;
        }
        List<Integer> ids = new ArrayList<>();
        if (packet.getMessageId() == Incoming.UnseenResetItemsEvent) {
            int count = packet.readInt();
            if (count <= 0 || count > 1000 || packet.bytesAvailable() < count * Integer.BYTES) {
                return;
            }
            for (int index = 0; index < count; index++) {
                ids.add(packet.readInt());
            }
        }
        client.getHabbo()
                .getHabbiconService()
                .clearUnseen(client.getHabbo().getHabboInfo().getId(), ids);
    }
}
