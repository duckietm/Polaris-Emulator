package polaris.messages.outgoing.hotelview;

import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class BonusRareComposer extends MessageComposer {
    private final User user;

    public BonusRareComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BonusRareComposer);
        this.response.appendString(Emulator.getConfig()
                .getValue(
                        "hotelview.promotional.reward.name",
                        "prizetrophy_breed_gold")); // Furniture Name. Note: Image is in external_variables.txt
        this.response.appendInt(Emulator.getConfig().getInt("hotelview.promotional.reward.id", 0)); // Furniture ID
        this.response.appendInt(Emulator.getConfig().getInt("hotelview.promotional.points", 120)); // Total Required
        // this.response.appendInt(this.user.getUserInfo().getBonusRarePoints() >=
        // Emulator.getConfig().getInt("hotelview.promotinal.points", 120) ?
        // Emulator.getConfig().getInt("hotelview.promotinal.points", 120) :
        // this.user.getUserInfo().getBonusRarePoints() ); //Total To Gain
        int points = Emulator.getConfig().getInt("hotelview.promotional.points", 120)
                - this.user.getUserInfo().getBonusRarePoints();
        this.response.appendInt(Math.max(points, 0));

        return this.response;
    }

    public User getUser() {
        return user;
    }
}
