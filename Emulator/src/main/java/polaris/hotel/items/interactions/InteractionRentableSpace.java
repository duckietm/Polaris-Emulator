package polaris.hotel.items.interactions;

import java.awt.Rectangle;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.economy.EconomyOperationId;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.rooms.items.rentablespaces.RentableSpaceInfoComposer;
import polaris.threading.runnables.ClearRentedSpace;

/**
 * The official rentable space ({@code furniture_rentable_space}, widget
 * RWE_RENTABLESPACE): a Habbo Club member rents the tiles under the furni for
 * {@code hotel.rentablespace.days} days, only the renter can walk on and build
 * there, and when the rent ends their furni goes back to their inventory.
 */
public class InteractionRentableSpace extends UserItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionRentableSpace.class);
    private static final String RENT_DAYS_CONFIG = "hotel.rentablespace.days";
    private static final int DEFAULT_RENT_DAYS = 7;

    private int renterId;
    private String renterName;
    private int endTimestamp;
    private ScheduledFuture<?> expiry;

    public InteractionRentableSpace(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);

        String[] data = set.getString("extra_data").split(":");

        this.renterName = "Unknown";

        if (data.length == 2) {
            this.renterId = Integer.parseInt(data[0]);
            this.endTimestamp = Integer.parseInt(data[1]);

            if (this.renterId > 0) {
                if (this.isRented()) {
                    User user = Emulator.getGameEnvironment().getUserManager().getUser(this.renterId);

                    if (user != null) {
                        this.renterName = user.getUserInfo().getUsername();
                    } else {
                        try (Connection connection =
                                        Emulator.getDatabase().getDataSource().getConnection();
                                PreparedStatement statement = connection.prepareStatement(
                                        "SELECT username FROM users WHERE id = ? LIMIT 1")) {
                            statement.setInt(1, this.renterId);
                            try (ResultSet row = statement.executeQuery()) {
                                if (row.next()) {
                                    this.renterName = row.getString("username");
                                }
                            }
                        } catch (SQLException e) {
                            LOGGER.error("Caught SQL exception", e);
                        }
                    }
                    this.scheduleExpiry();
                } else {
                    if (this.getRoomId() > 0) {
                        Emulator.getThreading()
                                .run(new ClearRentedSpace(
                                        this,
                                        Emulator.getGameEnvironment()
                                                .getRoomManager()
                                                .getRoom(this.getRoomId())));
                        // endRent clears the renter once the space is emptied: cleared here, the task ran with renter
                        // 0 and left every item of the renter in the space.
                    }
                }
            }
        }
    }

    public InteractionRentableSpace(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);

        this.renterName = "";
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        if (this.getExtradata().isEmpty()) return false;

        User user = room.getUser(roomUnit);

        if (user == null) return true;

        // The room's owner walks on a rented space; this compared the user id with the room id.
        if (user.getUserInfo().getId() == room.getOwnerId()) return true;

        if (this.endTimestamp > Emulator.getIntUnixTimestamp()) {
            return this.renterId > 0 && this.renterId == user.getUserInfo().getId();
        }

        return false;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        this.sendRentWidget(client.getUser());
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        if (this.getExtradata().isEmpty()) this.setExtradata("0:0");

        serverMessage.appendInt(1 + (this.isLimited() ? 256 : 0));

        if (this.isRented()) {
            serverMessage.appendInt(1);
            serverMessage.appendString("renterId");
            serverMessage.appendString(this.renterId + "");
        } else {
            serverMessage.appendInt(0);
        }

        super.serializeExtradata(serverMessage);
    }

    /**
     * Rents the space to {@code user}. Returns 0 when the rent went through,
     * otherwise the RentableSpaceInfoComposer error code the client shows.
     */
    public int tryRent(User user) {
        // Checked and rented under one lock: two users renting at once both passed the check and both paid, and the
        // first was left with a rent of a space that was not theirs.
        synchronized (this) {
            int errorCode = this.getRentErrorCode(user);
            if (errorCode != 0) return errorCode;

            this.rent(user);
            return this.getRenterId() == user.getUserInfo().getId() ? 0 : RentableSpaceInfoComposer.NOT_ENOUGH_CREDITS;
        }
    }

    /** Charges the rent and marks the space rented; silently does nothing when the user cannot pay. */
    public void rent(User user) {
        int cost = this.rentCost();
        boolean hasInfiniteCredits = user.hasPermission(Permission.ACC_INFINITE_CREDITS);
        if (!hasInfiniteCredits && user.getUserInfo().getCredits() < cost) return;

        // The space is rented only when the charge went through: a cancelled or refused debit used to rent it anyway.
        if (!hasInfiniteCredits
                && cost > 0
                && !user.applyCredits(
                        -cost,
                        "rentable_space.rent",
                        EconomyOperationId.create("rentable_space:" + this.getId() + ":"
                                + user.getUserInfo().getId()),
                        user.getUserInfo().getId())) {
            return;
        }

        this.setRenterId(user.getUserInfo().getId());
        this.setRenterName(user.getUserInfo().getUsername());
        this.setEndTimestamp(Emulator.getIntUnixTimestamp() + rentDays() * 86400);

        user.getUserStats().setRentedItemId(this.getId());
        user.getUserStats().setRentedTimeEnd(this.endTimestamp);
        this.needsUpdate(true);
        this.run();
        this.scheduleExpiry();
    }

    /** Ends the rent when the period runs out, so the renter's furni goes home while the room is loaded. */
    private void scheduleExpiry() {
        this.cancelExpiry();
        if (!this.isRented()) return;

        long delay = Math.max(0L, (long) (this.endTimestamp - Emulator.getIntUnixTimestamp()) * 1000L);
        this.expiry = Emulator.getThreading().run(this::expire, delay);
    }

    private void cancelExpiry() {
        if (this.expiry != null) {
            this.expiry.cancel(false);
            this.expiry = null;
        }
    }

    private void expire() {
        this.expiry = null;
        if (this.renterId <= 0 || this.isRented()) return;

        this.endRent();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room != null) room.updateItem(this);
    }

    public void endRent() {
        this.cancelExpiry();
        this.setEndTimestamp(0);

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null) return;

        Rectangle rect = RoomLayout.getRectangle(
                this.getX(),
                this.getY(),
                this.getBaseItem().getWidth(),
                this.getBaseItem().getLength(),
                this.getRotation());

        Set<UserItem> items = new HashSet<>();
        for (int i = rect.x; i < rect.x + rect.getWidth(); i++) {
            for (int j = rect.y; j < rect.y + rect.getHeight(); j++) {
                items.addAll(room.getItemsAt(i, j, this.getZ()));
            }
        }

        for (UserItem item : items) {
            // The renter's furni go home, never the space itself (a renter who owns the space used to lose it too).
            if (item != this && item.getUserId() == this.renterId) {
                room.pickUpItem(item, null);
            }
        }

        User user = Emulator.getGameEnvironment().getUserManager().getUser(this.renterId);

        if (user != null) {
            user.getUserStats().setRentedItemId(0);
            user.getUserStats().setRentedTimeEnd(0);
        } else {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "UPDATE users_settings SET rent_space_id = 0, rent_space_endtime = 0 WHERE user_id = ? LIMIT 1")) {
                statement.setInt(1, this.renterId);
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }

        this.setRenterId(0);
        this.setRenterName("");
        this.needsUpdate(true);
        this.run();
    }

    @Override
    public String getExtradata() {
        return this.renterId + ":" + this.endTimestamp;
    }

    public int getRenterId() {
        return this.renterId;
    }

    public void setRenterId(int renterId) {
        this.renterId = renterId;
    }

    public String getRenterName() {
        return this.renterName;
    }

    public void setRenterName(String renterName) {
        this.renterName = renterName;
    }

    public int getEndTimestamp() {
        return this.endTimestamp;
    }

    public void setEndTimestamp(int endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public boolean isRented() {
        return this.endTimestamp > Emulator.getIntUnixTimestamp();
    }

    /** Seconds left on the current rent, 0 when the space is free. */
    public int getSecondsRemaining() {
        return Math.max(0, this.endTimestamp - Emulator.getIntUnixTimestamp());
    }

    public static int rentDays() {
        return Emulator.getConfig().getInt(RENT_DAYS_CONFIG, DEFAULT_RENT_DAYS);
    }

    public int rentCost() {
        String[] data =
                this.getBaseItem().getName().replace("hblooza_spacerent", "").split("x");

        if (data.length == 2) {
            int x = Integer.parseInt(data[0]);
            int y = Integer.parseInt(data[1]);

            return 10 * (x * y);
        }

        return 1337;
    }

    /** Who may cancel a rent: the furni owner, the room owner and staff, as the official widget shows the button. */
    public boolean canCancelRent(User user, Room room) {
        if (user == null) return false;

        int userId = user.getUserInfo().getId();
        return this.getUserId() == userId
                || (room != null && room.getOwnerId() == userId)
                || user.hasPermission(Permission.ACC_ANYROOMOWNER);
    }

    public int getRentErrorCode(User user) {
        if (this.isRented()) {
            return RentableSpaceInfoComposer.SPACE_ALREADY_RENTED;
        }

        if (user.getUserStats().isRentingSpace() && user.getUserStats().getRentedItemId() != this.getId()) {
            return RentableSpaceInfoComposer.CAN_RENT_ONLY_ONE_SPACE;
        }

        if (user.getUserStats().getClubExpireTimestamp() < Emulator.getIntUnixTimestamp()) {
            return RentableSpaceInfoComposer.CANT_RENT_NO_CLUB;
        }

        if (!user.hasPermission(Permission.ACC_INFINITE_CREDITS)
                && this.rentCost() > user.getUserInfo().getCredits()) {
            return RentableSpaceInfoComposer.NOT_ENOUGH_CREDITS;
        }

        return 0;
    }

    public void sendRentWidget(User user) {
        user.getClient().sendResponse(new RentableSpaceInfoComposer(user, this, this.getRentErrorCode(user)));
    }
}
