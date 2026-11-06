package polaris.messages.outgoing.rooms.users;

import polaris.hotel.users.User;
import polaris.hotel.users.UserCustomizationData;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomUserDataComposer extends MessageComposer {
    private final User user;

    public RoomUserDataComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUserDataComposer);
        this.response.appendInt(
                this.user.getRoomUnit() == null ? -1 : this.user.getRoomUnit().getId());
        this.response.appendString(this.user.getUserInfo().getLook());
        this.response.appendString(this.user.getUserInfo().getGender().name() + "");
        this.response.appendString(this.user.getUserInfo().getMotto());
        this.response.appendInt(this.user.getUserStats().getAchievementScore());
        this.response.appendInt(this.user.getUserInfo().getInfostandBg());
        this.response.appendInt(this.user.getUserInfo().getInfostandStand());
        this.response.appendInt(this.user.getUserInfo().getInfostandOverlay());
        this.response.appendInt(this.user.getUserInfo().getInfostandCardBg());
        UserCustomizationData customizationData = UserCustomizationData.fromUser(this.user);
        this.response.appendString(customizationData.nickIcon);
        this.response.appendString(customizationData.prefixText);
        this.response.appendString(customizationData.prefixColor);
        this.response.appendString(customizationData.prefixIcon);
        this.response.appendString(customizationData.prefixEffect);
        this.response.appendString(customizationData.prefixFont);
        this.response.appendString(customizationData.displayOrder);
        this.response.appendInt(this.user.getUserInfo().getInfostandBorder());
        return this.response;
    }

    public User getUser() {
        return user;
    }
}
