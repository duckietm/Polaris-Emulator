package polaris.messages.incoming.users;

import polaris.hotel.users.UserWordFilter;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.CustomWordFilterModifyResultComposer;

/** AddCustomFilterWord (68). */
public class AddCustomWordFilterWordEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String word = this.packet.readString();
        word = UserWordFilter.normalize(word);
        if (word == null) {
            this.client.sendResponse(
                    new CustomWordFilterModifyResultComposer(CustomWordFilterModifyResultComposer.FAILED, ""));
            return;
        }

        boolean added = this.client.getUser().getUserStats().addCustomFilterWord(word);
        this.client.sendResponse(new CustomWordFilterModifyResultComposer(
                added ? CustomWordFilterModifyResultComposer.ADDED : CustomWordFilterModifyResultComposer.FAILED,
                word));
    }
}
