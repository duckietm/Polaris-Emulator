package polaris.hotel.users;

import java.awt.Rectangle;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.achievements.Achievement;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.bots.Bot;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.FurnitureUsagePolicy;
import polaris.hotel.items.IEventTriggers;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionCrackable;
import polaris.hotel.items.interactions.InteractionDice;
import polaris.hotel.items.interactions.InteractionGuildGate;
import polaris.hotel.items.interactions.InteractionMultiHeight;
import polaris.hotel.items.interactions.InteractionPostIt;
import polaris.hotel.items.interactions.InteractionTeleport;
import polaris.hotel.items.interactions.InteractionTeleportTile;
import polaris.hotel.items.interactions.InteractionTrophy;
import polaris.hotel.items.interactions.InteractionWired;
import polaris.hotel.items.interactions.InteractionWiredHighscore;
import polaris.hotel.items.interactions.games.InteractionGameTimer;
import polaris.hotel.items.interactions.wired.chest.ChestRelease;
import polaris.hotel.items.rentable.RentableFurniture;
import polaris.hotel.items.rentable.RentableFurnitureManager;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomTileState;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitType;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.rooms.users.RoomUserDanceComposer;
import polaris.messages.outgoing.rooms.users.RoomUserDataComposer;
import polaris.messages.outgoing.users.UpdateUserLookComposer;

