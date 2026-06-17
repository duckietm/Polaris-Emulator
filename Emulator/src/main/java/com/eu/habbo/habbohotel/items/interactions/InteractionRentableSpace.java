package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.items.rentablespaces.RentableSpaceInfoComposer;
import com.eu.habbo.threading.runnables.ClearRentedSpace;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionRentableSpace extends HabboItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionRentableSpace.class);

    private int renterId;
    private String renterName;
    private int endTimestamp;

    public InteractionRentableSpace(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);

        String[] data = set.getString("extra_data").split(":");

        this.renterName = "Unknown";

        if (data.length == 2) {
            this.renterId = Integer.parseInt(data[0]);
            this.endTimestamp = Integer.parseInt(data[1]);

            if (this.renterId > 0) {
                if (this.isRented()) {
                    Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(this.renterId);

                    if (habbo != null) {
                        this.renterName = habbo.getHabboInfo().getUsername();
                    } else {
                        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT username FROM users WHERE id = ? LIMIT 1")) {
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
                } else {
                    if (this.getRoomId() > 0) {
                        Emulator.getThreading().run(new ClearRentedSpace(this, Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId())));
                        this.renterId = 0;
                    }
                }
            }
        }
    }

    public InteractionRentableSpace(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);

        this.renterName = "";
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        if (this.getExtradata().isEmpty())
            return false;

        Habbo habbo = room.getHabbo(roomUnit);

        if (habbo == null)
            return true;

        if (habbo.getHabboInfo().getId() == room.getId())
            return true;

        if (this.endTimestamp > Emulator.getIntUnixTimestamp()) {
            return this.renterId > 0 && this.renterId == habbo.getHabboInfo().getId();
        }

        return false;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        this.sendRentWidget(client.getHabbo());
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        if (this.getExtradata().isEmpty())
            this.setExtradata("0:0");

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

    public void rent(Habbo habbo) {
        if (this.isRented())
            return;

        if (habbo.getHabboStats().isRentingSpace())
            return;

        int cost = this.rentCost();
        boolean hasInfiniteCredits = habbo.hasPermission(Permission.ACC_INFINITE_CREDITS);
        if (!hasInfiniteCredits && habbo.getHabboInfo().getCredits() < cost)
            return;

        if (habbo.getHabboStats().getClubExpireTimestamp() < Emulator.getIntUnixTimestamp())
            return;

        if (!hasInfiniteCredits) {
            habbo.giveCredits(-cost);
        }

        this.setRenterId(habbo.getHabboInfo().getId());
        this.setRenterName(habbo.getHabboInfo().getUsername());
        this.setEndTimestamp(Emulator.getIntUnixTimestamp() + (7 * 86400));

        habbo.getHabboStats().setRentedItemId(this.getId());
        habbo.getHabboStats().setRentedTimeEnd(this.endTimestamp);
        this.needsUpdate(true);
        this.run();
    }

    public void endRent() {
        this.setEndTimestamp(0);

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null)
            return;

        Rectangle rect = RoomLayout.getRectangle(this.getX(), this.getY(), this.getBaseItem().getWidth(), this.getBaseItem().getLength(), this.getRotation());

        THashSet<HabboItem> items = new THashSet<>();
        for (int i = rect.x; i < rect.x + rect.getWidth(); i++) {
            for (int j = rect.y; j < rect.y + rect.getHeight(); j++) {
                items.addAll(room.getItemsAt(i, j, this.getZ()));
            }
        }

        for (HabboItem item : items) {
            if (item.getUserId() == this.renterId) {
                room.pickUpItem(item, null);
            }
        }

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(this.renterId);

        if (habbo != null) {
            habbo.getHabboStats().setRentedItemId(0);
            habbo.getHabboStats().setRentedTimeEnd(0);
        } else {
            int zero = 0;

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE users_settings SET rent_space_id = ?, rent_space_endtime = ? WHERE user_id = ? LIMIT 1")) {
                statement.setInt(1, zero);
                statement.setInt(2, zero);
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }

        //room.ejectUserFurni(this.renterId);

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

    public int rentCost() {
        String[] data = this.getBaseItem().getName().replace("hblooza_spacerent", "").split("x");

        if (data.length == 2) {
            int x = Integer.parseInt(data[0]);
            int y = Integer.parseInt(data[1]);

            return 10 * (x * y);
        }

        return 1337;
    }

    public int getRentErrorCode(Habbo habbo) {
        if (this.isRented() && this.renterId != habbo.getHabboInfo().getId()) {
            return RentableSpaceInfoComposer.SPACE_ALREADY_RENTED;
        }

        if (habbo.getHabboStats().isRentingSpace() && habbo.getHabboStats().getRentedItemId() != this.getId()) {
            return RentableSpaceInfoComposer.CAN_RENT_ONLY_ONE_SPACE;
        }

        if (habbo.getHabboStats().getClubExpireTimestamp() < Emulator.getIntUnixTimestamp()) {
            return RentableSpaceInfoComposer.CANT_RENT_NO_HABBO_CLUB;
        }

        if (this.rentCost() > habbo.getHabboInfo().getCredits()) {
            return RentableSpaceInfoComposer.NOT_ENOUGH_CREDITS;
        }

        return 0;
    }

    public void sendRentWidget(Habbo habbo) {
        habbo.getClient().sendResponse(new RentableSpaceInfoComposer(habbo, this, this.getRentErrorCode(habbo)));
    }
}
