package com.eu.habbo.habbohotel.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.IEventTriggers;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.*;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDanceComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;
import com.eu.habbo.messages.outgoing.users.UpdateUserLookComposer;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public abstract class HabboItem implements Runnable, IEventTriggers {

    private static final Logger LOGGER = LoggerFactory.getLogger(HabboItem.class);

    private static Class<?>[] TOGGLING_INTERACTIONS = new Class<?>[]{
            InteractionGameTimer.class,
            InteractionWired.class,
            InteractionWiredHighscore.class,
            InteractionMultiHeight.class
    };

    private int id;
    private int userId;
    private int databaseUserId;
    private int roomId;
    private Item baseItem;
    private String wallPosition;
    private short x;
    private short y;
    private double z;
    private int rotation;
    private String extradata;
    private int limitedStack;
    private int limitedSells;
    private boolean needsUpdate = false;
    private boolean needsDelete = false;
    private boolean isFromGift = false;

    public HabboItem(ResultSet set, Item baseItem) throws SQLException {
        this.id = set.getInt("id");
        this.userId = set.getInt("user_id");
        this.databaseUserId = this.userId;
        this.roomId = set.getInt("room_id");
        this.baseItem = baseItem;
        this.wallPosition = set.getString("wall_pos");
        this.x = set.getShort("x");
        this.y = set.getShort("y");
        this.z = set.getDouble("z");
        this.rotation = set.getInt("rot");
        this.extradata = set.getString("extra_data").isEmpty() ? "0" : set.getString("extra_data");

        String ltdData = set.getString("limited_data");
        if (!ltdData.isEmpty()) {
            this.limitedStack = Integer.parseInt(set.getString("limited_data").split(":")[0]);
            this.limitedSells = Integer.parseInt(set.getString("limited_data").split(":")[1]);
        }
    }

    public HabboItem(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        this.id = id;
        this.userId = userId;
        this.databaseUserId = userId;
        this.roomId = 0;
        this.baseItem = item;
        this.wallPosition = "";
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.rotation = 0;
        this.extradata = extradata.isEmpty() ? "0" : extradata;
        this.limitedSells = limitedSells;
        this.limitedStack = limitedStack;
    }

    public static RoomTile getSquareInFront(RoomLayout roomLayout, HabboItem item) {
        return roomLayout.getTileInFront(roomLayout.getTile(item.getX(), item.getY()), item.getRotation());
    }

    public void serializeFloorData(ServerMessage serverMessage) {
        try {
            serverMessage.appendInt(this.getId());
            serverMessage.appendInt(this.baseItem.getSpriteId());
            serverMessage.appendInt(this.x);
            serverMessage.appendInt(this.y);
            serverMessage.appendInt(this.getRotation());
            serverMessage.appendString(Double.toString(this.z));

            serverMessage.appendString((this.getBaseItem().getInteractionType().getType() == InteractionTrophy.class || this.getBaseItem().getInteractionType().getType() == InteractionCrackable.class || this.getBaseItem().getName().equalsIgnoreCase("gnome_box")) ? "1.0" : ((this.getBaseItem().allowWalk() || this.getBaseItem().allowSit() && this.roomId != 0) ? Item.getCurrentHeight(this) + "" : ""));
            //serverMessage.appendString( ? "1.0" : ((this.getBaseItem().allowWalk() || this.getBaseItem().allowSit() && this.roomId != 0) ? Item.getCurrentHeight(this) : ""));

        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }

    public void serializeExtradata(ServerMessage serverMessage) {
        if (this.isLimited()) {
            serverMessage.appendInt(this.getLimitedSells());
            serverMessage.appendInt(this.getLimitedStack());
        }
    }

    public void serializeWallData(ServerMessage serverMessage) {
        serverMessage.appendString(this.getId() + "");
        serverMessage.appendInt(this.baseItem.getSpriteId());
        serverMessage.appendString(this.wallPosition);

        if (this instanceof InteractionPostIt)
            serverMessage.appendString(this.extradata.split(" ")[0]);
        else
            serverMessage.appendString(this.extradata);
        serverMessage.appendInt(-1);
        serverMessage.appendInt(this.isUsable());
        serverMessage.appendInt(this.getUserId());
        serverMessage.appendInt(this.getBaseItem().allowStack() ? 1 : 0);
        serverMessage.appendInt(this.getBaseItem().allowSit() ? 1 : 0);
        serverMessage.appendInt(this.getBaseItem().allowLay() ? 1 : 0);
        serverMessage.appendInt(this.getBaseItem().allowWalk() ? 1 : 0);
        serverMessage.appendInt(this.getBaseItem().getWidth());
        serverMessage.appendInt(this.getBaseItem().getLength());
        serverMessage.appendInt(this.getTeleportTargetId());
    }

    public int getTeleportTargetId() {
        if (!(InteractionTeleport.class.isAssignableFrom(this.getBaseItem().getInteractionType().getType())
            || InteractionTeleportTile.class.isAssignableFrom(this.getBaseItem().getInteractionType().getType()))) {
            return 0;
        }

        int[] target = Emulator.getGameEnvironment().getItemManager().getTargetTeleportRoomId(this);

        return (target.length >= 2) ? target[1] : 0;
    }

    public int getId() {
        return this.id;
    }

    public int getGiftAdjustedId() {
        if (this.isFromGift) return -this.id;

        return this.id;
    }

    public int getUserId() {
        return this.userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
        this.databaseUserId = userId;
    }

    public void setVirtualUserId(int userId) {
        this.userId = userId;
    }

    public int getRoomId() {
        return this.roomId;
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }

    public Item getBaseItem() {
        return this.baseItem;
    }

    public String getWallPosition() {
        return this.wallPosition;
    }

    public void setWallPosition(String wallPosition) {
        this.wallPosition = wallPosition;
    }

    public short getX() {
        return this.x;
    }

    public void setX(short x) {
        this.x = x;
    }

    public short getY() {
        return this.y;
    }

    public void setY(short y) {
        this.y = y;
    }

    public double getZ() {
        return this.z;
    }

    public void setZ(double z) {
        if (z > 9999 || z < -9999) return;
        this.z = z;
    }

    public int getRotation() {
        return this.rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = (byte) (rotation % 8);
    }

    public String getExtradata() {
        return this.extradata;
    }

    public void setExtradata(String extradata) {
        this.extradata = extradata;
    }

    public boolean needsUpdate() {
        return this.needsUpdate;
    }

    public boolean needsDelete() {
        return needsDelete;
    }

    public void needsUpdate(boolean value) {
        this.needsUpdate = value;
    }

    public void needsDelete(boolean value) {
        this.needsDelete = value;
    }

    public boolean isLimited() {
        return this.limitedStack > 0;
    }

    public int getLimitedStack() {
        return this.limitedStack;
    }

    public int getLimitedSells() {
        return this.limitedSells;
    }

    public int getMaximumRotations() { return this.baseItem.getRotations(); }

    @Override
    public void run() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            if (this.needsDelete) {
                this.needsUpdate = false;
                this.needsDelete = false;

                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM items WHERE id = ?")) {
                    statement.setInt(1, this.getId());
                    statement.execute();
                }
            } else if (this.needsUpdate) {
                try (PreparedStatement statement = connection.prepareStatement("UPDATE items SET user_id = ?, room_id = ?, wall_pos = ?, x = ?, y = ?, z = ?, rot = ?, extra_data = ?, limited_data = ? WHERE id = ?")) {
                    statement.setInt(1, this.databaseUserId);
                    statement.setInt(2, this.roomId);
                    statement.setString(3, this.wallPosition);
                    statement.setInt(4, this.x);
                    statement.setInt(5, this.y);
                    statement.setDouble(6, Math.max(-9999, Math.min(9999, Math.round(this.z * Math.pow(10, 6)) / Math.pow(10, 6))));
                    statement.setInt(7, this.rotation);
                    statement.setString(8, this instanceof InteractionGuildGate ? "" : this.getDatabaseExtraData());
                    statement.setString(9, this.limitedStack + ":" + this.limitedSells);
                    statement.setInt(10, this.id);
                    statement.execute();
                } catch (SQLException e) {
                    LOGGER.error("Caught SQL exception", e);
                    LOGGER.error("SQLException trying to save HabboItem: {}", this.toString());
                }

                this.needsUpdate = false;
            }

        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public abstract boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects);

    public abstract boolean isWalkable();

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client != null && this.getBaseItem().getType() == FurnitureType.FLOOR) {
            if (objects != null && objects.length >= 2) {
                if (objects[1] instanceof WiredEffectType) {
                    return;
                }
            }

            boolean isTogglingInteraction = Arrays.stream(HabboItem.TOGGLING_INTERACTIONS)
                    .anyMatch(type -> type.isAssignableFrom(this.getClass()));

            if ((this.getBaseItem().getStateCount() > 1 && !(this instanceof InteractionDice)) || isTogglingInteraction || (objects != null && objects.length == 1 && objects[0].equals("TOGGLE_OVERRIDE"))) {
                WiredManager.triggerFurniStateChanged(room, client.getHabbo().getRoomUnit(), this);
            }
        }
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        /*if (objects != null && objects.length >= 1 && objects[0] instanceof InteractionWired)
            return;*/

        WiredManager.triggerUserWalksOn(room, roomUnit, this);

        if ((this.getBaseItem().allowSit() || this.getBaseItem().allowLay()) && !roomUnit.getDanceType().equals(DanceType.NONE)) {
            roomUnit.setDanceType(DanceType.NONE);
            room.sendComposer(new RoomUserDanceComposer(roomUnit).compose());
        }

        if (!this.getBaseItem().getClothingOnWalk().isEmpty() && roomUnit.getPreviousLocation() != roomUnit.getGoal() && roomUnit.getGoal() == room.getLayout().getTile(this.x, this.y)) {
            Habbo habbo = room.getHabbo(roomUnit);

            if (habbo != null && habbo.getClient() != null) {
                String[] clothingKeys = Arrays.stream(this.getBaseItem().getClothingOnWalk().split("\\.")).map(k -> k.split("-")[0]).toArray(String[]::new);
                habbo.getHabboInfo().setLook(String.join(".", Arrays.stream(habbo.getHabboInfo().getLook().split("\\.")).filter(k -> !ArrayUtils.contains(clothingKeys, k.split("-")[0])).toArray(String[]::new)) + "." + this.getBaseItem().getClothingOnWalk());

                habbo.getClient().sendResponse(new UpdateUserLookComposer(habbo));
                if (habbo.getHabboInfo().getCurrentRoom() != null) {
                    habbo.getHabboInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(habbo).compose());
                }
            }
        }
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        if(objects != null && objects.length > 0) {
            WiredManager.triggerUserWalksOff(room, roomUnit, this);
        }
    }

    public abstract void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception;


    public void onPlace(Room room) {
        //TODO: IMPORTANT: MAKE THIS GENERIC. (HOLES, ICE SKATE PATCHES, BLACK HOLE, BUNNY RUN FIELD, FOOTBALL FIELD)
        Achievement roomDecoAchievement = Emulator.getGameEnvironment().getAchievementManager().getAchievement("RoomDecoFurniCount");
        Habbo owner = room.getHabbo(this.getUserId());

        int furniCollecterProgress;
        if (owner == null) {
            furniCollecterProgress = AchievementManager.getAchievementProgressForHabbo(this.getUserId(), roomDecoAchievement);
        } else {
            furniCollecterProgress = owner.getHabboStats().getAchievementProgress(roomDecoAchievement);
        }

        int difference = room.getUserFurniCount(this.getUserId()) - furniCollecterProgress;
        if (difference > 0) {
            if (owner != null) {
                AchievementManager.progressAchievement(owner, roomDecoAchievement, difference);
            } else {
                AchievementManager.progressAchievement(this.getUserId(), roomDecoAchievement, difference);
            }
        }

        Achievement roomDecoUniqueAchievement = Emulator.getGameEnvironment().getAchievementManager().getAchievement("RoomDecoFurniTypeCount");

        int uniqueFurniCollecterProgress;
        if (owner == null) {
            uniqueFurniCollecterProgress = AchievementManager.getAchievementProgressForHabbo(this.getUserId(), roomDecoUniqueAchievement);
        } else {
            uniqueFurniCollecterProgress = owner.getHabboStats().getAchievementProgress(roomDecoUniqueAchievement);
        }

        int uniqueDifference = room.getUserUniqueFurniCount(this.getUserId()) - uniqueFurniCollecterProgress;
        if (uniqueDifference > 0) {
            if (owner != null) {
                AchievementManager.progressAchievement(owner, roomDecoUniqueAchievement, uniqueDifference);
            } else {
                AchievementManager.progressAchievement(this.getUserId(), roomDecoUniqueAchievement, uniqueDifference);
            }
        }
    }

    public void onPickUp(Room room) {
        if (this.getBaseItem().getEffectF() > 0 || this.getBaseItem().getEffectM() > 0) {
            HabboItem topItem2 = room.getTopItemAt(this.getX(), this.getY(), this);
            int nextEffectM = 0;
            int nextEffectF = 0;

            if(topItem2 != null) {
                nextEffectM = topItem2.getBaseItem().getEffectM();
                nextEffectF = topItem2.getBaseItem().getEffectF();
            }

            for (Habbo habbo : room.getHabbosOnItem(this)) {
                if (this.getBaseItem().getEffectM() > 0 && habbo.getHabboInfo().getGender().equals(HabboGender.M) && habbo.getRoomUnit().getEffectId() == this.getBaseItem().getEffectM()) {
                    room.giveEffect(habbo, nextEffectM, -1);
                }

                if (this.getBaseItem().getEffectF() > 0 && habbo.getHabboInfo().getGender().equals(HabboGender.F) && habbo.getRoomUnit().getEffectId() == this.getBaseItem().getEffectF()) {
                    room.giveEffect(habbo, nextEffectF, -1);
                }
            }

            for (Bot bot : room.getBotsAt(room.getLayout().getTile(this.getX(), this.getY()))) {
                if (this.getBaseItem().getEffectM() > 0 && bot.getGender().equals(HabboGender.M) && bot.getRoomUnit().getEffectId() == this.getBaseItem().getEffectM()) {
                    room.giveEffect(bot.getRoomUnit(), nextEffectM, -1);
                }

                if (this.getBaseItem().getEffectF() > 0 && bot.getGender().equals(HabboGender.F) && bot.getRoomUnit().getEffectId() == this.getBaseItem().getEffectF()) {
                    room.giveEffect(bot.getRoomUnit(), nextEffectF, -1);
                }
            }
        }
    }

    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        if (this.getBaseItem().getEffectF() > 0 || this.getBaseItem().getEffectM() > 0) {
            HabboItem topItem2 = room.getTopItemAt(oldLocation.x, oldLocation.y, this);
            int nextEffectM = 0;
            int nextEffectF = 0;

            if(topItem2 != null) {
                nextEffectM = topItem2.getBaseItem().getEffectM();
                nextEffectF = topItem2.getBaseItem().getEffectF();
            }

            List<Habbo> oldHabbos = new ArrayList<>();
            List<Habbo> newHabbos = new ArrayList<>();
            List<Bot> oldBots = new ArrayList<>();
            List<Bot> newBots = new ArrayList<>();

            for (RoomTile tile : room.getLayout().getTilesAt(oldLocation, this.getBaseItem().getWidth(), this.getBaseItem().getLength(), this.getRotation())) {
                oldHabbos.addAll(room.getHabbosAt(tile));
                oldBots.addAll(room.getBotsAt(tile));
            }

            for (RoomTile tile : room.getLayout().getTilesAt(oldLocation, this.getBaseItem().getWidth(), this.getBaseItem().getLength(), this.getRotation())) {
                newHabbos.addAll(room.getHabbosAt(tile));
                newBots.addAll(room.getBotsAt(tile));
            }

            oldHabbos.removeAll(newHabbos);
            oldBots.removeAll(newBots);

            for (Habbo habbo : oldHabbos) {
                if (this.getBaseItem().getEffectM() > 0 && habbo.getHabboInfo().getGender().equals(HabboGender.M) && habbo.getRoomUnit().getEffectId() == this.getBaseItem().getEffectM()) {
                    room.giveEffect(habbo, nextEffectM, -1);
                }

                if (this.getBaseItem().getEffectF() > 0 && habbo.getHabboInfo().getGender().equals(HabboGender.F) && habbo.getRoomUnit().getEffectId() == this.getBaseItem().getEffectF()) {
                    room.giveEffect(habbo, nextEffectF, -1);
                }
            }

            for (Habbo habbo : newHabbos) {
                if (this.getBaseItem().getEffectM() > 0 && habbo.getHabboInfo().getGender().equals(HabboGender.M) && habbo.getRoomUnit().getEffectId() != this.getBaseItem().getEffectM()) {
                    room.giveEffect(habbo, this.getBaseItem().getEffectM(), -1);
                }

                if (this.getBaseItem().getEffectF() > 0 && habbo.getHabboInfo().getGender().equals(HabboGender.F) && habbo.getRoomUnit().getEffectId() != this.getBaseItem().getEffectF()) {
                    room.giveEffect(habbo, this.getBaseItem().getEffectF(), -1);
                }
            }

            for (Bot bot : oldBots) {
                if (this.getBaseItem().getEffectM() > 0 && bot.getGender().equals(HabboGender.M) && bot.getRoomUnit().getEffectId() == this.getBaseItem().getEffectM()) {
                    room.giveEffect(bot.getRoomUnit(), nextEffectM, -1);
                }

                if (this.getBaseItem().getEffectF() > 0 && bot.getGender().equals(HabboGender.F) && bot.getRoomUnit().getEffectId() == this.getBaseItem().getEffectF()) {
                    room.giveEffect(bot.getRoomUnit(), nextEffectF, -1);
                }
            }

            for (Bot bot : newBots) {
                if (this.getBaseItem().getEffectM() > 0 && bot.getGender().equals(HabboGender.M) && bot.getRoomUnit().getEffectId() != this.getBaseItem().getEffectM()) {
                    room.giveEffect(bot.getRoomUnit(), this.getBaseItem().getEffectM(), -1);
                }

                if (this.getBaseItem().getEffectF() > 0 && bot.getGender().equals(HabboGender.F) && bot.getRoomUnit().getEffectId() != this.getBaseItem().getEffectF()) {
                    room.giveEffect(bot.getRoomUnit(), this.getBaseItem().getEffectF(), -1);
                }
            }
        }
    }

    public String getDatabaseExtraData() {
        return this.getExtradata();
    }

    @Override
    public String toString() {
        return "ID: " + this.id + ", BaseID: " + this.getBaseItem().getId() + ", X: " + this.x + ", Y: " + this.y + ", Z: " + this.z + ", Extradata: " + this.extradata;
    }

    public boolean allowWiredResetState() {
        return false;
    }

    public boolean isUsable() {
        return this.baseItem.getStateCount() > 1;
    }

    public boolean canStackAt(Room room, List<Pair<RoomTile, Set<HabboItem>>> itemsAtLocation) {
        return true;
    }

    public boolean isFromGift() {
        return isFromGift;
    }

    public void setFromGift(boolean fromGift) {
        isFromGift = fromGift;
    }

    public boolean invalidatesToRoomKick() { return false; }

    public List<RoomTile> getOccupyingTiles(RoomLayout layout) {
        List<RoomTile> tiles = new ArrayList<>();

        Rectangle rect = RoomLayout.getRectangle(this.getX(), this.getY(), this.getBaseItem().getWidth(), this.getBaseItem().getLength(), this.getRotation());

        for (int i = rect.x; i < rect.x + rect.getWidth(); i++) {
            for (int j = rect.y; j < rect.y + rect.getHeight(); j++) {
                tiles.add(layout.getTile((short) i, (short) j));
            }
        }

        return tiles;
    }

    public RoomTile getOverrideGoalTile(RoomUnit unit, Room room, RoomTile tile) {
        return tile;
    }

    public RoomTileState getOverrideTileState(RoomTile tile, Room room) {
        return null;
    }

    public boolean canOverrideTile(RoomUnit unit, Room room, RoomTile tile) {
        return false;
    }

    public Rectangle getRectangle() {
        return RoomLayout.getRectangle(
                this.getX(),
                this.getY(),
                this.getBaseItem().getWidth(),
                this.getBaseItem().getLength(),
                this.getRotation());
    }

    public Rectangle getRectangle(int marginX, int marginY) {
        return RoomLayout.getRectangle(
                this.getX() - marginX,
                this.getY() - marginY,
                this.getBaseItem().getWidth() + (marginX * 2),
                this.getBaseItem().getLength() + (marginY * 2),
                this.getRotation());
    }
}
