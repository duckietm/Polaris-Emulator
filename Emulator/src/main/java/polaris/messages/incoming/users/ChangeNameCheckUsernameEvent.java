package polaris.messages.incoming.users;

import java.util.ArrayList;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.users.UserManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.ChangeNameCheckResultComposer;

public class ChangeNameCheckUsernameEvent extends MessageHandler {
    public static volatile String VALID_CHARACTERS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_-=!?@:,.";

    @Override
    public void handle() throws Exception {
        if (!this.client.getUser().getUserStats().allowNameChange) return;

        String name = this.packet.readString();

        int errorCode = ChangeNameCheckResultComposer.AVAILABLE;

        List<String> suggestions = new ArrayList<>(4);
        if (name.length() < 3) {
            errorCode = ChangeNameCheckResultComposer.TOO_SHORT;
        } else if (name.length() > 15) {
            errorCode = ChangeNameCheckResultComposer.TOO_LONG;
        } else if (name.equalsIgnoreCase(this.client.getUser().getUserInfo().getUsername())
                || UserManager.getOfflineUserInfo(name) != null
                || ConfirmChangeNameEvent.changingUsernames.contains(name.toLowerCase())) {
            errorCode = ChangeNameCheckResultComposer.TAKEN_WITH_SUGGESTIONS;
            suggestions.add(name + Emulator.getRandom().nextInt(9999));
            suggestions.add(name + Emulator.getRandom().nextInt(9999));
            suggestions.add(name + Emulator.getRandom().nextInt(9999));
            suggestions.add(name + Emulator.getRandom().nextInt(9999));
        } else if (!Emulator.getGameEnvironment()
                .getWordFilter()
                .filter(name, this.client.getUser())
                .equalsIgnoreCase(name)) {
            errorCode = ChangeNameCheckResultComposer.NOT_VALID;
        } else {
            String checkName = name;
            for (char c : VALID_CHARACTERS.toCharArray()) {
                checkName = checkName.replace(c + "", "");
            }

            if (!checkName.isEmpty()) {
                errorCode = ChangeNameCheckResultComposer.NOT_VALID;
            } else {
                this.client.getUser().getUserStats().changeNameChecked = name;
            }
        }

        this.client.sendResponse(new ChangeNameCheckResultComposer(errorCode, name, suggestions));
    }
}
