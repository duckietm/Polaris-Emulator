package polaris.messages.outgoing.catalog;

import polaris.hotel.rooms.BuildersClubRoomSupport;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class BuildersClubSubscriptionStatusComposer extends MessageComposer {
    private final int secondsLeft;
    private final int furniLimit;
    private final int maxFurniLimit;
    private final int secondsLeftWithGrace;
    private final boolean placementBlockedByVisitors;
    private final boolean placementAllowedInCurrentRoom;

    public BuildersClubSubscriptionStatusComposer(User user) {
        this(
                BuildersClubRoomSupport.getMembershipSecondsLeft(
                        user.getUserInfo().getId()),
                BuildersClubRoomSupport.getFurniLimit(user.getUserInfo().getId()),
                BuildersClubRoomSupport.getFurniLimit(user.getUserInfo().getId()),
                BuildersClubRoomSupport.getMembershipSecondsLeft(
                        user.getUserInfo().getId()),
                BuildersClubRoomSupport.isPlacementBlockedByVisitors(user),
                BuildersClubRoomSupport.canPlaceInCurrentRoom(user));
    }

    public BuildersClubSubscriptionStatusComposer(
            int secondsLeft,
            int furniLimit,
            int maxFurniLimit,
            int secondsLeftWithGrace,
            boolean placementBlockedByVisitors,
            boolean placementAllowedInCurrentRoom) {
        this.secondsLeft = Math.max(0, secondsLeft);
        this.furniLimit = Math.max(0, furniLimit);
        this.maxFurniLimit = Math.max(0, maxFurniLimit);
        this.secondsLeftWithGrace = Math.max(0, secondsLeftWithGrace);
        this.placementBlockedByVisitors = placementBlockedByVisitors;
        this.placementAllowedInCurrentRoom = placementAllowedInCurrentRoom;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BuildersClubExpiredComposer);
        this.response.appendInt(this.secondsLeft);
        this.response.appendInt(this.furniLimit);
        this.response.appendInt(this.maxFurniLimit);
        this.response.appendInt(this.secondsLeftWithGrace);
        this.response.appendBoolean(this.placementBlockedByVisitors);
        this.response.appendBoolean(this.placementAllowedInCurrentRoom);
        return this.response;
    }
}
