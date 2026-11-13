package polaris.hotel.users;

import it.unimi.dsi.fastutil.ints.IntCollection;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.WiredPlatform;
import polaris.hotel.GameEnvironment;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.bots.Bot;
import polaris.hotel.economy.EconomyLedger;
import polaris.hotel.economy.EconomyOperation;
import polaris.hotel.economy.EconomyOperationId;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.habbicons.HabbiconService;
import polaris.hotel.messenger.Messenger;
import polaris.hotel.modtool.ModToolBan;
import polaris.hotel.permissions.Permission;
import polaris.hotel.pets.Pet;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitType;
import polaris.hotel.rooms.RoomUserAction;
import polaris.hotel.rooms.RoomVisitorQueueSupport;
import polaris.hotel.users.inventory.BadgesComponent;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.generic.alerts.GenericAlertComposer;
import polaris.messages.outgoing.generic.alerts.MessagesForYouComposer;
import polaris.messages.outgoing.generic.alerts.StaffAlertWithLinkComposer;
import polaris.messages.outgoing.inventory.AddBotComposer;
import polaris.messages.outgoing.inventory.AddPetComposer;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryBadgesComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.inventory.RemoveBotComposer;
import polaris.messages.outgoing.inventory.RemovePetComposer;
import polaris.messages.outgoing.inventory.RemoveUserItemComposer;
import polaris.messages.outgoing.rooms.FloodCounterComposer;
import polaris.messages.outgoing.rooms.ForwardToRoomComposer;
import polaris.messages.outgoing.rooms.users.RoomUserActionComposer;
import polaris.messages.outgoing.rooms.users.RoomUserRespectComposer;
import polaris.messages.outgoing.rooms.users.RoomUserShoutComposer;
import polaris.messages.outgoing.rooms.users.RoomUserTalkComposer;
import polaris.messages.outgoing.rooms.users.RoomUserWhisperComposer;
import polaris.messages.outgoing.users.AddUserBadgeComposer;
import polaris.messages.outgoing.users.BanInfoComposer;
import polaris.messages.outgoing.users.MutedWhisperComposer;
import polaris.messages.outgoing.users.UserCreditsComposer;
import polaris.messages.outgoing.users.UserCurrencyComposer;
import polaris.messages.outgoing.users.UserPointsComposer;
import polaris.networking.gameserver.GameServerAttributes;
import polaris.plugin.events.users.UserCreditsEvent;
import polaris.plugin.events.users.UserDisconnectEvent;
import polaris.plugin.events.users.UserGetIPAddressEvent;
import polaris.plugin.events.users.UserPointsEvent;

