package polaris.messages.incoming.users;

import polaris.hotel.users.UserWordFilter;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.CustomWordFilterModifyResultComposer;

/** RemoveCustomFilterWord (1996). */
public class RemoveCustomWordFilterWordEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String word = this.packet.readString();
        word = UserWordFilter.normalize(word);
        if (word == null) {
            this.client.sendResponse(
                    new CustomWordFilterModifyResultComposer(CustomWordFilterModifyResultComposer.FAILED, ""));
            return;
        }

        boolean removed = this.client.getUser().getUserStats().removeCustomFilterWord(word);
        this.client.sendResponse(new CustomWordFilterModifyResultComposer(
                removed ? CustomWordFilterModifyResultComposer.REMOVED : CustomWordFilterModifyResultComposer.FAILED,
                word));
    }
}
