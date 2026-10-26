package com.eu.habbo.messages.incoming.handshake;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.MaintenanceMode;
import com.eu.habbo.habbohotel.achievements.TalentTrackType;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.gameclients.GameClientManager;
import com.eu.habbo.habbohotel.gameclients.SessionResumeManager;
import com.eu.habbo.habbohotel.habbicons.HabbiconService;
import com.eu.habbo.habbohotel.messenger.Messenger;
import com.eu.habbo.habbohotel.modtool.ModToolSanctionItem;
import com.eu.habbo.habbohotel.modtool.ModToolSanctions;
import com.eu.habbo.habbohotel.navigation.NavigatorSavedSearch;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.quests.QuestProgressEvents;
import com.eu.habbo.habbohotel.rooms.BuildersClubRoomSupport;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.habbohotel.users.clothingvalidation.ClothingValidationManager;
import com.eu.habbo.habbohotel.users.subscriptions.Subscription;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionHabboClub;
import com.eu.habbo.messages.NoAuthMessage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.collectibles.CollectibleWardrobe;
import com.eu.habbo.messages.outgoing.catalog.BuildersClubSubscriptionStatusComposer;
import com.eu.habbo.messages.outgoing.commands.AvailableCommandsComposer;
import com.eu.habbo.messages.outgoing.gamecenter.GameCenterAccountInfoComposer;
import com.eu.habbo.messages.outgoing.gamecenter.GameCenterGameListComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.MessagesForYouComposer;
import com.eu.habbo.messages.outgoing.habbicons.UserHabbiconsComposer;
import com.eu.habbo.messages.outgoing.habboway.nux.NewUserExperienceNotCompleteComposer;
import com.eu.habbo.messages.outgoing.habboway.nux.NewUserIdentityComposer;
import com.eu.habbo.messages.outgoing.handshake.AvailabilityStatusMessageComposer;
import com.eu.habbo.messages.outgoing.handshake.DisconnectReasonComposer;
import com.eu.habbo.messages.outgoing.handshake.EnableNotificationsComposer;
import com.eu.habbo.messages.outgoing.handshake.PingComposer;
import com.eu.habbo.messages.outgoing.handshake.SecureLoginOKComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.AvatarEffectSelectedComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryAchievementsComposer;
import com.eu.habbo.messages.outgoing.inventory.UserEffectsListComposer;
import com.eu.habbo.messages.outgoing.modtool.CfhTopicsMessageComposer;
import com.eu.habbo.messages.outgoing.modtool.ModToolComposer;
import com.eu.habbo.messages.outgoing.modtool.ModToolIssueHandlerDimensionsComposer;
import com.eu.habbo.messages.outgoing.modtool.ModToolSanctionInfoComposer;
import com.eu.habbo.messages.outgoing.mysterybox.MysteryBoxKeysComposer;
import com.eu.habbo.messages.outgoing.navigator.NewNavigatorSavedSearchesComposer;
import com.eu.habbo.messages.outgoing.unknown.UnknownStatusComposer;
import com.eu.habbo.messages.outgoing.users.FavoriteRoomsCountComposer;
import com.eu.habbo.messages.outgoing.users.UserAchievementScoreComposer;
import com.eu.habbo.messages.outgoing.users.UserCitizinShipComposer;
import com.eu.habbo.messages.outgoing.users.UserClothesComposer;
import com.eu.habbo.messages.outgoing.users.UserClubComposer;
import com.eu.habbo.messages.outgoing.users.UserHomeRoomComposer;
import com.eu.habbo.messages.outgoing.users.UserPermissionsComposer;
import com.eu.habbo.networking.gameserver.GameServerAttributes;
import com.eu.habbo.networking.gameserver.auth.GameConnectionLimits;
import com.eu.habbo.plugin.events.users.UserLoginEvent;
import com.eu.habbo.resilience.RuntimeResilienceController;
import com.eu.habbo.resilience.RuntimeResilienceRuntime;
import com.eu.habbo.session.SessionRecoveryRuntime;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NoAuthMessage
public class SecureLoginEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecureLoginEvent.class);
    private static final int MAX_SSO_TICKET_LENGTH = 128;
    /** AccountSafetyLockStatusChange: 0 locks the account, 1 releases it. */
    private static final int SAFETY_LOCK_LOCKED = 0;

    private static final int SAFETY_LOCK_UNLOCKED = 1;

    @Override
    public int getRatelimit() {
        return 500;
    }

    private static int rankIdOf(Habbo habbo) {
        if (habbo == null
                || habbo.getHabboInfo() == null
                || habbo.getHabboInfo().getRank() == null) {
            return Integer.MIN_VALUE;
        }
        return habbo.getHabboInfo().getRank().getId();
    }

    private static GameClientManager clients() {
        return Emulator.getGameServer().getGameClientManager();
    }

    /**
     * The gates that run before the packet is read: a forced TLS handshake, the client release and the runtime
     * admission. Returns false when the connection was turned away.
     */
    private boolean connectionAccepted() {
        ConfigurationManager config = Emulator.getConfig();
        if (config.getBoolean("encryption.forced", false)
                && Emulator.getCrypto().isEnabled()
                && !this.client.isHandshakeFinished()) {
            clients().disposeClient(this.client);
            LOGGER.warn("Encryption is forced and TLS Handshake isn't finished! Closed connection...");
            return false;
        }

        String allowedReleases = config.getValue("client.release.allowed", ClientReleaseGuard.DEFAULT_ALLOWED_RELEASES);
        if (!ClientReleaseGuard.isAllowed(this.client.getReleaseVersion(), allowedReleases)) {
            LOGGER.warn(
                    "Rejected client release '{}' (allowed: '{}')", this.client.getReleaseVersion(), allowedReleases);
            clients().disposeClient(this.client);
            return false;
        }

        RuntimeResilienceController.Admission admission =
                RuntimeResilienceRuntime.admit(RuntimeResilienceController.WorkClass.INTERACTIVE);
        if (admission.effectiveAction() == RuntimeResilienceController.Action.REJECT) {
            this.client.sendResponse(new GenericAlertComposer("The hotel is temporarily busy. Please try again."));
            this.client.getChannel().close();
            return false;
        }

        return true;
    }

    @Override
    public void handle() throws Exception {
        if (!this.client.getChannel().isOpen()) {
            clients().disposeClient(this.client);
            return;
        }

        if (!Emulator.isReady) return;

        if (!this.connectionAccepted()) return;

        String sso = SecureLoginInputGuard.normalizeSsoTicket(this.packet.readString());
        this.packet.readInt(); // client timestamp; retained for wire compatibility
        // Older clients end the packet after the timestamp, which reads as an empty token.
        String recoveryToken = this.packet.readString();

        if (!SecureLoginInputGuard.isValidSsoTicket(sso)) {
            clients().disposeClient(this.client);
            LOGGER.debug("Client is trying to connect with an invalid SSO ticket! Closed connection...");
            return;
        }

        if (sso.isEmpty() || sso.length() > MAX_SSO_TICKET_LENGTH) {
            clients().disposeClient(this.client);
            LOGGER.debug("Client is trying to connect with missing or invalid SSO ticket! Closed connection...");
            return;
        }

        if (this.client.getHabbo() != null) {
            clients().disposeClient(this.client);
            return;
        }

        String visitor =
                this.client.getChannel().attr(GameServerAttributes.VISITOR).get();
        GameConnectionLimits limits = GameConnectionLimits.shared();
        if (limits.isLoginBlocked(visitor, System.currentTimeMillis())) {
            clients().disposeClient(this.client);
            LOGGER.warn("Refused a ticket login from {}: too many failed logins", visitor);
            return;
        }

        // Store SSO ticket on client for grace period tracking
        this.client.setSsoTicket(sso);
        this.disposeEarlierConnection(sso);

        int lookupUserId = this.userIdForTicket(sso);
        Habbo habbo = lookupUserId > 0 ? SessionResumeManager.getInstance().resumeSession(lookupUserId) : null;
        boolean isSessionResume = habbo != null;
        if (isSessionResume) {
            if (!this.attachResumedSession(habbo)) return;
        } else {
            habbo = this.loadHabbo(sso, recoveryToken);
        }

        if (habbo == null) {
            limits.recordFailedLogin(
                    visitor, GameConnectionLimits.Settings.from(Emulator.getConfig()), System.currentTimeMillis());
            clients().disposeClient(this.client);
            LOGGER.warn("Someone tried to login with a non-existing SSO token! Closed connection...");
            return;
        }

        this.replaceDuplicateSession(habbo);
        if (!isSessionResume && !this.connectFreshLogin(habbo)) return;

        if (ClothingValidationManager.VALIDATE_ON_LOGIN) {
            String validated = ClothingValidationManager.validateLook(habbo);
            if (!validated.equals(habbo.getHabboInfo().getLook())) {
                habbo.getHabboInfo().setLook(validated);
            }
        }

        this.sendLoginState(habbo, isSessionResume);
        if (!isSessionResume) notifyBuildersClubMembership(habbo);
        this.sendAccountState(habbo);
        this.replaySanctions(habbo);

        // Skip login-only events on session resume (welcome alerts, login events, etc.)
        if (!isSessionResume) this.completeFreshLogin(habbo);
    }

    /** A connection still holding this ticket is closed, so its session parks and can be resumed below. */
    private void disposeEarlierConnection(String sso) {
        GameClient existingClient = clients().findClientBySsoTicket(sso);
        if (existingClient != null && existingClient != this.client) {
            LOGGER.info(
                    "[SessionResume] Found existing client with same SSO ticket — disposing old connection to trigger parking");
            clients().disposeClient(existingClient);
        }
    }

    private int userIdForTicket(String sso) {
        int lookupUserId = 0;
        try (java.sql.Connection conn = Emulator.getDatabase().getDataSource().getConnection();
                java.sql.PreparedStatement stmt =
                        conn.prepareStatement("SELECT id FROM users WHERE auth_ticket = ? LIMIT 1")) {
            stmt.setString(1, sso);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    lookupUserId = rs.getInt("id");
                }
            }
        } catch (Exception e) {
            LOGGER.error("Caught exception looking up user for session resume", e);
        }
        return lookupUserId;
    }

    /**
     * Puts a parked session on this connection, after the ban and maintenance checks. Returns false when the session
     * was refused and the connection closed.
     */
    private boolean attachResumedSession(Habbo habbo) {
        LOGGER.info(
                "[SessionResume] Resuming session for {} (id={})",
                habbo.getHabboInfo().getUsername(),
                habbo.getHabboInfo().getId());

        habbo.setClient(this.client);
        this.client.setHabbo(habbo);
        this.client.setMachineId(habbo.getHabboInfo().getMachineID());

        if (!habbo.passesConnectionSecurityChecks()) {
            LOGGER.warn(
                    "[SessionResume] Rejected resumed session for banned identity id={}",
                    habbo.getHabboInfo().getId());
            clients().forceDisposeClient(this.client);
            return false;
        }

        if (!MaintenanceMode.canLogin(rankIdOf(habbo))) {
            LOGGER.info(
                    "[Maintenance] Rejected resumed session for user id={} (rank below hotel.maintenance.min_rank)",
                    habbo.getHabboInfo().getId());
            this.client.sendResponse(new GenericAlertComposer(MaintenanceMode.getMessage()));
            clients().forceDisposeClient(this.client);
            return false;
        }

        Emulator.getGameEnvironment()
                .getHabboManager()
                .consumeSsoTicket(habbo.getHabboInfo().getId());
        return true;
    }

    /** Loads the user of the ticket or, when the ticket is spent, of a session recovery token. */
    private Habbo loadHabbo(String sso, String recoveryToken) {
        HabboManager habboManager = Emulator.getGameEnvironment().getHabboManager();
        Habbo habbo = habboManager.loadHabbo(sso);
        if (habbo == null && !recoveryToken.isEmpty()) {
            OptionalInt recoveredUser = SessionRecoveryRuntime.consume(recoveryToken);
            if (recoveredUser.isPresent()) {
                habbo = habboManager.loadHabboById(recoveredUser.getAsInt());
                LOGGER.info("Recovered authenticated session for user id={}", recoveredUser.getAsInt());
            }
        }
        return habbo;
    }

    private void replaceDuplicateSession(Habbo habbo) {
        GameClient previousClient =
                clients().claimAuthenticatedSession(habbo.getHabboInfo().getId(), this.client);
        if (previousClient != null && previousClient != this.client) {
            LOGGER.info(
                    "Replacing duplicate active session for user {}",
                    habbo.getHabboInfo().getId());
            clients().disconnectWithReason(previousClient, DisconnectReasonComposer.CONCURRENT_LOGIN);
        }
    }

    /**
     * Brings a freshly loaded user online on this connection. Returns false when the login was refused and the
     * connection closed.
     */
    private boolean connectFreshLogin(Habbo habbo) {
        try {
            habbo.setClient(this.client);
            this.client.setHabbo(habbo);

            if (habbo.getHabboInfo().getRank() == null) {
                throw new NullPointerException(habbo.getHabboInfo().getUsername() + " has a NON EXISTING RANK!");
            }

            // Maintenance mode: only hotel.maintenance.min_rank and above may
            // log in. The HTTP auth API refuses earlier, but the socket login
            // is a separate door (SSO ticket, recovery token), so gate here too.
            // Before connect(): a refused user never comes online, so friends
            // are not told and the closed connection leaves no session to park.
            if (!MaintenanceMode.canLogin(rankIdOf(habbo))) {
                LOGGER.info(
                        "[Maintenance] Rejected login for user id={} (rank below hotel.maintenance.min_rank)",
                        habbo.getHabboInfo().getId());
                this.client.sendResponse(new GenericAlertComposer(MaintenanceMode.getMessage()));
                clients().disposeClient(this.client);
                return false;
            }

            if (!habbo.connect()) {
                clients().disposeClient(this.client);
                return false;
            }

            // If the machine fingerprint already arrived (UniqueID before login),
            // persist it so machine/super bans can target this user.
            if (this.client.getMachineId() != null
                    && !this.client.getMachineId().isEmpty()) {
                habbo.getHabboInfo().setMachineID(this.client.getMachineId());
            }

            Emulator.getThreading().run(habbo);
            Emulator.getGameEnvironment().getHabboManager().addHabbo(habbo);
            return true;
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
            // A login that failed half way is closed, not parked for a resume.
            clients().forceDisposeClient(this.client);
            return false;
        }
    }

    private void sendLoginState(Habbo habbo, boolean isSessionResume) {
        ArrayList<ServerMessage> messages = new ArrayList<>();
        this.addSessionMessages(messages, habbo, isSessionResume);
        addHotelMessages(messages, habbo);
        this.client.sendResponses(messages);
    }

    private void addSessionMessages(ArrayList<ServerMessage> messages, Habbo habbo, boolean isSessionResume) {
        Room resumedRoom = isSessionResume ? habbo.getHabboInfo().getCurrentRoom() : null;
        int resumedRoomId = resumedRoom != null ? resumedRoom.getId() : 0;
        String nextRecoveryToken =
                SessionRecoveryRuntime.issue(habbo.getHabboInfo().getId());
        messages.add(new SecureLoginOKComposer(isSessionResume, resumedRoomId, nextRecoveryToken).compose());

        // On session resume no room is entered: the client keeps its room view alive and the habbo is
        // still in the room on the server, so a room to enter would tear the view down and rebuild it.
        if (resumedRoom != null) {
            LOGGER.info(
                    "[SessionResume] {} is still in room {} — client will resume in-place",
                    habbo.getHabboInfo().getUsername(),
                    resumedRoom.getId());
        }
        int roomIdToEnter = isSessionResume ? 0 : roomToEnter(habbo);
        messages.add(new UserHomeRoomComposer(habbo.getHabboInfo().getHomeRoom(), roomIdToEnter).compose());
        messages.add(new UserEffectsListComposer(
                        habbo,
                        habbo.getInventory().getEffectsComponent().effects.values())
                .compose());
        CollectibleWardrobe.load(habbo);
        messages.add(new UserClothesComposer(habbo).compose());
        addHabbicons(messages, habbo);

        // Which effect is on: without it the window forgets the choice at every reconnect.
        messages.add(
                new AvatarEffectSelectedComposer(habbo.getInventory().getEffectsComponent().activatedEffect).compose());
        messages.add(new NewUserIdentityComposer(habbo).compose());

        // The gift offer of the first days: the client shows it only once it is told the
        // new user experience has not been finished.
        if (!habbo.getHabboStats().nuxReward && Emulator.getConfig().getBoolean("hotel.nux.gifts.enabled")) {
            messages.add(new NewUserExperienceNotCompleteComposer().compose());
        }
    }

    /**
     * The room a fresh login enters: the user's own home room while the new user tour is not done or, with the retro
     * home room style, when one is set; otherwise the hotel's home room with the retro style.
     */
    private static int roomToEnter(Habbo habbo) {
        boolean retroHomeRoom = Emulator.getConfig().getBoolean("retro.style.homeroom");
        int homeRoom = habbo.getHabboInfo().getHomeRoom();
        if (!habbo.getHabboStats().nux || retroHomeRoom && homeRoom != 0) return homeRoom;
        if (retroHomeRoom && RoomManager.HOME_ROOM_ID > 0) return RoomManager.HOME_ROOM_ID;
        return 0;
    }

    private static void addHabbicons(ArrayList<ServerMessage> messages, Habbo habbo) {
        HabbiconService habbiconService = habbo.getHabbiconService();
        if (habbiconService == null) return;

        HabbiconService.Snapshot habbicons;
        try {
            habbicons = habbiconService.load(habbo.getHabboInfo().getId());
        } catch (SQLException e) {
            // The collection is an extra: a table that cannot be read leaves it out of the login.
            LOGGER.error(
                    "Could not load the habbicons of user id={}",
                    habbo.getHabboInfo().getId(),
                    e);
            return;
        }
        if (habbicons == null) return;

        messages.add(new UserHabbiconsComposer(habbicons).compose());
        if (!habbicons.unseen().isEmpty()) {
            messages.add(new AddHabboItemComposer(
                            habbicons.unseen().stream()
                                    .mapToInt(Integer::intValue)
                                    .toArray(),
                            AddHabboItemComposer.AddHabboItemCategory.HABBICON)
                    .compose());
        }
    }

    private static void addHotelMessages(ArrayList<ServerMessage> messages, Habbo habbo) {
        messages.add(new UserPermissionsComposer(habbo).compose());
        messages.add(new AvailableCommandsComposer(Emulator.getGameEnvironment()
                        .getCommandHandler()
                        .getCommandsForRank(habbo.getHabboInfo().getRank().getId()))
                .compose());
        messages.add(new AvailabilityStatusMessageComposer(true, false, true).compose());
        messages.add(new PingComposer().compose());
        messages.add(new EnableNotificationsComposer(Emulator.getConfig().getBoolean("bubblealerts.enabled", true))
                .compose());
        messages.add(new UserAchievementScoreComposer(habbo).compose());
        messages.add(new IsFirstLoginOfDayComposer(UsernameEvent.isFirstLoginOfDay(
                        habbo.getHabboInfo().getLastOnline(), (int) (System.currentTimeMillis() / 1000L)))
                .compose());
        messages.add(new MysteryBoxKeysComposer().compose());
        messages.add(new BuildersClubSubscriptionStatusComposer(habbo).compose());
        messages.add(new CfhTopicsMessageComposer().compose());
        messages.add(new FavoriteRoomsCountComposer(habbo).compose());
        messages.add(new GameCenterGameListComposer().compose());
        messages.add(new GameCenterAccountInfoComposer(3, 100).compose());
        // Game 0 (SnowWar) has a real games-left counter; the hub asks for
        // it when it opens (Game2GetAccountGameStatus), no placeholder here.

        messages.add(new UserClubComposer(habbo, SubscriptionHabboClub.HABBO_CLUB, UserClubComposer.RESPONSE_TYPE_LOGIN)
                .compose());

        if (habbo.hasPermission(Permission.ACC_SUPPORTTOOL)) {
            messages.add(new ModToolComposer(habbo).compose());
        }
    }

    /** Syncs the Builders Club rooms of a fresh login and warns about a membership running out or run out. */
    private static void notifyBuildersClubMembership(Habbo habbo) {
        int userId = habbo.getHabboInfo().getId();
        BuildersClubRoomSupport.syncOwnedRooms(userId);

        HabboStats stats = habbo.getHabboStats();
        if (stats.hasSubscription(Subscription.BUILDERS_CLUB)) {
            int remaining = BuildersClubRoomSupport.getMembershipSecondsLeft(userId);
            if (remaining > 0 && remaining <= (72 * 3600)) {
                BuildersClubRoomSupport.sendMembershipExpiringAlert(userId);
            }
        } else if (stats.subscriptions.stream()
                .anyMatch(subscription ->
                        subscription.getSubscriptionType().equalsIgnoreCase(Subscription.BUILDERS_CLUB))) {
            BuildersClubRoomSupport.sendMembershipExpiredAlert(
                    userId, BuildersClubRoomSupport.hasTrackedItemsInOwnedRooms(userId));
        }
    }

    private void sendAccountState(Habbo habbo) {
        this.client.sendResponse(new InventoryAchievementsComposer());

        // Official TalentTrackLevel (1203): TalentPromoCtrl needs the level pair of both
        // tracks before the hotel view decides whether to promote them.
        if (Emulator.getConfig().getBoolean("hotel.talenttrack.enabled")) {
            for (TalentTrackType talentTrackType : TalentTrackType.values()) {
                this.client.sendResponse(UserCitizinShipComposer.forHabbo(habbo, talentTrackType));
            }
        }

        // Official AccountSafetyLockStatusChange (1243): the toolbar badge stays up for as
        // long as the account is locked, so replay the stored status on every login.
        this.client.sendResponse(new UnknownStatusComposer(
                habbo.getHabboStats().safetyLocked ? SAFETY_LOCK_LOCKED : SAFETY_LOCK_UNLOCKED));

        // Official RewardTracks push: the client never requests the tracks, the progress
        // menu ("Introduction") and its claimable-rewards badge read the pushed list.
        QuestProgressEvents.loggedIn(habbo);

        // Official ModToolPreferences (31) round trip: replay where the moderator left the
        // issue handler window.
        if (habbo.hasPermission(Permission.ACC_SUPPORTTOOL) && habbo.getHabboStats().modToolWindowWidth > 0) {
            this.client.sendResponse(new ModToolIssueHandlerDimensionsComposer(
                    habbo.getHabboStats().modToolWindowX,
                    habbo.getHabboStats().modToolWindowY,
                    habbo.getHabboStats().modToolWindowWidth,
                    habbo.getHabboStats().modToolWindowHeight));
        }
    }

    /** Applies the user's latest sanction: an ongoing probation is shown, an ended trade lock or mute is lifted. */
    private void replaySanctions(Habbo habbo) {
        if (!Emulator.getConfig().getBoolean("hotel.sanctions.enabled")) return;

        ModToolSanctions modToolSanctions = Emulator.getGameEnvironment().getModToolSanctions();
        int userId = habbo.getHabboInfo().getId();
        ArrayList<ModToolSanctionItem> modToolSanctionItems =
                modToolSanctions.getSanctions(userId).get(userId);
        if (modToolSanctionItems == null || modToolSanctionItems.isEmpty()) return;

        ModToolSanctionItem item = modToolSanctionItems.get(modToolSanctionItems.size() - 1);
        int now = Emulator.getIntUnixTimestamp();

        if (item.sanctionLevel > 0 && item.probationTimestamp != 0 && item.probationTimestamp > now) {
            this.client.sendResponse(new ModToolSanctionInfoComposer(habbo));
        } else if (item.sanctionLevel > 0 && item.probationTimestamp != 0 && item.probationTimestamp <= now) {
            modToolSanctions.updateSanction(item.id, 0);
        }

        if (item.tradeLockedUntil > 0 && item.tradeLockedUntil <= now) {
            modToolSanctions.updateTradeLockedUntil(item.id, 0);
            habbo.getHabboStats().setAllowTrade(true);
        } else if (item.tradeLockedUntil > 0 && item.tradeLockedUntil > now) {
            habbo.getHabboStats().setAllowTrade(false);
        }

        if (item.isMuted && item.muteDuration <= now) {
            modToolSanctions.updateMuteDuration(item.id, 0);
            habbo.unMute();
        } else if (item.isMuted && item.muteDuration > now) {
            // mute() counts in seconds and adds to a mute already stored, which usually holds this
            // sanction already: extend it only up to the sanction's end.
            int missing = item.muteDuration - Math.max(habbo.getHabboStats().getMuteEndTime(), now);
            if (missing > 0) habbo.mute(missing, false);
        }
    }

    private void completeFreshLogin(Habbo habbo) throws Exception {
        UsernameEvent.completeLogin(this.client);

        UserLoginEvent userLoginEvent =
                new UserLoginEvent(habbo, habbo.getHabboInfo().getIpLogin());
        Emulator.getPluginManager().fireEvent(userLoginEvent);

        if (userLoginEvent.isCancelled()) {
            clients().forceDisposeClient(this.client);
            return;
        }

        if (Emulator.getConfig().getBoolean("hotel.welcome.alert.enabled")) {
            this.scheduleWelcomeAlert(habbo);
        }

        if (SubscriptionHabboClub.HC_PAYDAY_ENABLED) {
            SubscriptionHabboClub.processUnclaimed(habbo);
        }

        SubscriptionHabboClub.processClubBadge(habbo);

        Messenger.checkFriendSizeProgress(habbo);

        if (!habbo.getHabboStats().hasGottenDefaultSavedSearches) {
            this.giveDefaultSavedSearches(habbo);
        }
    }

    private void scheduleWelcomeAlert(Habbo habbo) {
        Emulator.getThreading()
                .run(
                        () -> {
                            String username = habbo.getHabboInfo().getUsername();
                            String message = HabboManager.WELCOME_MESSAGE
                                    .replace("%username%", username)
                                    .replace("%user%", username);
                            if (Emulator.getConfig().getBoolean("hotel.welcome.alert.oldstyle")) {
                                this.client.sendResponse(new MessagesForYouComposer(message.split("<br/>")));
                            } else {
                                this.client.sendResponse(new GenericAlertComposer(message));
                            }
                        },
                        Emulator.getConfig().getInt("hotel.welcome.alert.delay", 5000));
    }

    private void giveDefaultSavedSearches(Habbo habbo) {
        habbo.getHabboStats().hasGottenDefaultSavedSearches = true;
        Emulator.getThreading().run(habbo.getHabboStats());

        habbo.getHabboInfo().addSavedSearch(new NavigatorSavedSearch("official-root", ""));
        habbo.getHabboInfo().addSavedSearch(new NavigatorSavedSearch("my", ""));
        habbo.getHabboInfo().addSavedSearch(new NavigatorSavedSearch("favorites", ""));

        this.client.sendResponse(
                new NewNavigatorSavedSearchesComposer(habbo.getHabboInfo().getSavedSearches()));
    }
}