public abstract class UserItem implements Runnable, IEventTriggers {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserItem.class);

    private static Class<?>[] TOGGLING_INTERACTIONS = new Class<?>[] {
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
    private volatile boolean needsUpdate = false;
    // Bumped by every needsUpdate(true). A save clears the flag only when nothing asked for another save while it
    // wrote: the flag used to be cleared after the query, which dropped a change made during it.
    private final AtomicLong saveGeneration = new AtomicLong();
    // Saves of one item run one at a time, so an older UPDATE can no longer land after a newer one.
    private final Object saveLock = new Object();
    private boolean needsDelete = false;
    /**
     * A temporary furni (the official "place furni" wired): it has no database row and no owner, is
     * never saved or given to an inventory, and is gone when the room unloads.
     */
    private volatile boolean temporary = false;

    private int temporaryOwnerId;

    private boolean isFromGift = false;
    /** Avatars may walk underneath this item when it is raised high enough, even when the room-wide underpass setting is off. */
    private boolean allowUnderpass = false;
    /** Unix timestamp the rent period ends at, {@link RentableFurniture#NEVER} for furni owned outright. */
    private int expiresTimestamp = RentableFurniture.NEVER;
    /** The room's tile index while this furni is in a room; told about every move. */
    private volatile ItemFootprintListener footprintListener;

    public UserItem(ResultSet set, Item baseItem) throws SQLException {
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

        this.expiresTimestamp = RentableFurniture.readExpires(set);
        this.allowUnderpass = readAllowUnderpass(set);
        RentableFurnitureManager.track(this);
    }

    /** {@code items.allow_underpass}, tolerant of result sets that predate the column. */
    private static boolean readAllowUnderpass(ResultSet set) throws SQLException {
        ResultSetMetaData metaData = set.getMetaData();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            if ("allow_underpass".equalsIgnoreCase(metaData.getColumnLabel(i))) {
                return set.getInt("allow_underpass") == 1;
            }
        }
        return false;
    }

    public UserItem(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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

    public static RoomTile getSquareInFront(RoomLayout roomLayout, UserItem item) {
        return roomLayout.getTileInFront(roomLayout.getTile(item.getX(), item.getY()), item.getRotation());
    }

    public void serializeFloorData(ServerMessage serverMessage) {
        try {
            serverMessage.appendInt(BuildersClubItemIds.toWire(this.getId()));
            serverMessage.appendInt(this.baseItem.getSpriteId());
            serverMessage.appendInt(this.x);
            serverMessage.appendInt(this.y);
            serverMessage.appendInt(this.getRotation());
            serverMessage.appendString(Double.toString(this.z));

            serverMessage.appendString(
                    (this.getBaseItem().getInteractionType().getType() == InteractionTrophy.class
                                    || this.getBaseItem().getInteractionType().getType() == InteractionCrackable.class
                                    || this.getBaseItem().getName().equalsIgnoreCase("gnome_box"))
                            ? "1.0"
                            : ((this.getBaseItem().allowWalk()
                                            || this.getBaseItem().allowSit() && this.roomId != 0)
                                    ? Item.getCurrentHeight(this) + ""
                                    : ""));
            // serverMessage.appendString( ? "1.0" : ((this.getBaseItem().allowWalk() || this.getBaseItem().allowSit()
            // && this.roomId != 0) ? Item.getCurrentHeight(this) : ""));

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
        serverMessage.appendString(BuildersClubItemIds.toWire(this.getId()) + "");
        serverMessage.appendInt(this.baseItem.getSpriteId());
        serverMessage.appendString(this.wallPosition);

        if (this instanceof InteractionPostIt)
            serverMessage.appendString(this.extradata.split(" ")[0]);
        else serverMessage.appendString(this.extradata);
        serverMessage.appendInt(this.getSecondsToExpiration());
        serverMessage.appendInt(FurnitureUsagePolicy.of(this));
        serverMessage.appendInt(this.getUserId());
        serverMessage.appendInt(this.getBaseItem().allowStack() ? 1 : 0);
        serverMessage.appendInt(this.getBaseItem().allowSit() ? 1 : 0);
        serverMessage.appendInt(this.getBaseItem().allowLay() ? 1 : 0);
        serverMessage.appendInt(this.getBaseItem().allowWalk() ? 1 : 0);
        serverMessage.appendInt(this.getBaseItem().getWidth());
        serverMessage.appendInt(this.getBaseItem().getLength());
        serverMessage.appendInt(BuildersClubItemIds.toWire(this.getTeleportTargetId()));
    }

    public int getTeleportTargetId() {
        if (!(InteractionTeleport.class.isAssignableFrom(
                        this.getBaseItem().getInteractionType().getType())
                || InteractionTeleportTile.class.isAssignableFrom(
                        this.getBaseItem().getInteractionType().getType()))) {
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

    public int getDatabaseUserId() {
        return this.databaseUserId;
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

    public void setBaseItem(Item baseItem) {
        if (baseItem != null) {
            this.baseItem = baseItem;
        }
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
        this.notifyFootprintChanged();
    }

    public short getY() {
        return this.y;
    }

    public void setY(short y) {
        this.y = y;
        this.notifyFootprintChanged();
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
        this.notifyFootprintChanged();
    }

    public void setFootprintListener(ItemFootprintListener listener) {
        this.footprintListener = listener;
    }

    /** Detaches the listener only if it is still this one, so a newer room keeps its own. */
    public void clearFootprintListener(ItemFootprintListener listener) {
        if (this.footprintListener == listener) {
            this.footprintListener = null;
        }
    }

    private void notifyFootprintChanged() {
        ItemFootprintListener listener = this.footprintListener;
        if (listener != null) {
            listener.onFootprintChanged(this);
        }
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
        if (value) this.saveGeneration.incrementAndGet();
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

    public int getMaximumRotations() {
        return this.baseItem.getRotations();
    }

    public boolean isTemporary() {
        return this.temporary;
    }

    public void markTemporary() {
        this.temporary = true;
    }

    /** A temporary copy placed by a box: its owner may pick the copy up, like the room owner. */
    public void markTemporary(int ownerId) {
        this.markTemporary();
        this.temporaryOwnerId = ownerId;
    }

    /** The owner of the box that placed this temporary copy, or 0. */
    public int getTemporaryOwnerId() {
        return this.temporaryOwnerId;
    }

    @Override
    public void run() {
        synchronized (this.saveLock) {
            this.save();
        }
    }

    private void save() {
        if (this.temporary) {
            // No row to write or delete.
            this.needsUpdate = false;
            this.needsDelete = false;
            return;
        }

        if (this.needsDelete) {
            // A wired chest gives what it holds back to its owner before its row goes.
            ChestRelease.releaseToOwner(this);
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            if (this.needsDelete) {
                this.needsUpdate = false;
                this.needsDelete = false;

                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM items WHERE id = ?")) {
                    statement.setInt(1, this.getId());
                    statement.execute();
                }
            } else if (this.needsUpdate) {
                try (PreparedStatement statement = connection.prepareStatement(UPDATE_ROW_SQL)) {
                    long generation = this.bindRow(statement);
                    statement.execute();
                    // Cleared only once written: a failed update used to be forgotten until the next change.
                    this.markSaved(generation);
                } catch (SQLException e) {
                    LOGGER.error("Caught SQL exception", e);
                    LOGGER.error("SQLException trying to save item: {}", this.toString());
                }
            }

        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    /** The one statement that writes an item's row; {@link #bindRow} fills it. */
    public static final String UPDATE_ROW_SQL =
            "UPDATE items SET user_id = ?, room_id = ?, wall_pos = ?, x = ?, y = ?, z = ?, rot = ?, extra_data = ?, limited_data = ?, expires = ?, allow_underpass = ? WHERE id = ?";

    /**
     * Binds this item's columns to {@link #UPDATE_ROW_SQL}, for a single save or a batch, and returns the change
     * generation they reflect, to hand to {@link #markSaved} once the row is written.
     */
    public long bindRow(PreparedStatement statement) throws SQLException {
        long generation = this.saveGeneration.get();
        statement.setInt(1, this.databaseUserId);
        statement.setInt(2, this.roomId);
        statement.setString(3, this.wallPosition);
        statement.setInt(4, this.x);
        statement.setInt(5, this.y);
        statement.setDouble(6, Math.max(-9999, Math.min(9999, Math.round(this.z * Math.pow(10, 6)) / Math.pow(10, 6))));
        statement.setInt(7, this.rotation);
        statement.setString(8, this instanceof InteractionGuildGate ? "" : this.getDatabaseExtraData());
        statement.setString(9, this.limitedStack + ":" + this.limitedSells);
        statement.setInt(10, this.expiresTimestamp);
        statement.setInt(11, this.allowUnderpass ? 1 : 0);
        statement.setInt(12, this.id);
        return generation;
    }

    /** The row written by {@link #bindRow} is saved, unless the item changed again since. */
    public void markSaved(long generation) {
        if (this.saveGeneration.get() == generation) this.needsUpdate = false;
    }

    /** Unix timestamp the rent period ends at, {@link RentableFurniture#NEVER} when the furni is owned outright. */
    public int getExpiresTimestamp() {
        return this.expiresTimestamp;
    }

    public void setExpiresTimestamp(int expiresTimestamp) {
        this.expiresTimestamp = expiresTimestamp;
    }

    /** True when avatars may walk underneath this item regardless of the room-wide underpass setting. */
    public boolean isAllowUnderpass() {
        return this.allowUnderpass;
    }

    public void setAllowUnderpass(boolean allowUnderpass) {
        this.allowUnderpass = allowUnderpass;
    }

    /** True for furni bought through a rent offer that has not been bought out. */
    public boolean hasRentPeriod() {
        return RentableFurniture.hasRentPeriod(this.expiresTimestamp);
    }

    /** Seconds left on the rent period as the client expects them, -1 for furni owned outright. */
    public int getSecondsToExpiration() {
        return RentableFurniture.secondsToExpiration(this.expiresTimestamp, Emulator.getIntUnixTimestamp());
    }

    public abstract boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects);

    /**
     * "State changed" for a furni whose state a user changed outside the plain toggle (a dice roll, a vending
     * machine, a random-state furni), as in Habbo. Behaviour observed in plus-emulator/PlusEMU 54c5c942 and 72f46ba0,
     * reimplemented.
     */
    protected static void notifyStateChanged(Room room, RoomUnit unit, UserItem item) {
        WiredManager.triggerFurniStateChanged(room, unit, item);
        WiredManager.triggerFurniStateUpdated(room, unit, item, false);
    }

    public abstract boolean isWalkable();

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client != null && this.getBaseItem().getType() == FurnitureType.FLOOR) {
            if (objects != null && objects.length >= 2) {
                if (objects[1] instanceof WiredEffectType) {
                    return;
                }
            }

            boolean isTogglingInteraction = Arrays.stream(UserItem.TOGGLING_INTERACTIONS)
                    .anyMatch(type -> type.isAssignableFrom(this.getClass()));

            if ((this.getBaseItem().getStateCount() > 1 && !(this instanceof InteractionDice))
                    || isTogglingInteraction
                    || (objects != null && objects.length == 1 && objects[0].equals("TOGGLE_OVERRIDE"))) {
                WiredManager.triggerFurniStateChanged(room, client.getUser().getRoomUnit(), this);
                WiredManager.triggerFurniStateUpdated(room, client.getUser().getRoomUnit(), this, false);
            }
        }
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        /*if (objects != null && objects.length >= 1 && objects[0] instanceof InteractionWired)
        return;*/

        WiredManager.triggerUserWalksOn(room, roomUnit, this);
        if (roomUnit != null && roomUnit.getRoomUnitType() == RoomUnitType.BOT) {
            WiredManager.triggerBotReachedFurni(room, roomUnit, this);
        }

        if ((this.getBaseItem().allowSit() || this.getBaseItem().allowLay())
                && !roomUnit.getDanceType().equals(DanceType.NONE)) {
            roomUnit.setDanceType(DanceType.NONE);
            room.sendComposer(new RoomUserDanceComposer(roomUnit).compose());
        }

        if (!this.getBaseItem().getClothingOnWalk().isEmpty()
                && roomUnit.getPreviousLocation() != roomUnit.getGoal()
                && roomUnit.getGoal() == room.getLayout().getTile(this.x, this.y)) {
            User user = room.getUser(roomUnit);

            if (user != null && user.getClient() != null) {
                String[] clothingKeys = Arrays.stream(
                                this.getBaseItem().getClothingOnWalk().split("\\."))
                        .map(k -> k.split("-")[0])
                        .toArray(String[]::new);
                user.getUserInfo()
                        .setLook(String.join(
                                        ".",
                                        Arrays.stream(user.getUserInfo()
                                                        .getLook()
                                                        .split("\\."))
                                                .filter(k -> !ArrayUtils.contains(clothingKeys, k.split("-")[0]))
                                                .toArray(String[]::new))
                                + "." + this.getBaseItem().getClothingOnWalk());

                user.getClient().sendResponse(new UpdateUserLookComposer(user));
                if (user.getUserInfo().getCurrentRoom() != null) {
                    user.getUserInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(user).compose());
                }
            }
        }
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        if (objects != null && objects.length > 0) {
            WiredManager.triggerUserWalksOff(room, roomUnit, this);
        }
    }

    public abstract void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception;

    public void onPlace(Room room) {
        // TODO: IMPORTANT: MAKE THIS GENERIC. (HOLES, ICE SKATE PATCHES, BLACK HOLE, BUNNY RUN FIELD, FOOTBALL FIELD)
        Achievement roomDecoAchievement =
                Emulator.getGameEnvironment().getAchievementManager().getAchievement("RoomDecoFurniCount");
        User owner = room.getUser(this.getUserId());

        int furniCollecterProgress;
        if (owner == null) {
            furniCollecterProgress =
                    AchievementManager.getAchievementProgressForUser(this.getUserId(), roomDecoAchievement);
        } else {
            furniCollecterProgress = owner.getUserStats().getAchievementProgress(roomDecoAchievement);
        }

        int difference = room.getUserFurniCount(this.getUserId()) - furniCollecterProgress;
        if (difference > 0) {
            if (owner != null) {
                AchievementManager.progressAchievement(owner, roomDecoAchievement, difference);
            } else {
                AchievementManager.progressAchievement(this.getUserId(), roomDecoAchievement, difference);
            }
        }

        Achievement roomDecoUniqueAchievement =
                Emulator.getGameEnvironment().getAchievementManager().getAchievement("RoomDecoFurniTypeCount");

        int uniqueFurniCollecterProgress;
        if (owner == null) {
            uniqueFurniCollecterProgress =
                    AchievementManager.getAchievementProgressForUser(this.getUserId(), roomDecoUniqueAchievement);
        } else {
            uniqueFurniCollecterProgress = owner.getUserStats().getAchievementProgress(roomDecoUniqueAchievement);
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
            UserItem topItem2 = room.getTopItemAt(this.getX(), this.getY(), this);
            int nextEffectM = 0;
            int nextEffectF = 0;

            if (topItem2 != null) {
                nextEffectM = topItem2.getBaseItem().getEffectM();
                nextEffectF = topItem2.getBaseItem().getEffectF();
            }

            for (User user : room.getUsersOnItem(this)) {
                if (this.getBaseItem().getEffectM() > 0
                        && user.getUserInfo().getGender().equals(UserGender.M)
                        && user.getRoomUnit().getEffectId()
                                == this.getBaseItem().getEffectM()) {
                    room.giveEffect(user, nextEffectM, -1);
                }

                if (this.getBaseItem().getEffectF() > 0
                        && user.getUserInfo().getGender().equals(UserGender.F)
                        && user.getRoomUnit().getEffectId()
                                == this.getBaseItem().getEffectF()) {
                    room.giveEffect(user, nextEffectF, -1);
                }
            }

            for (Bot bot : room.getBotsAt(room.getLayout().getTile(this.getX(), this.getY()))) {
                if (this.getBaseItem().getEffectM() > 0
                        && bot.getGender().equals(UserGender.M)
                        && bot.getRoomUnit().getEffectId() == this.getBaseItem().getEffectM()) {
                    room.giveEffect(bot.getRoomUnit(), nextEffectM, -1);
                }

                if (this.getBaseItem().getEffectF() > 0
                        && bot.getGender().equals(UserGender.F)
                        && bot.getRoomUnit().getEffectId() == this.getBaseItem().getEffectF()) {
                    room.giveEffect(bot.getRoomUnit(), nextEffectF, -1);
                }
            }
        }
    }

    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        if (this.getBaseItem().getEffectF() > 0 || this.getBaseItem().getEffectM() > 0) {
            UserItem topItem2 = room.getTopItemAt(oldLocation.x, oldLocation.y, this);
            int nextEffectM = 0;
            int nextEffectF = 0;

            if (topItem2 != null) {
                nextEffectM = topItem2.getBaseItem().getEffectM();
                nextEffectF = topItem2.getBaseItem().getEffectF();
            }

            List<User> oldUsers = new ArrayList<>();
            List<User> newUsers = new ArrayList<>();
            List<Bot> oldBots = new ArrayList<>();
            List<Bot> newBots = new ArrayList<>();

            for (RoomTile tile : room.getLayout()
                    .getTilesAt(
                            oldLocation,
                            this.getBaseItem().getWidth(),
                            this.getBaseItem().getLength(),
                            this.getRotation())) {
                oldUsers.addAll(room.getUsersAt(tile));
                oldBots.addAll(room.getBotsAt(tile));
            }

            for (RoomTile tile : room.getLayout()
                    .getTilesAt(
                            oldLocation,
                            this.getBaseItem().getWidth(),
                            this.getBaseItem().getLength(),
                            this.getRotation())) {
                newUsers.addAll(room.getUsersAt(tile));
                newBots.addAll(room.getBotsAt(tile));
            }

            oldUsers.removeAll(newUsers);
            oldBots.removeAll(newBots);

            for (User user : oldUsers) {
                if (this.getBaseItem().getEffectM() > 0
                        && user.getUserInfo().getGender().equals(UserGender.M)
                        && user.getRoomUnit().getEffectId()
                                == this.getBaseItem().getEffectM()) {
                    room.giveEffect(user, nextEffectM, -1);
                }

                if (this.getBaseItem().getEffectF() > 0
                        && user.getUserInfo().getGender().equals(UserGender.F)
                        && user.getRoomUnit().getEffectId()
                                == this.getBaseItem().getEffectF()) {
                    room.giveEffect(user, nextEffectF, -1);
                }
            }

            for (User user : newUsers) {
                if (this.getBaseItem().getEffectM() > 0
                        && user.getUserInfo().getGender().equals(UserGender.M)
                        && user.getRoomUnit().getEffectId()
                                != this.getBaseItem().getEffectM()) {
                    room.giveEffect(user, this.getBaseItem().getEffectM(), -1);
                }

                if (this.getBaseItem().getEffectF() > 0
                        && user.getUserInfo().getGender().equals(UserGender.F)
                        && user.getRoomUnit().getEffectId()
                                != this.getBaseItem().getEffectF()) {
                    room.giveEffect(user, this.getBaseItem().getEffectF(), -1);
                }
            }

            for (Bot bot : oldBots) {
                if (this.getBaseItem().getEffectM() > 0
                        && bot.getGender().equals(UserGender.M)
                        && bot.getRoomUnit().getEffectId() == this.getBaseItem().getEffectM()) {
                    room.giveEffect(bot.getRoomUnit(), nextEffectM, -1);
                }

                if (this.getBaseItem().getEffectF() > 0
                        && bot.getGender().equals(UserGender.F)
                        && bot.getRoomUnit().getEffectId() == this.getBaseItem().getEffectF()) {
                    room.giveEffect(bot.getRoomUnit(), nextEffectF, -1);
                }
            }

            for (Bot bot : newBots) {
                if (this.getBaseItem().getEffectM() > 0
                        && bot.getGender().equals(UserGender.M)
                        && bot.getRoomUnit().getEffectId() != this.getBaseItem().getEffectM()) {
                    room.giveEffect(bot.getRoomUnit(), this.getBaseItem().getEffectM(), -1);
                }

                if (this.getBaseItem().getEffectF() > 0
                        && bot.getGender().equals(UserGender.F)
                        && bot.getRoomUnit().getEffectId() != this.getBaseItem().getEffectF()) {
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
        return "ID: " + this.id + ", BaseID: " + this.getBaseItem().getId() + ", X: " + this.x + ", Y: " + this.y
                + ", Z: " + this.z + ", Extradata: " + this.extradata;
    }

    public boolean allowWiredResetState() {
        return false;
    }

    public boolean isUsable() {
        return this.baseItem.getStateCount() > 1;
    }

    public boolean canStackAt(Room room, List<Pair<RoomTile, Set<UserItem>>> itemsAtLocation) {
        return true;
    }

    public boolean isFromGift() {
        return isFromGift;
    }

    public void setFromGift(boolean fromGift) {
        isFromGift = fromGift;
    }

    public boolean invalidatesToRoomKick() {
        return false;
    }

    public List<RoomTile> getOccupyingTiles(RoomLayout layout) {
        List<RoomTile> tiles = new ArrayList<>();

        Rectangle rect = RoomLayout.getRectangle(
                this.getX(),
                this.getY(),
                this.getBaseItem().getWidth(),
                this.getBaseItem().getLength(),
                this.getRotation());

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
