package polaris.plugin.events.users;

import polaris.hotel.items.interactions.wired.effects.WiredEffectGiveReward;
import polaris.hotel.users.User;

public class UserWiredRewardReceived extends UserEvent {

    public final WiredEffectGiveReward wiredEffectGiveReward;

    public final String type;

    public String value;

    public UserWiredRewardReceived(User user, WiredEffectGiveReward wiredEffectGiveReward, String type, String value) {
        super(user);

        this.wiredEffectGiveReward = wiredEffectGiveReward;
        this.type = type;
        this.value = value;
    }
}
