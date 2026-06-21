package com.eu.habbo.messages.incoming.polls;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.polls.Poll;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.polls.PollQuestionsComposer;

public class GetPollDataEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int pollId = this.packet.readInt();

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null || room.getPollId() != pollId) {
            return;
        }

        Poll poll = Emulator.getGameEnvironment().getPollManager().getPoll(pollId);

        if (poll != null) {
            this.client.sendResponse(new PollQuestionsComposer(poll));
        }
    }
}