public class User implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(User.class);

    private final UserInfo userInfo;
    private final UserStats userStats;
    private final Messenger messenger;
    private final UserInventory userInventory;
    private GameClient client;
    private RoomUnit roomUnit;

    private volatile boolean update;
    private volatile boolean disconnected = false;
    private volatile boolean disconnecting = false;
    public boolean roomBypass = false;

    private HabbiconService habbiconService;

    public HabbiconService getHabbiconService() {
        return this.habbiconService;
    }

    void setHabbiconService(HabbiconService habbiconService) {
        this.habbiconService = habbiconService;
    }

    public User(ResultSet set) {
        this.client = null;
        this.userInfo = new UserInfo(set);
        this.userStats = UserStats.load(this.userInfo);
        this.userInventory = new UserInventory(this);

        this.messenger = new Messenger();
        this.messenger.loadFriends(this);
        this.messenger.loadFriendRequests(this);

        this.roomUnit = new RoomUnit();
        this.roomUnit.setRoomUnitType(RoomUnitType.USER);
        this.update = false;
    }

    public boolean isOnline() {
        return this.userInfo.isOnline();
    }

    void isOnline(boolean value) {
        this.userInfo.setOnline(value);
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

    public UserInfo getUserInfo() {
        return this.userInfo;
    }

    public UserStats getUserStats() {
        return this.userStats;
    }

    public UserInventory getInventory() {
        return this.userInventory;
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
        ConnectionSecurityResult security = this.checkConnectionSecurity();
        if (!security.allowed()) {
            return false;
        }

        this.isOnline(true);
        this.messenger.connectionChanged(this, true, false);
        Emulator.getGameEnvironment().getRoomManager().loadRoomsForUser(this);
        LOGGER.info(
                "{} logged in from IP {} using proxyserver {}",
                this.userInfo.getUsername(),
                this.userInfo.getIpLogin(),
                security.proxyInfo());
        LOGGER.info("{} client MachineId = {}", this.userInfo.getUsername(), this.client.getMachineId());
        return true;
    }

    /**
     * Re-evaluates account, IP, and machine bans for the current connection,
     * used when resuming a parked session that skips the normal login path.
     * This resolves and records the resuming connection's IP (and fires
     * {@link polaris.plugin.events.users.UserGetIPAddressEvent}) exactly
     * as {@link #connect()} does, but does not bring the Habbo online or load
     * rooms. Returns true when the connection is allowed to proceed.
     */
    public boolean passesConnectionSecurityChecks() {
        return this.checkConnectionSecurity().allowed();
    }

    private ConnectionSecurityResult checkConnectionSecurity() {
        if (this.client == null || this.client.getChannel() == null) {
            return new ConnectionSecurityResult(false, "missing client channel");
        }

        String ip = "";
        String proxyInfo = "";

        String wsIp = this.client.getChannel().attr(GameServerAttributes.WS_IP).get();
        if (wsIp != null && !wsIp.isEmpty()) {
            ip = wsIp;
            proxyInfo = remoteIp(this.client.getChannel().remoteAddress());
        } else if (!Emulator.getConfig().getBoolean("networking.tcp.proxy")
                && this.client.getChannel().remoteAddress() != null) {
            ip = remoteIp(this.client.getChannel().remoteAddress());
            proxyInfo = "- no proxy server used";
        } else {
            proxyInfo = remoteIp(this.client.getChannel().remoteAddress());
        }

        if (Emulator.getPluginManager().isRegistered(UserGetIPAddressEvent.class, true)) {
            UserGetIPAddressEvent event = Emulator.getPluginManager().fireEvent(new UserGetIPAddressEvent(this, ip));
            if (event.hasChangedIP()) {
                ip = event.getUpdatedIp();
            }
        }

        if (!ip.isEmpty()) {
            this.userInfo.setIpLogin(ip);
        }

        ModToolBan accountBan =
                Emulator.getGameEnvironment().getModToolManager().checkForBan(this.userInfo.getId());

        if (accountBan != null) {
            // Official class_2799 BanInfo: the client turns it into the ban alert with the expiry
            // (HabboAlertDialogManager.handleBanInfoMessage) before the connection goes away.
            this.client.sendResponse(new BanInfoComposer(
                    this.userInfo.getId(),
                    accountBan.reason,
                    Math.max(-1, accountBan.expireDate - Emulator.getIntUnixTimestamp()),
                    ""));
            return new ConnectionSecurityResult(false, proxyInfo);
        }

        // Nitro sends its machine fingerprint after SSO. Check it here only when
        // already available; MachineIDEvent enforces a later-arriving fingerprint.
        String machineId = this.client.getMachineId();
        if (machineId != null && !machineId.isEmpty()) {
            this.userInfo.setMachineID(machineId);
            if (Emulator.getGameEnvironment().getModToolManager().hasMACBan(this.client)) {
                return new ConnectionSecurityResult(false, proxyInfo);
            }
        }

        if (Emulator.getGameEnvironment().getModToolManager().hasIPBan(this.userInfo.getIpLogin())) {
            return new ConnectionSecurityResult(false, proxyInfo);
        }

        return new ConnectionSecurityResult(true, proxyInfo);
    }

    private static String remoteIp(SocketAddress address) {
        if (address instanceof InetSocketAddress inetAddress && inetAddress.getAddress() != null) {
            return inetAddress.getAddress().getHostAddress();
        }
        return "";
    }

    private record ConnectionSecurityResult(boolean allowed, String proxyInfo) {}

    public void disconnect() {
        GameEnvironment environment = Emulator.getGameEnvironment();
        UserManager userManager = environment.getUserManager();
        boolean shuttingDown = Emulator.isShuttingDown;
        var pluginManager = Emulator.getPluginManager();
        DisconnectPersistenceGate.Registration persistenceRegistration;
        synchronized (this) {
            if (this.disconnected || this.disconnecting) {
                if (!shuttingDown) {
                    pluginManager.fireEvent(new UserDisconnectEvent(this));
                }
                return;
            }

            persistenceRegistration = userManager.beginDisconnectPersistence(this.userInfo.getId());
            boolean cancelled;
            try {
                cancelled = !shuttingDown
                        && pluginManager
                                .fireEvent(new UserDisconnectEvent(this))
                                .isCancelled();
            } catch (RuntimeException exception) {
                userManager.cancelDisconnectPersistence(persistenceRegistration);
                throw exception;
            }
            if (cancelled) {
                userManager.cancelDisconnectPersistence(persistenceRegistration);
                return;
            }
            this.disconnecting = true;

            try {
                if (this.getUserInfo().getCurrentRoom() != null) {
                    environment
                            .getRoomManager()
                            .leaveRoom(this, this.getUserInfo().getCurrentRoom());
                }
                if (this.getUserInfo().getRoomQueueId() > 0) {
                    Room room = environment
                            .getRoomManager()
                            .getRoom(this.getUserInfo().getRoomQueueId());

                    if (room != null) {
                        room.removeFromQueue(this);
                    }
                }
                // AIR 13 room queue: a logout leaves every full-room queue.
                RoomVisitorQueueSupport.removeEverywhere(this);
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }

            try {
                environment.getGuideManager().userLogsOut(this);
                // The wheel kept every user's state and last spin for the whole uptime.
                if (environment.getWheelManager() != null) {
                    environment.getWheelManager().forgetUser(this.userInfo.getId());
                }
                this.userInfo.setOnline(false);
                this.needsUpdate(true);
                this.messenger.connectionChanged(this, false, false);
                this.messenger.dispose();
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            } finally {
                this.disconnected = true;
                try {
                    environment.getRoomManager().unloadRoomsForUser(this);
                } catch (Exception e) {
                    LOGGER.error("Unable to unload owned rooms during disconnect", e);
                }
                try {
                    userManager.removeUser(this);
                } catch (Exception e) {
                    LOGGER.error("Unable to remove disconnected user", e);
                }
                this.client = null;
            }
        }

        userManager.submitDisconnectPersistence(persistenceRegistration, this::persistDisconnect);
    }

    private void persistDisconnect() {
        this.run();
        this.getInventory().dispose();
        AchievementManager.saveAchievements(this);
        polaris.hotel.quests.QuestProgressEvents.unload(this.userInfo.getId());
        this.userStats.dispose();
        LOGGER.info("{} disconnected.", this.userInfo.getUsername());
    }

    @Override
    public void run() {
        if (this.needsUpdate()) {
            this.userInfo.run();
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
        this.giveCredits(credits, "economy.api.credits");
    }

    public void giveCredits(int credits, String reason) {
        this.giveCredits(
                credits,
                reason,
                EconomyOperationId.create("credits:" + this.getUserInfo().getId()),
                this.getUserInfo().getId());
    }

    public void giveCredits(int credits, String reason, String operationId, Integer actorId) {
        this.applyCredits(credits, reason, operationId, actorId);
    }

    /**
     * Applies a credit grant or debit and says whether it happened: a plugin can cancel it and the ledger refuses a
     * negative balance or an overflow, and callers that report the change must not report one that did not happen.
     */
    public boolean applyCredits(int credits, String reason, String operationId, Integer actorId) {
        if (credits == 0) return false;

        UserCreditsEvent event = new UserCreditsEvent(this, credits);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled()) return false;

        try {
            LedgerWalletMutation.execute(
                    this,
                    new EconomyOperation(
                            operationId,
                            this.getUserInfo().getId(),
                            actorId,
                            event.credits > 0 ? "credit_grant" : "credit_debit",
                            reason,
                            EconomyLedger.CREDITS,
                            event.credits,
                            null,
                            ""));
        } catch (Exception exception) {
            LOGGER.error(
                    "Unable to apply audited credit mutation for user {}",
                    this.getUserInfo().getId(),
                    exception);
            return false;
        }

        if (this.client != null) this.client.sendResponse(new UserCreditsComposer(this));
        return true;
    }

    public boolean tryTakeCredits(int credits) {
        return this.tryTakeCredits(
                credits,
                "economy.api.credits",
                EconomyOperationId.create("credits:" + this.getUserInfo().getId()),
                this.getUserInfo().getId());
    }

    public boolean tryTakeCredits(int credits, String reason, String operationId, Integer actorId) {
        if (credits <= 0) {
            return false;
        }

        UserCreditsEvent event = new UserCreditsEvent(this, -credits);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled() || event.credits != -credits) {
            return false;
        }

        try {
            LedgerWalletMutation.execute(
                    this,
                    new EconomyOperation(
                            operationId,
                            this.getUserInfo().getId(),
                            actorId,
                            "credit_debit",
                            reason,
                            EconomyLedger.CREDITS,
                            event.credits,
                            null,
                            ""));
        } catch (IllegalArgumentException exception) {
            return false;
        } catch (Exception exception) {
            LOGGER.error(
                    "Unable to apply audited credit debit for user {}",
                    this.getUserInfo().getId(),
                    exception);
            return false;
        }

        if (this.client != null) this.client.sendResponse(new UserCreditsComposer(this));
        return true;
    }

    public void givePixels(int pixels) {
        this.givePoints(0, pixels, "economy.api.pixels");
    }

    public void givePixels(int pixels, String reason) {
        this.givePoints(0, pixels, reason);
    }

    public void givePoints(int points) {
        this.givePoints(Emulator.getConfig().getInt("seasonal.primary.type"), points);
    }

    public void givePoints(int type, int points) {
        this.givePoints(type, points, "economy.api.currency");
    }

    public void givePoints(int type, int points, String reason) {
        this.givePoints(
                type,
                points,
                reason,
                EconomyOperationId.create("currency:" + this.getUserInfo().getId() + ":" + type),
                this.getUserInfo().getId());
    }

    public void givePoints(int type, int points, String reason, String operationId, Integer actorId) {
        this.applyPoints(type, points, reason, operationId, actorId);
    }

    /** {@link #applyCredits} for an activity-point currency. */
    public boolean applyPoints(int type, int points, String reason, String operationId, Integer actorId) {
        if (points == 0) return false;

        UserPointsEvent event = new UserPointsEvent(this, points, type);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled()) return false;

        try {
            LedgerWalletMutation.execute(
                    this,
                    new EconomyOperation(
                            operationId,
                            this.getUserInfo().getId(),
                            actorId,
                            event.points > 0 ? "currency_grant" : "currency_debit",
                            reason,
                            event.type,
                            event.points,
                            null,
                            ""));
        } catch (Exception exception) {
            LOGGER.error(
                    "Unable to apply audited currency mutation for user {}",
                    this.getUserInfo().getId(),
                    exception);
            return false;
        }
        if (this.client != null) {
            if (event.type == 0) this.client.sendResponse(new UserCurrencyComposer(this));
            else
                this.client.sendResponse(new UserPointsComposer(
                        this.getUserInfo().getCurrencyAmount(event.type), event.points, event.type));
        }
        return true;
    }

    public boolean tryTakePoints(int type, int points) {
        return this.tryTakePoints(
                type,
                points,
                "economy.api.currency",
                EconomyOperationId.create("currency:" + this.getUserInfo().getId() + ":" + type),
                this.getUserInfo().getId());
    }

    public boolean tryTakePoints(int type, int points, String reason, String operationId, Integer actorId) {
        if (points <= 0) {
            return false;
        }

        UserPointsEvent event = new UserPointsEvent(this, -points, type);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled()
                || event.type != type
                || event.points != -points) {
            return false;
        }

        try {
            LedgerWalletMutation.execute(
                    this,
                    new EconomyOperation(
                            operationId,
                            this.getUserInfo().getId(),
                            actorId,
                            "currency_debit",
                            reason,
                            event.type,
                            event.points,
                            null,
                            ""));
        } catch (IllegalArgumentException exception) {
            return false;
        } catch (Exception exception) {
            LOGGER.error(
                    "Unable to apply audited currency debit for user {}",
                    this.getUserInfo().getId(),
                    exception);
            return false;
        }

        if (this.client != null) {
            this.client.sendResponse(
                    new UserPointsComposer(this.getUserInfo().getCurrencyAmount(event.type), event.points, event.type));
        }
        return true;
    }

    public void whisper(String message) {
        this.whisper(message, this.userStats.chatColor);
    }

    public void whisper(String message, RoomChatMessageBubbles bubble) {
        if (this.getRoomUnit().isInRoom()) {
            this.client.sendResponse(
                    new RoomUserWhisperComposer(new RoomChatMessage(message, this.getRoomUnit(), bubble)));
        }
    }

    public void whisperLocalized(
            String textKey, String placeholder, String replacement, RoomChatMessageBubbles bubble) {
        this.whisper(this.getText(textKey).replace(placeholder, replacement), bubble);
    }

    public void whisperLocalized(String textKey, RoomChatMessageBubbles bubble) {
        this.whisper(this.getText(textKey), bubble);
    }

    /**
     * Whispers a text a hotel may not have in emulator_texts yet, falling back to the given line.
     */
    public void whisperLocalizedOrDefault(String textKey, String fallback, RoomChatMessageBubbles bubble) {
        this.whisper(this.getText(textKey, fallback), bubble);
    }

    private String getText(String key) {
        return this.getText(key, "");
    }

    private String getText(String key, String fallback) {
        return Emulator.getTexts().getValue(key, fallback);
    }

    public void talk(String message) {
        this.talk(message, this.userStats.chatColor);
    }

    public void talk(String message, RoomChatMessageBubbles bubble) {
        if (this.getRoomUnit().isInRoom()) {
            this.getUserInfo()
                    .getCurrentRoom()
                    .sendComposer(new RoomUserTalkComposer(new RoomChatMessage(message, this.getRoomUnit(), bubble))
                            .compose());
        }
    }

    public void shout(String message) {
        this.shout(message, this.userStats.chatColor);
    }

    public void shout(String message, RoomChatMessageBubbles bubble) {
        if (this.getRoomUnit().isInRoom()) {
            this.getUserInfo()
                    .getCurrentRoom()
                    .sendComposer(new RoomUserShoutComposer(new RoomChatMessage(message, this.getRoomUnit(), bubble))
                            .compose());
        }
    }

    public void alert(String message) {
        if (Emulator.getConfig().getBoolean("hotel.alert.oldstyle")) {
            this.client.sendResponse(new MessagesForYouComposer(new String[] {message}));
        } else {
            this.client.sendResponse(new GenericAlertComposer(message));
        }
    }

    public void alertLocalized(String textKey) {
        this.alert(this.getText(textKey));
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

    public void addFurniture(UserItem item) {
        this.userInventory.getItemsComponent().addItem(item);
        this.client.sendResponse(new AddUserItemComposer(item));
        this.client.sendResponse(new InventoryRefreshComposer());
    }

    public void addFurniture(Collection<UserItem> items) {
        this.userInventory.getItemsComponent().addItems(items);
        this.client.sendResponse(new AddUserItemComposer(items));
        this.client.sendResponse(new InventoryRefreshComposer());
    }

    public void removeFurniture(UserItem item) {
        this.userInventory.getItemsComponent().removeUserItem(item);
        this.client.sendResponse(new RemoveUserItemComposer(item.getId()));
    }

    public void addBot(Bot bot) {
        this.userInventory.getBotsComponent().addBot(bot);
        this.client.sendResponse(new AddBotComposer(bot));
    }

    public void removeBot(Bot bot) {
        this.userInventory.getBotsComponent().removeBot(bot);
        this.client.sendResponse(new RemoveBotComposer(bot));
    }

    public void deleteBot(Bot bot) {
        this.removeBot(bot);
        bot.getRoom().removeBot(bot);
        Emulator.getGameEnvironment().getBotManager().deleteBot(bot);
    }

    public void addPet(Pet pet) {
        this.userInventory.getPetsComponent().addPet(pet);
        this.client.sendResponse(new AddPetComposer(pet));
    }

    public void removePet(Pet pet) {
        this.userInventory.getPetsComponent().removePet(pet);
        this.client.sendResponse(new RemovePetComposer(pet));
    }

    public boolean addBadge(String code) {
        return this.addBadge(code, "");
    }

    public boolean addBadge(String code, String senderName) {
        if (!this.userInventory.getBadgesComponent().hasBadge(code)) {
            UserBadge badge = BadgesComponent.createBadge(code, this);
            if (badge == null) return false;
            this.client.sendResponse(new AddUserBadgeComposer(badge, senderName));
            this.client.sendResponse(
                    new AddUserItemComposer(badge.getId(), AddUserItemComposer.AddUserItemCategory.BADGE));

            Map<String, String> keys = new HashMap<>();
            keys.put("display", "BUBBLE");
            keys.put("image", "${image.library.url}album1584/" + badge.getCode() + ".gif");
            keys.put("message", this.getText("commands.generic.cmd_badge.received"));
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys));

            return true;
        }

        return false;
    }

    public void deleteBadge(UserBadge badge) {
        if (badge != null) {
            this.userInventory.getBadgesComponent().removeBadge(badge);
            BadgesComponent.deleteBadge(this.getUserInfo().getId(), badge.getCode());
            this.client.sendResponse(new InventoryBadgesComposer(this));
        }
    }

    public void mute(int seconds, boolean isFlood) {
        if (seconds <= 0) {
            LOGGER.warn("Tried to mute user for {} seconds, which is invalid.", seconds);
            return;
        }

        if (!this.hasPermission(Permission.ACC_NOMUTE)) {
            int remaining = this.userStats.addMuteTime(seconds);
            this.client.sendResponse(new FloodCounterComposer(remaining));
            // RemainingMutePeriod reaches the muted user only, as in AIR. The room is not told: an
            // IgnoreResult would put this user on every other client's own ignore list.
            this.client.sendResponse(new MutedWhisperComposer(remaining));
        }
    }

    public void unMute() {
        this.userStats.unMute();
        this.client.sendResponse(new FloodCounterComposer(3));
    }

    /** The client's noobness level: a new identity for the first days of the account (see NewIdentityPolicy). */
    public int noobStatus() {
        var configuration = WiredPlatform.configuration();
        int days = configuration == null
                ? NewIdentityPolicy.DEFAULT_DAYS
                : configuration.getInt(NewIdentityPolicy.DAYS_KEY, NewIdentityPolicy.DEFAULT_DAYS);

        return NewIdentityPolicy.status(this.userInfo.getAccountCreated(), WiredPlatform.unixTimestamp(), days);
    }

    public void clearCaches() {
        int currentTimestamp = Emulator.getIntUnixTimestamp();
        int twentyFourHoursInSeconds = 24 * 60 * 60; // 24 hours in seconds

        this.userStats.pruneLtdLog(currentTimestamp, twentyFourHoursInSeconds);
    }

    public void respect(User target) {
        if (target != null && target != this) {
            target.getUserStats().respectPointsReceived++;
            this.getUserStats().respectPointsGiven++;
            this.getUserStats().respectPointsToGive--;
            this.getUserInfo().getCurrentRoom().sendComposer(new RoomUserRespectComposer(target).compose());
            this.getUserInfo()
                    .getCurrentRoom()
                    .sendComposer(new RoomUserActionComposer(this.getRoomUnit(), RoomUserAction.THUMB_UP).compose());

            AchievementManager.progressAchievement(
                    this, Emulator.getGameEnvironment().getAchievementManager().getAchievement("RespectGiven"));
            AchievementManager.progressAchievement(
                    target,
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("RespectEarned"));
            polaris.hotel.quests.QuestProgressEvents.progress(this, polaris.hotel.quests.QuestGoalType.GIVE_RESPECT, 1);

            this.getUserInfo().getCurrentRoom().unIdle(this);
            this.getUserInfo().getCurrentRoom().dance(this.getRoomUnit(), DanceType.NONE);
        }
    }

    public Set<Integer> getForbiddenClothing() {
        IntCollection clothingIDs = this.getInventory().getWardrobeComponent().getClothing();

        return Emulator.getGameEnvironment().getCatalogManager().getClothingSnapshot().values().stream()
                .filter(c -> !clothingIDs.contains(c.id))
                .map(c -> c.setId)
                .flatMap(c -> Arrays.stream(c).boxed())
                .collect(Collectors.toSet());
    }
}
