package com.eu.habbo.habbohotel.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.messenger.Messenger;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.inventory.BadgesComponent;
import com.eu.habbo.messages.outgoing.generic.alerts.*;
import com.eu.habbo.messages.outgoing.inventory.*;
import com.eu.habbo.messages.outgoing.rooms.FloodCounterComposer;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;
import com.eu.habbo.messages.outgoing.rooms.users.*;
import com.eu.habbo.messages.outgoing.users.*;
import com.eu.habbo.networking.gameserver.GameServerAttributes;
import com.eu.habbo.plugin.events.users.UserCreditsEvent;
import com.eu.habbo.plugin.events.users.UserDisconnectEvent;
import com.eu.habbo.plugin.events.users.UserGetIPAddressEvent;
import com.eu.habbo.plugin.events.users.UserPointsEvent;
import gnu.trove.TIntCollection;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

public class Habbo implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Habbo.class);

    private final HabboInfo habboInfo;
    private final HabboStats habboStats;
    private final Messenger messenger;
    private final HabboInventory habboInventory;
    private GameClient client;
    private RoomUnit roomUnit;

    private volatile boolean update;
    private volatile boolean disconnected = false;
    private volatile boolean disconnecting = false;
    public boolean roomBypass = false;

    public Habbo(ResultSet set) {
        this.client = null;
        this.habboInfo = new HabboInfo(set);
        this.habboStats = HabboStats.load(this.habboInfo);
        this.habboInventory = new HabboInventory(this);

        this.messenger = new Messenger();
        this.messenger.loadFriends(this);
        this.messenger.loadFriendRequests(this);

        this.roomUnit = new RoomUnit();
        this.roomUnit.setRoomUnitType(RoomUnitType.USER);
        this.update = false;
    }

    public boolean isOnline() {
        return this.habboInfo.isOnline();
    }

    void isOnline(boolean value) {
        this.habboInfo.setOnline(value);
        this.update();
    }

    void update() {
        this.update = true;
        this.run();
    }

    void needsUpdate(boolean value) {
        this.update = value;
    }

    boolean needsUpdate() {
        return this.update;
    }

    public Messenger getMessenger() {
        return this.messenger;
    }

    public HabboInfo getHabboInfo() {
        return this.habboInfo;
    }

    public HabboStats getHabboStats() {
        return this.habboStats;
    }

    public HabboInventory getInventory() {
        return this.habboInventory;
    }

    public RoomUnit getRoomUnit() {
        return this.roomUnit;
    }

    public void setRoomUnit(RoomUnit roomUnit) {
        this.roomUnit = roomUnit;
    }

    public GameClient getClient() {
        return this.client;
    }

    public void setClient(GameClient client) {
        this.client = client;
    }


    public boolean connect() {
        String ip = "";
        String proxyInfo = "";

        String wsIp = this.client.getChannel().attr(GameServerAttributes.WS_IP).get();
        if (wsIp != null && !wsIp.isEmpty()) {
            ip = wsIp;
            SocketAddress address = this.client.getChannel().remoteAddress();
            proxyInfo = ((InetSocketAddress) address).getAddress().getHostAddress();
        } else if (!Emulator.getConfig().getBoolean("networking.tcp.proxy") && this.client.getChannel().remoteAddress() != null) {
            SocketAddress address = this.client.getChannel().remoteAddress();
            ip = ((InetSocketAddress) address).getAddress().getHostAddress();
            proxyInfo = "- no proxy server used";
        } else {
            SocketAddress address = this.client.getChannel().remoteAddress();
            proxyInfo = ((InetSocketAddress) address).getAddress().getHostAddress();
        }

        if (Emulator.getPluginManager().isRegistered(UserGetIPAddressEvent.class, true)) {
            UserGetIPAddressEvent event = Emulator.getPluginManager().fireEvent(new UserGetIPAddressEvent(this, ip));
            if (event.hasChangedIP()) {
                ip = event.getUpdatedIp();
            }
        }

        if (!ip.isEmpty()) {
            this.habboInfo.setIpLogin(ip);
        }

        // The Nitro client sends the UniqueID (machine fingerprint) packet right
        // AFTER the SSO ticket, so client.getMachineId() may still be null here.
        // Do NOT reject the login for a missing machineId — MachineIDEvent sets it
        // and enforces the MAC ban as soon as the UniqueID packet arrives. Only
        // MAC-ban check here when the fingerprint is already available.
        String machineId = this.client.getMachineId();
        if (machineId != null && !machineId.isEmpty()) {
            this.habboInfo.setMachineID(machineId);

            if (Emulator.getGameEnvironment().getModToolManager().hasMACBan(this.client)) {
                return false;
            }
        }

        if (Emulator.getGameEnvironment().getModToolManager().hasIPBan(this.habboInfo.getIpLogin())) {
            return false;
        }
        this.isOnline(true);

        this.messenger.connectionChanged(this, true, false);

        Emulator.getGameEnvironment().getRoomManager().loadRoomsForHabbo(this);
        LOGGER.info("{} logged in from IP {} using proxyserver {}", this.habboInfo.getUsername(), this.habboInfo.getIpLogin(), proxyInfo);
        LOGGER.info("{} client MachineId = {}", this.habboInfo.getUsername(), this.client.getMachineId());
        return true;
    }


    public synchronized void disconnect() {
        if (!Emulator.isShuttingDown) {
            if (Emulator.getPluginManager().fireEvent(new UserDisconnectEvent(this)).isCancelled()) return;
        }

        if (this.disconnected || this.disconnecting)
            return;

        this.disconnecting = true;

        try {
            if (this.getHabboInfo().getCurrentRoom() != null) {
                Emulator.getGameEnvironment().getRoomManager().leaveRoom(this, this.getHabboInfo().getCurrentRoom());
            }
            if (this.getHabboInfo().getRoomQueueId() > 0) {
                Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getHabboInfo().getRoomQueueId());

                if (room != null) {
                    room.removeFromQueue(this);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        try {
            Emulator.getGameEnvironment().getGuideManager().userLogsOut(this);
            this.isOnline(false);
            this.needsUpdate(true);
            this.run();
            this.getInventory().dispose();
            this.messenger.connectionChanged(this, false, false);
            this.messenger.dispose();
            this.disconnected = true;
            AchievementManager.saveAchievements(this);

            this.habboStats.dispose();
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
            return;
        } finally {
            Emulator.getGameEnvironment().getRoomManager().unloadRoomsForHabbo(this);
            Emulator.getGameEnvironment().getHabboManager().removeHabbo(this);
        }
        LOGGER.info("{} disconnected.", this.habboInfo.getUsername());
        this.client = null;
    }

    @Override
    public void run() {
        if (this.needsUpdate()) {
            this.habboInfo.run();
            this.needsUpdate(false);
        }
    }


    public boolean hasPermission(String key) {
        return this.hasPermission(key, false);
    }


    public boolean hasPermission(String key, boolean hasRoomRights) {
        return Emulator.getGameEnvironment().getPermissionsManager().hasPermission(this, key, hasRoomRights);
    }


    public void giveCredits(int credits) {
        if (credits == 0)
            return;

        UserCreditsEvent event = new UserCreditsEvent(this, credits);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled())
            return;

        this.getHabboInfo().addCredits(event.credits);

        if (this.client != null) this.client.sendResponse(new UserCreditsComposer(this));
    }


    public void givePixels(int pixels) {
        if (pixels == 0)
            return;


        UserPointsEvent event = new UserPointsEvent(this, pixels, 0);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled())
            return;

        this.getHabboInfo().addPixels(event.points);
        if (this.client != null) this.client.sendResponse(new UserCurrencyComposer(this));
    }


    public void givePoints(int points) {
        this.givePoints(Emulator.getConfig().getInt("seasonal.primary.type"), points);
    }


    public void givePoints(int type, int points) {
        if (points == 0)
            return;

        UserPointsEvent event = new UserPointsEvent(this, points, type);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled())
            return;

        this.getHabboInfo().addCurrencyAmount(event.type, event.points);
        if (this.client != null)
            this.client.sendResponse(new UserPointsComposer(this.getHabboInfo().getCurrencyAmount(type), event.points, event.type));
    }


    public void whisper(String message) {
        this.whisper(message, this.habboStats.chatColor);
    }


    public void whisper(String message, RoomChatMessageBubbles bubble) {
        if (this.getRoomUnit().isInRoom()) {
            this.client.sendResponse(new RoomUserWhisperComposer(new RoomChatMessage(message, this.getRoomUnit(), bubble)));
        }
    }


    public void talk(String message) {
        this.talk(message, this.habboStats.chatColor);
    }


    public void talk(String message, RoomChatMessageBubbles bubble) {
        if (this.getRoomUnit().isInRoom()) {
            this.getHabboInfo().getCurrentRoom().sendComposer(new RoomUserTalkComposer(new RoomChatMessage(message, this.getRoomUnit(), bubble)).compose());
        }
    }


    public void shout(String message) {
        this.shout(message, this.habboStats.chatColor);
    }


    public void shout(String message, RoomChatMessageBubbles bubble) {
        if (this.getRoomUnit().isInRoom()) {
            this.getHabboInfo().getCurrentRoom().sendComposer(new RoomUserShoutComposer(new RoomChatMessage(message, this.getRoomUnit(), bubble)).compose());
        }
    }


    public void alert(String message) {
        if (Emulator.getConfig().getBoolean("hotel.alert.oldstyle")) {
            this.client.sendResponse(new MessagesForYouComposer(new String[]{message}));
        } else {
            this.client.sendResponse(new GenericAlertComposer(message));
        }
    }


    public void alert(String[] messages) {
        this.client.sendResponse(new MessagesForYouComposer(messages));
    }


    public void alertWithUrl(String message, String url) {
        this.client.sendResponse(new StaffAlertWithLinkComposer(message, url));
    }


    public void goToRoom(int id) {
        this.client.sendResponse(new ForwardToRoomComposer(id));
    }


    public void addFurniture(HabboItem item) {
        this.habboInventory.getItemsComponent().addItem(item);
        this.client.sendResponse(new AddHabboItemComposer(item));
        this.client.sendResponse(new InventoryRefreshComposer());
    }


    public void addFurniture(THashSet<HabboItem> items) {
        this.habboInventory.getItemsComponent().addItems(items);
        this.client.sendResponse(new AddHabboItemComposer(items));
        this.client.sendResponse(new InventoryRefreshComposer());
    }


    public void removeFurniture(HabboItem item) {
        this.habboInventory.getItemsComponent().removeHabboItem(item);
        this.client.sendResponse(new RemoveHabboItemComposer(item.getId()));
    }


    public void addBot(Bot bot) {
        this.habboInventory.getBotsComponent().addBot(bot);
        this.client.sendResponse(new AddBotComposer(bot));
    }


    public void removeBot(Bot bot) {
        this.habboInventory.getBotsComponent().removeBot(bot);
        this.client.sendResponse(new RemoveBotComposer(bot));
    }


    public void deleteBot(Bot bot) {
        this.removeBot(bot);
        bot.getRoom().removeBot(bot);
        Emulator.getGameEnvironment().getBotManager().deleteBot(bot);
    }


    public void addPet(Pet pet) {
        this.habboInventory.getPetsComponent().addPet(pet);
        this.client.sendResponse(new AddPetComposer(pet));
    }


    public void removePet(Pet pet) {
        this.habboInventory.getPetsComponent().removePet(pet);
        this.client.sendResponse(new RemovePetComposer(pet));
    }


    public boolean addBadge(String code) {
        return this.addBadge(code, "");
    }

    public boolean addBadge(String code, String senderName) {
        if (!this.habboInventory.getBadgesComponent().hasBadge(code)) {
            HabboBadge badge = BadgesComponent.createBadge(code, this);
            this.habboInventory.getBadgesComponent().addBadge(badge);
            this.client.sendResponse(new AddUserBadgeComposer(badge, senderName));
            this.client.sendResponse(new AddHabboItemComposer(badge.getId(), AddHabboItemComposer.AddHabboItemCategory.BADGE));

            THashMap<String, String> keys = new THashMap<>();
            keys.put("display", "BUBBLE");
            keys.put("image", "${image.library.url}album1584/" + badge.getCode() + ".gif");
            keys.put("message", Emulator.getTexts().getValue("commands.generic.cmd_badge.received"));
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys));

            return true;
        }

        return false;
    }


    public void deleteBadge(HabboBadge badge) {
        if (badge != null) {
            this.habboInventory.getBadgesComponent().removeBadge(badge);
            BadgesComponent.deleteBadge(this.getHabboInfo().getId(), badge.getCode());
            this.client.sendResponse(new InventoryBadgesComposer(this));
        }
    }

    public void mute(int seconds, boolean isFlood) {
        if (seconds <= 0) {
            LOGGER.warn("Tried to mute user for {} seconds, which is invalid.", seconds);
            return;
        }

        if (!this.hasPermission("acc_no_mute")) {
            int remaining = this.habboStats.addMuteTime(seconds);
            this.client.sendResponse(new FloodCounterComposer(remaining));
            this.client.sendResponse(new MutedWhisperComposer(remaining));

            Room room = this.getHabboInfo().getCurrentRoom();
            if (room != null && !isFlood) {
                room.sendComposer(new RoomUserIgnoredComposer(this, RoomUserIgnoredComposer.MUTED).compose());
            }
        }
    }

    public void unMute() {
        this.habboStats.unMute();
        this.client.sendResponse(new FloodCounterComposer(3));
        Room room = this.getHabboInfo().getCurrentRoom();
        if (room != null) {
            room.sendComposer(new RoomUserIgnoredComposer(this, RoomUserIgnoredComposer.UNIGNORED).compose());
        }
    }

    public int noobStatus() {

        return 1;

    }

    public void clearCaches() {
        int currentTimestamp = Emulator.getIntUnixTimestamp();
        int twentyFourHoursInSeconds = 24 * 60 * 60; // 24 hours in seconds

        THashMap<Integer, List<Integer>> newLog = new THashMap<>();

        for (Map.Entry<Integer, List<Integer>> ltdLog : this.habboStats.ltdPurchaseLog.entrySet()) {
            List<Integer> filteredTimestamps = new ArrayList<>();

            for (Integer time : ltdLog.getValue()) {
                if (currentTimestamp - time <= twentyFourHoursInSeconds) {
                    filteredTimestamps.add(time);
                }
            }

            if (!filteredTimestamps.isEmpty()) {
                newLog.put(ltdLog.getKey(), filteredTimestamps);
            }
        }

        this.habboStats.ltdPurchaseLog = newLog;
    }


    public void respect(Habbo target) {
        if (target != null && target != this) {
            target.getHabboStats().respectPointsReceived++;
            this.getHabboStats().respectPointsGiven++;
            this.getHabboStats().respectPointsToGive--;
            this.getHabboInfo().getCurrentRoom().sendComposer(new RoomUserRespectComposer(target).compose());
            this.getHabboInfo().getCurrentRoom().sendComposer(new RoomUserActionComposer(this.getRoomUnit(), RoomUserAction.THUMB_UP).compose());

            AchievementManager.progressAchievement(this, Emulator.getGameEnvironment().getAchievementManager().getAchievement("RespectGiven"));
            AchievementManager.progressAchievement(target, Emulator.getGameEnvironment().getAchievementManager().getAchievement("RespectEarned"));

            this.getHabboInfo().getCurrentRoom().unIdle(this);
            this.getHabboInfo().getCurrentRoom().dance(this.getRoomUnit(), DanceType.NONE);
        }
    }

    public Set<Integer> getForbiddenClothing() {
        TIntCollection clothingIDs = this.getInventory().getWardrobeComponent().getClothing();

        return Emulator.getGameEnvironment().getCatalogManager().clothing.values().stream()
                .filter(c -> !clothingIDs.contains(c.id))
                .map(c -> c.setId)
                .flatMap(c -> Arrays.stream(c).boxed())
                .collect(Collectors.toSet());
    }
}
