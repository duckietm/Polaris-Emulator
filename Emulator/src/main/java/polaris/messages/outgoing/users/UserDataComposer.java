package polaris.messages.outgoing.users;

import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class UserDataComposer extends MessageComposer {
    private final User user;

    public UserDataComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserDataComposer);

        this.response.appendInt(this.user.getUserInfo().getId());
        this.response.appendString(this.user.getUserInfo().getUsername());
        this.response.appendString(this.user.getUserInfo().getLook());
        this.response.appendString(this.user.getUserInfo().getGender().name().toUpperCase());
        this.response.appendString(this.user.getUserInfo().getMotto());
        this.response.appendString(this.user.getUserInfo().getUsername());
        this.response.appendBoolean(false);
        this.response.appendInt(this.user.getUserStats().respectPointsReceived);
        this.response.appendInt(this.user.getUserStats().respectPointsToGive);
        this.response.appendInt(this.user.getUserStats().petRespectPointsToGive);
        this.response.appendBoolean(false);
        this.response.appendString("01-01-1970 00:00:00");
        this.response.appendBoolean(this.user.getUserStats().allowNameChange); // can change name.
        this.response.appendBoolean(this.user.getUserStats().safetyLocked); // accountSafetyLocked

        // Official class_1757.parse reads two more optional blocks when bytes remain:
        // (accountTradeLocked, nameColor) and (respectReplenishesLeft, maxRespectPerDay).
        this.response.appendBoolean(!this.user.getUserStats().allowTrade());
        this.response.appendString("");
        this.response.appendInt(this.user.getUserStats().respectReplenishesLeft);
        this.response.appendInt(Emulator.getConfig().getInt("hotel.daily.respect"));

        return this.response;
    }

    public User getUser() {
        return user;
    }
}
