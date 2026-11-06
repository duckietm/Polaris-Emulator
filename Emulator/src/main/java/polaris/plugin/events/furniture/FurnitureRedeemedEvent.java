package polaris.plugin.events.furniture;

import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public class FurnitureRedeemedEvent extends FurnitureUserEvent {
    public static final int CREDITS = -1;
    public static final int PIXELS = 0;
    public static final int DIAMONDS = 5;

    public final int amount;
    public final int currencyID;

    public FurnitureRedeemedEvent(UserItem furniture, User user, int amount, int currencyID) {
        super(furniture, user);

        this.amount = amount;
        this.currencyID = currencyID;
    }
}
