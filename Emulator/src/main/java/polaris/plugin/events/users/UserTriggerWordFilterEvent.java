package polaris.plugin.events.users;

import polaris.hotel.modtool.WordFilterWord;
import polaris.hotel.users.User;

public class UserTriggerWordFilterEvent extends UserEvent {

    public final WordFilterWord word;

    public UserTriggerWordFilterEvent(User user, WordFilterWord word) {
        super(user);

        this.word = word;
    }
}
