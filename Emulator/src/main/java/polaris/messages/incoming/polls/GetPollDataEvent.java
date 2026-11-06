package polaris.messages.incoming.polls;

import polaris.Emulator;
import polaris.hotel.polls.Poll;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.polls.PollQuestionsComposer;

public class GetPollDataEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        int pollId = this.packet.readInt();

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null || room.getPollId() != pollId) {
            return;
        }

        Poll poll = Emulator.getGameEnvironment().getPollManager().getPoll(pollId);

        if (poll != null) {
            this.client.sendResponse(new PollQuestionsComposer(poll));
        }
    }
}
