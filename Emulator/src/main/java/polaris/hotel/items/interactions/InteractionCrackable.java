package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.CrackableReward;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserGender;
import polaris.hotel.users.UserItem;
import polaris.hotel.users.subscriptions.SubscriptionClub;
import polaris.messages.ServerMessage;
import polaris.threading.runnables.CrackableExplode;
import polaris.util.pathfinding.Rotation;

public class InteractionCrackable extends UserItem {
    private final Object lock = new Object();
    public boolean cracked = false;
    protected int ticks = 0;

    public InteractionCrackable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionCrackable(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        if (this.getExtradata().length() == 0) this.setExtradata("0");

        serverMessage.appendInt(7 + (this.isLimited() ? 256 : 0));

        serverMessage.appendString(Emulator.getGameEnvironment()
                        .getItemManager()
                        .calculateCrackState(
                                Integer.parseInt(this.getExtradata()),
                                Emulator.getGameEnvironment()
                                        .getItemManager()
                                        .getCrackableCount(this.getBaseItem().getId()),
                                this.getBaseItem())
                + "");
        serverMessage.appendInt(Integer.valueOf(this.getExtradata()));
        serverMessage.appendInt(Emulator.getGameEnvironment()
                .getItemManager()
                .getCrackableCount(this.getBaseItem().getId()));

        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client == null) {
            return;
        }

        super.onClick(client, room, objects);
        synchronized (this.lock) {
            if (this.getRoomId() == 0) return;

            if (this.cracked) return;

            if (this.userRequiredToBeAdjacent()
                    && client.getUser()
                                    .getRoomUnit()
                                    .getCurrentLocation()
                                    .distance(room.getLayout().getTile(this.getX(), this.getY()))
                            > 1.5) {
                client.getUser()
                        .getRoomUnit()
                        .setGoalLocation(room.getLayout()
                                .getTileInFront(
                                        room.getLayout().getTile(this.getX(), this.getY()),
                                        Rotation.Calculate(
                                                client.getUser().getRoomUnit().getX(),
                                                client.getUser().getRoomUnit().getY(),
                                                this.getX(),
                                                this.getY())));
                return;
            }

            if (this.getExtradata().length() == 0) this.setExtradata("0");

            if (this.getBaseItem().getEffectF() > 0)
                if (client.getUser().getUserInfo().getGender().equals(UserGender.F)
                        && this.getBaseItem().getEffectF()
                                == client.getUser().getRoomUnit().getEffectId()) return;

            if (this.getBaseItem().getEffectM() > 0)
                if (client.getUser().getUserInfo().getGender().equals(UserGender.M)
                        && this.getBaseItem().getEffectM()
                                == client.getUser().getRoomUnit().getEffectId()) return;

            this.onTick(client.getUser(), room);
        }
    }

    public void onTick(User user, Room room) {
        if (this.cracked) return;

        // A temporary copy placed by the "place furni" wired has no database row and no
        // owner: cracking one handed out the reward furni and, on the way, the HC or
        // Builders Club subscription below.
        if (this.isTemporary()) return;

        if (this.allowAnyone() || this.getUserId() == user.getUserInfo().getId()) {
            CrackableReward rewardData = Emulator.getGameEnvironment()
                    .getItemManager()
                    .getCrackableData(this.getBaseItem().getId());

            if (rewardData != null) {
                if (rewardData.requiredEffect > 0 && user.getRoomUnit().getEffectId() != rewardData.requiredEffect)
                    return;

                if (this.ticks < 1) {
                    // If there are no ticks (for example because the room has been reloaded), check the current
                    // extradata of the item and update the ticks.
                    this.ticks = Integer.parseInt(this.getExtradata());
                }
                this.ticks++;
                this.setExtradata("" + (this.ticks));
                this.needsUpdate(true);
                room.updateItem(this);

                if (!rewardData.achievementTick.isEmpty()) {
                    AchievementManager.progressAchievement(
                            user,
                            Emulator.getGameEnvironment()
                                    .getAchievementManager()
                                    .getAchievement(rewardData.achievementTick));
                }
                if (!this.cracked
                        && this.ticks
                                == Emulator.getGameEnvironment()
                                        .getItemManager()
                                        .getCrackableCount(this.getBaseItem().getId())) {
                    this.cracked = true;
                    Emulator.getThreading()
                            .run(
                                    new CrackableExplode(
                                            room, this, user, !this.placeInRoom(), this.getX(), this.getY()),
                                    1500);

                    if (!rewardData.achievementCracked.isEmpty()) {
                        AchievementManager.progressAchievement(
                                user,
                                Emulator.getGameEnvironment()
                                        .getAchievementManager()
                                        .getAchievement(rewardData.achievementCracked));
                    }

                    if (rewardData.subscriptionType != null && rewardData.subscriptionDuration > 0) {
                        // subscriptions are given immediately upon cracking
                        switch (rewardData.subscriptionType) {
                            case CLUB:
                                user.getUserStats()
                                        .createSubscription(
                                                SubscriptionClub.CLUB, rewardData.subscriptionDuration * 86400);
                                break;
                            case BUILDERS_CLUB:
                                user.getUserStats()
                                        .createSubscription("BUILDERS_CLUB", rewardData.subscriptionDuration * 86400);
                                break;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void onWalkOn(RoomUnit client, Room room, Object[] objects) throws Exception {}

    @Override
    public void onWalkOff(RoomUnit client, Room room, Object[] objects) throws Exception {}

    public boolean allowAnyone() {
        return false;
    }

    protected boolean placeInRoom() {
        return true;
    }

    public boolean resetable() {
        return false;
    }

    public boolean userRequiredToBeAdjacent() {
        return true;
    }

    public void reset(Room room) {
        this.cracked = false;
        this.ticks = 0;
        this.setExtradata("0");
        room.updateItem(this);
    }

    @Override
    public boolean isUsable() {
        return false;
    }
}
