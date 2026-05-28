package com.eu.habbo.habbohotel.bots;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.outgoing.rooms.users.*;
import com.eu.habbo.plugin.events.bots.BotChatEvent;
import com.eu.habbo.plugin.events.bots.BotShoutEvent;
import com.eu.habbo.plugin.events.bots.BotTalkEvent;
import com.eu.habbo.plugin.events.bots.BotWhisperEvent;
import com.eu.habbo.threading.runnables.BotFollowHabbo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;

public class Bot implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Bot.class);

    public static final String NO_CHAT_SET = "${bot.skill.chatter.configuration.text.placeholder}";
    public static String[] PLACEMENT_MESSAGES = "Yo!;Hello I'm a real party animal!;Hello!".split(";");
    public static boolean BOT_LIMIT_WALKING_DISTANCE = true;
    public static int BOT_WALKING_DISTANCE_RADIUS = 5;

    private final ArrayList<String> chatLines;
    private transient int id;
    private String name;
    private String motto;
    private String figure;
    private HabboGender gender;
    private int ownerId;
    private String ownerName;
    private Room room;
    private RoomUnit roomUnit;
    private boolean chatAuto;
    private boolean chatRandom;
    private short chatDelay;
    private int chatTimeOut;
    private int chatTimestamp;
    private int lastChatIndex;
    private int bubble;


    private final String type;


    private int effect;

    private transient boolean canWalk = true;


    private boolean needsUpdate;


    private transient int followingHabboId;

    public Bot(int id, String name, String motto, String figure, HabboGender gender, int ownerId, String ownerName) {
        this.id = id;
        this.name = name;
        this.motto = motto;
        this.figure = figure;
        this.gender = gender;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.chatAuto = false;
        this.chatRandom = false;
        this.chatDelay = 1000;
        this.chatLines = new ArrayList<>();
        this.type = "generic_bot";
        this.room = null;
        this.bubble = RoomChatMessageBubbles.BOT_RENTABLE.getType();
    }

    public Bot(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.name = set.getString("name");
        this.motto = set.getString("motto");
        this.figure = set.getString("figure");
        this.gender = HabboGender.valueOf(set.getString("gender"));
        this.ownerId = set.getInt("user_id");
        this.ownerName = set.getString("owner_name");
        this.chatAuto = set.getString("chat_auto").equals("1");
        this.chatRandom = set.getString("chat_random").equals("1");
        this.chatDelay = set.getShort("chat_delay");
        this.chatLines = new ArrayList<>(Arrays.asList(set.getString("chat_lines").split("\r")));
        this.type = set.getString("type");
        this.effect = set.getInt("effect");
        this.canWalk = set.getString("freeroam").equals("1");
        this.room = null;
        this.roomUnit = null;
        this.chatTimeOut = Emulator.getIntUnixTimestamp() + this.chatDelay;
        this.needsUpdate = false;
        this.bubble = set.getInt("bubble_id");
    }

    public Bot(Bot bot) {
        this.name = bot.getName();
        this.motto = bot.getMotto();
        this.figure = bot.getFigure();
        this.gender = bot.getGender();
        this.ownerId = bot.getOwnerId();
        this.ownerName = bot.getOwnerName();
        this.chatAuto = true;
        this.chatRandom = false;
        this.chatDelay = 10;
        this.chatTimeOut = Emulator.getIntUnixTimestamp() + this.chatDelay;
        this.chatLines = new ArrayList<>(Arrays.asList("Default Message :D"));
        this.type = bot.getType();
        this.effect = bot.getEffect();
        this.bubble = bot.getBubbleId();

        this.needsUpdate = false;
    }

    public static void initialise() {

    }

    public static void dispose() {

    }

    public void needsUpdate(boolean needsUpdate) {
        this.needsUpdate = needsUpdate;
    }

    public boolean needsUpdate() {
        return this.needsUpdate;
    }

    @Override
    public void run() {
        if (this.needsUpdate) {
            Room localRoom = this.room;
            RoomUnit localRoomUnit = this.roomUnit;
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE bots SET name = ?, motto = ?, figure = ?, gender = ?, user_id = ?, room_id = ?, x = ?, y = ?, z = ?, rot = ?, dance = ?, freeroam = ?, chat_lines = ?, chat_auto = ?, chat_random = ?, chat_delay = ?, effect = ?, bubble_id = ? WHERE id = ?")) {
                statement.setString(1, this.name);
                statement.setString(2, this.motto);
                statement.setString(3, this.figure);
                statement.setString(4, this.gender.toString());
                statement.setInt(5, this.ownerId);
                statement.setInt(6, localRoom == null ? 0 : localRoom.getId());
                statement.setInt(7, localRoomUnit == null ? 0 : localRoomUnit.getX());
                statement.setInt(8, localRoomUnit == null ? 0 : localRoomUnit.getY());
                statement.setDouble(9, localRoomUnit == null ? 0 : localRoomUnit.getZ());
                statement.setInt(10, localRoomUnit == null ? 0 : localRoomUnit.getBodyRotation().getValue());
                statement.setInt(11, localRoomUnit == null ? 0 : localRoomUnit.getDanceType().getType());
                statement.setString(12, this.canWalk ? "1" : "0");
                StringBuilder text = new StringBuilder();
                for (String s : this.chatLines) {
                    text.append(s).append("\r");
                }
                statement.setString(13, text.toString());
                statement.setString(14, this.chatAuto ? "1" : "0");
                statement.setString(15, this.chatRandom ? "1" : "0");
                statement.setInt(16, this.chatDelay);
                statement.setInt(17, this.effect);
                statement.setInt(18, this.bubble);
                statement.setInt(19, this.id);
                statement.execute();
                this.needsUpdate = false;
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    public void cycle(boolean allowBotsWalk) {
        if (this.roomUnit != null) {
            if (allowBotsWalk && this.canWalk) {
                if (!this.roomUnit.isWalking()) {
                    if (this.roomUnit.getWalkTimeOut() < Emulator.getIntUnixTimestamp() && this.followingHabboId == 0) {
                        this.roomUnit.setGoalLocation(
                                Bot.BOT_LIMIT_WALKING_DISTANCE
                                        ? this.room.getRandomWalkableTilesAround(
                                        this.getRoomUnit(),
                                        this.room.getLayout().getTile(this.roomUnit.getBotStartLocation().x, this.roomUnit.getBotStartLocation().y),
                                        Bot.BOT_WALKING_DISTANCE_RADIUS)
                                        : this.room.getRandomWalkableTile()
                        );

                        int timeOut = Emulator.getRandom().nextInt(20) * 2;
                        this.roomUnit.setWalkTimeOut((timeOut < 10 ? 5 : timeOut) + Emulator.getIntUnixTimestamp());
                    }
                }
            }

            if (!this.chatLines.isEmpty() && this.chatTimeOut <= Emulator.getIntUnixTimestamp() && this.chatAuto) {
                if (this.room != null) {
                    if (this.lastChatIndex < 0 || this.lastChatIndex >= this.chatLines.size()) {
                        this.lastChatIndex = 0;
                    }

                    String message = this.chatLines.get(this.lastChatIndex)
                            .replace(Emulator.getTexts().getValue("wired.variable.owner", "%owner%"), this.room.getOwnerName())
                            .replace(Emulator.getTexts().getValue("wired.variable.item_count", "%item_count%"), this.room.itemCount() + "")
                            .replace(Emulator.getTexts().getValue("wired.variable.name", "%name%"), this.name)
                            .replace(Emulator.getTexts().getValue("wired.variable.roomname", "%roomname%"), this.room.getName())
                            .replace(Emulator.getTexts().getValue("wired.variable.user_count", "%user_count%"), this.room.getUserCount() + "");

                    if(!WiredManager.triggerUserSays(room, this.getRoomUnit(), message)) {
                        this.talk(message);
                    }

                    if (this.chatRandom) {
                        this.lastChatIndex = (short) Emulator.getRandom().nextInt(this.chatLines.size());
                    } else {
                        this.lastChatIndex++;
                        if (this.lastChatIndex >= this.chatLines.size()) {
                            this.lastChatIndex = 0;
                        }
                    }

                    this.chatTimeOut = Emulator.getIntUnixTimestamp() + this.chatDelay;
                }
            }

        }
    }

    public void talk(String message) {
        if (this.room != null) {
            BotChatEvent event = new BotTalkEvent(this, message);
            if (Emulator.getPluginManager().fireEvent(event).isCancelled())
                return;

            this.chatTimestamp = Emulator.getIntUnixTimestamp();
            this.room.botChat(new RoomUserTalkComposer(new RoomChatMessage(event.message, this.roomUnit, RoomChatMessageBubbles.getBubble(this.getBubbleId()))).compose());

            if (message.equals("o/") || message.equals("_o/")) {
                this.room.sendComposer(new RoomUserActionComposer(this.roomUnit, RoomUserAction.WAVE).compose());
            }
        }
    }

    public void shout(String message) {
        if (this.room != null) {
            BotChatEvent event = new BotShoutEvent(this, message);
            if (Emulator.getPluginManager().fireEvent(event).isCancelled())
                return;

            this.chatTimestamp = Emulator.getIntUnixTimestamp();
            this.room.botChat(new RoomUserShoutComposer(new RoomChatMessage(event.message, this.roomUnit, RoomChatMessageBubbles.getBubble(this.getBubbleId()))).compose());

            if (message.equals("o/") || message.equals("_o/")) {
                this.room.sendComposer(new RoomUserActionComposer(this.roomUnit, RoomUserAction.WAVE).compose());
            }
        }
    }

    public void whisper(String message, Habbo habbo) {
        if (this.room != null && habbo != null) {
            BotWhisperEvent event = new BotWhisperEvent(this, message, habbo);
            if (Emulator.getPluginManager().fireEvent(event).isCancelled())
                return;

            this.chatTimestamp = Emulator.getIntUnixTimestamp();
            event.target.getClient().sendResponse(new RoomUserWhisperComposer(new RoomChatMessage(event.message, this.roomUnit, RoomChatMessageBubbles.getBubble(this.getBubbleId()))));
        }
    }

    public void onPlace(Habbo habbo, Room room) {
        if (this.roomUnit != null) {
            room.giveEffect(this.roomUnit, this.effect, -1);
        }

        if(PLACEMENT_MESSAGES.length > 0) {
            String message = PLACEMENT_MESSAGES[Emulator.getRandom().nextInt(PLACEMENT_MESSAGES.length)];
            if (!WiredManager.triggerUserSays(room, this.getRoomUnit(), message)) {
                this.talk(message);
            }
        }
    }

    public void onPickUp(Habbo habbo, Room room) {
        this.stopFollowingHabbo();
    }

    public void onUserSay(final RoomChatMessage message) {

    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public int getBubbleId() {
        return bubble;
    }

    public void setName(String name) {
        this.name = name;
        this.needsUpdate = true;
    }

    public String getMotto() {
        return this.motto;
    }

    public void setMotto(String motto) {
        this.motto = motto;
        this.needsUpdate = true;
    }

    public String getFigure() {
        return this.figure;
    }

    public void setFigure(String figure) {
        this.figure = figure;
        this.needsUpdate = true;

        if (this.room != null)
            this.room.sendComposer(new RoomUsersComposer(this).compose());
    }

    public HabboGender getGender() {
        return this.gender;
    }

    public void setGender(HabboGender gender) {
        this.gender = gender;
        this.needsUpdate = true;

        if (this.room != null)
            this.room.sendComposer(new RoomUsersComposer(this).compose());
    }

    public int getOwnerId() {
        return this.ownerId;
    }

    public void setOwnerId(int ownerId) {
        this.ownerId = ownerId;
        this.needsUpdate = true;

        if (this.room != null)
            this.room.sendComposer(new RoomUsersComposer(this).compose());
    }

    public String getOwnerName() {
        return this.ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
        this.needsUpdate = true;

        if (this.room != null)
            this.room.sendComposer(new RoomUsersComposer(this).compose());
    }

    public Room getRoom() {
        return this.room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public RoomUnit getRoomUnit() {
        return this.roomUnit;
    }

    public void setRoomUnit(RoomUnit roomUnit) {
        this.roomUnit = roomUnit;
    }

    public boolean isChatAuto() {
        return this.chatAuto;
    }

    public void setChatAuto(boolean chatAuto) {
        this.chatAuto = chatAuto;
        this.needsUpdate = true;
    }

    public boolean isChatRandom() {
        return this.chatRandom;
    }

    public void setChatRandom(boolean chatRandom) {
        this.chatRandom = chatRandom;
        this.needsUpdate = true;
    }

    public boolean hasChat() {
        return !this.chatLines.isEmpty();
    }

    public int getChatDelay() {
        return this.chatDelay;
    }

    public void setChatDelay(short chatDelay) {
        this.chatDelay = (short) Math.min(Math.max(chatDelay, BotManager.MINIMUM_CHAT_SPEED), BotManager.MAXIMUM_CHAT_SPEED);
        this.needsUpdate = true;
        this.chatTimeOut = Emulator.getIntUnixTimestamp() + this.chatDelay;
    }

    public int getChatTimestamp() {
        return this.chatTimestamp;
    }

    public void clearChat() {
        synchronized (this.chatLines) {
            this.chatLines.clear();
            this.needsUpdate = true;
        }
    }

    public String getType() {
        return this.type;
    }

    public int getEffect() {
        return this.effect;
    }

    public void setBubble(int bubble) {
        this.bubble = bubble;
        this.needsUpdate = true;
    }

    public void setEffect(int effect, int duration) {
        this.effect = effect;
        this.needsUpdate = true;

        if (this.roomUnit != null) {
            if (this.room != null) {
                this.room.giveEffect(this.roomUnit, this.effect, duration);
            }
        }
    }

    public void addChatLines(ArrayList<String> chatLines) {
        synchronized (this.chatLines) {
            this.chatLines.addAll(chatLines);
            this.needsUpdate = true;
        }
    }

    public void addChatLine(String chatLine) {
        synchronized (this.chatLines) {
            this.chatLines.add(chatLine);
            this.needsUpdate = true;
        }
    }

    public ArrayList<String> getChatLines() {
        return this.chatLines;
    }

    public int getFollowingHabboId() {
        return this.followingHabboId;
    }

    public void startFollowingHabbo(Habbo habbo) {
        this.followingHabboId = habbo.getHabboInfo().getId();

        Emulator.getThreading().run(new BotFollowHabbo(this, habbo, habbo.getHabboInfo().getCurrentRoom()));
    }

    public void stopFollowingHabbo() {
        this.followingHabboId = 0;
    }

    public boolean canWalk() {
        return this.canWalk;
    }

    public void setCanWalk(boolean canWalk) {
        this.canWalk = canWalk;
    }

    public void lookAt(Habbo habbo) {
        this.lookAt(habbo.getRoomUnit().getCurrentLocation());
    }

    public void lookAt(RoomUnit roomUnit) {
        this.lookAt(roomUnit.getCurrentLocation());
    }

    public void lookAt(RoomTile tile) {
        this.roomUnit.lookAtPoint(tile);
        this.roomUnit.statusUpdate(true);
    }

    public void onPlaceUpdate() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE bots SET name = ?, motto = ?, figure = ?, gender = ?, user_id = ?, room_id = ?, x = ?, y = ?, z = ?, rot = ?, dance = ?, freeroam = ?, chat_lines = ?, chat_auto = ?, chat_random = ?, chat_delay = ?, effect = ?, bubble_id = ? WHERE id = ?")) {
            statement.setString(1, this.name);
            statement.setString(2, this.motto);
            statement.setString(3, this.figure);
            statement.setString(4, this.gender.toString());
            statement.setInt(5, this.ownerId);
            statement.setInt(6, this.room == null ? 0 : this.room.getId());
            statement.setInt(7, this.roomUnit == null ? 0 : this.roomUnit.getX());
            statement.setInt(8, this.roomUnit == null ? 0 : this.roomUnit.getY());
            statement.setDouble(9, this.roomUnit == null ? 0 : this.roomUnit.getZ());
            statement.setInt(10, this.roomUnit == null ? 0 : this.roomUnit.getBodyRotation().getValue());
            statement.setInt(11, this.roomUnit == null ? 0 : this.roomUnit.getDanceType().getType());
            statement.setString(12, this.canWalk ? "1" : "0");
            StringBuilder text = new StringBuilder();
            for (String s : this.chatLines) {
                text.append(s).append("\r");
            }
            statement.setString(13, text.toString());
            statement.setString(14, this.chatAuto ? "1" : "0");
            statement.setString(15, this.chatRandom ? "1" : "0");
            statement.setInt(16, this.chatDelay);
            statement.setInt(17, this.effect);
            statement.setInt(18, this.bubble);
            statement.setInt(19, this.id);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    private static final short[] DEFAULT_OWNER_ACTION_IDS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11};

    public static final int ACTION_ROTATE = 11;

    private static final long MIN_OWNER_ACTION_INTERVAL_MS = 200L;

    private volatile long lastOwnerActionAt;

    public short[] getOwnerActionIds() {
        return DEFAULT_OWNER_ACTION_IDS;
    }

    public synchronized boolean tryAcquireOwnerActionSlot() {
        long now = System.currentTimeMillis();
        if (now - this.lastOwnerActionAt < MIN_OWNER_ACTION_INTERVAL_MS) {
            return false;
        }
        this.lastOwnerActionAt = now;
        return true;
    }

    public void onPostOwnerAction(int actionId) {
        // no-op default
    }
}
