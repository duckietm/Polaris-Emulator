package polaris.messages.incoming.handshake;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.hotel.MaintenanceMode;
import polaris.hotel.achievements.TalentTrackType;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.gameclients.GameClientManager;
import polaris.hotel.gameclients.SessionResumeManager;
import polaris.hotel.habbicons.HabbiconService;
import polaris.hotel.messenger.Messenger;
import polaris.hotel.modtool.ModToolSanctionItem;
import polaris.hotel.modtool.ModToolSanctions;
import polaris.hotel.navigation.NavigatorSavedSearch;
import polaris.hotel.permissions.Permission;
import polaris.hotel.quests.QuestProgressEvents;
import polaris.hotel.rooms.BuildersClubRoomSupport;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomManager;
import polaris.hotel.users.User;
import polaris.hotel.users.UserManager;
import polaris.hotel.users.UserStats;
import polaris.hotel.users.clothingvalidation.ClothingValidationManager;
import polaris.hotel.users.firstlogin.FirstLoginSetup;
import polaris.hotel.users.subscriptions.Subscription;
import polaris.hotel.users.subscriptions.SubscriptionClub;
import polaris.messages.NoAuthMessage;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.collectibles.CollectibleWardrobe;
import polaris.messages.outgoing.catalog.BuildersClubSubscriptionStatusComposer;
import polaris.messages.outgoing.commands.AvailableCommandsComposer;
import polaris.messages.outgoing.gamecenter.GameCenterAccountInfoComposer;
import polaris.messages.outgoing.gamecenter.GameCenterGameListComposer;
import polaris.messages.outgoing.generic.alerts.GenericAlertComposer;
import polaris.messages.outgoing.generic.alerts.MessagesForYouComposer;
import polaris.messages.outgoing.habbicons.UserHabbiconsComposer;
import polaris.messages.outgoing.handshake.AvailabilityStatusMessageComposer;
import polaris.messages.outgoing.handshake.DisconnectReasonComposer;
import polaris.messages.outgoing.handshake.EnableNotificationsComposer;
import polaris.messages.outgoing.handshake.IsFirstLoginOfDayComposer;
import polaris.messages.outgoing.handshake.PingComposer;
import polaris.messages.outgoing.handshake.SecureLoginOKComposer;
import polaris.messages.outgoing.hotelway.nux.NewUserExperienceNotCompleteComposer;
import polaris.messages.outgoing.hotelway.nux.NewUserIdentityComposer;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.AvatarEffectSelectedComposer;
import polaris.messages.outgoing.inventory.InventoryAchievementsComposer;
import polaris.messages.outgoing.inventory.UserEffectsListComposer;
import polaris.messages.outgoing.modtool.CfhTopicsMessageComposer;
import polaris.messages.outgoing.modtool.ModToolComposer;
import polaris.messages.outgoing.modtool.ModToolIssueHandlerDimensionsComposer;
import polaris.messages.outgoing.modtool.ModToolSanctionInfoComposer;
import polaris.messages.outgoing.mysterybox.MysteryBoxKeysComposer;
import polaris.messages.outgoing.navigator.NewNavigatorSavedSearchesComposer;
import polaris.messages.outgoing.users.AccountSafetyLockStatusChangeComposer;
import polaris.messages.outgoing.users.FavoriteRoomsCountComposer;
import polaris.messages.outgoing.users.UserAchievementScoreComposer;
import polaris.messages.outgoing.users.UserCitizinShipComposer;
import polaris.messages.outgoing.users.UserClothesComposer;
import polaris.messages.outgoing.users.UserClubComposer;
import polaris.messages.outgoing.users.UserHomeRoomComposer;
import polaris.messages.outgoing.users.UserPermissionsComposer;
import polaris.networking.gameserver.GameServerAttributes;
import polaris.networking.gameserver.auth.GameConnectionLimits;
import polaris.plugin.events.users.UserLoginEvent;
import polaris.resilience.RuntimeResilienceController;
import polaris.resilience.RuntimeResilienceRuntime;
import polaris.session.SessionRecoveryRuntime;

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

    private static int rankIdOf(User user) {
        if (user == null || user.getUserInfo() == null || user.getUserInfo().getRank() == null) {
            return Integer.MIN_VALUE;
        }
        return user.getUserInfo().getRank().getId();
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

        if (this.client.getUser() != null) {
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
        User user = lookupUserId > 0 ? SessionResumeManager.getInstance().resumeSession(lookupUserId) : null;
        boolean isSessionResume = user != null;
        if (isSessionResume) {
            if (!this.attachResumedSession(user)) return;
        } else {
            user = this.loadUser(sso, recoveryToken);
            if (user != null) {
                // A session parked for this user would expire later and log out the fresh one: end it now.
                SessionResumeManager.getInstance()
                        .disposeGhostSession(user.getUserInfo().getId());
            }
        }

        if (user == null) {
            limits.recordFailedLogin(
                    visitor, GameConnectionLimits.Settings.from(Emulator.getConfig()), System.currentTimeMillis());
            clients().disposeClient(this.client);
            LOGGER.warn("Someone tried to login with a non-existing SSO token! Closed connection...");
            return;
        }

        this.replaceDuplicateSession(user);
        if (!isSessionResume && !this.connectFreshLogin(user)) return;

        if (ClothingValidationManager.VALIDATE_ON_LOGIN) {
            String validated = ClothingValidationManager.validateLook(user);
            if (!validated.equals(user.getUserInfo().getLook())) {
                user.getUserInfo().setLook(validated);
            }
        }

        this.sendLoginState(user, isSessionResume);
        if (!isSessionResume) notifyBuildersClubMembership(user);
        this.sendAccountState(user);
        this.replaySanctions(user);

        // Skip login-only events on session resume (welcome alerts, login events, etc.)
        if (!isSessionResume) this.completeFreshLogin(user);
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
    private boolean attachResumedSession(User user) {
        LOGGER.info(
                "[SessionResume] Resuming session for {} (id={})",
                user.getUserInfo().getUsername(),
                user.getUserInfo().getId());

        user.setClient(this.client);
        this.client.setUser(user);
        this.client.setMachineId(user.getUserInfo().getMachineID());

        if (!user.passesConnectionSecurityChecks()) {
            LOGGER.warn(
                    "[SessionResume] Rejected resumed session for banned identity id={}",
                    user.getUserInfo().getId());
            clients().forceDisposeClient(this.client);
            return false;
        }

        if (!MaintenanceMode.canLogin(rankIdOf(user))) {
            LOGGER.info(
                    "[Maintenance] Rejected resumed session for user id={} (rank below hotel.maintenance.min_rank)",
                    user.getUserInfo().getId());
            this.client.sendResponse(new GenericAlertComposer(MaintenanceMode.getMessage()));
            clients().forceDisposeClient(this.client);
            return false;
        }

        Emulator.getGameEnvironment()
                .getUserManager()
                .consumeSsoTicket(user.getUserInfo().getId());
        return true;
    }

    /** Loads the user of the ticket or, when the ticket is spent, of a session recovery token. */
    private User loadUser(String sso, String recoveryToken) {
        UserManager userManager = Emulator.getGameEnvironment().getUserManager();
        User user = userManager.loadUser(sso);
        if (user == null && !recoveryToken.isEmpty()) {
            OptionalInt recoveredUser = SessionRecoveryRuntime.consume(recoveryToken);
            if (recoveredUser.isPresent()) {
                // A parked session saves what it holds before the user is read again.
                SessionResumeManager.getInstance().disposeGhostSession(recoveredUser.getAsInt());
                user = userManager.loadUserById(recoveredUser.getAsInt());
                LOGGER.info("Recovered authenticated session for user id={}", recoveredUser.getAsInt());
            }
        }
        return user;
    }

    private void replaceDuplicateSession(User user) {
        GameClient previousClient =
                clients().claimAuthenticatedSession(user.getUserInfo().getId(), this.client);
        if (previousClient != null && previousClient != this.client) {
            LOGGER.info(
                    "Replacing duplicate active session for user {}",
                    user.getUserInfo().getId());
            clients().disconnectWithReason(previousClient, DisconnectReasonComposer.CONCURRENT_LOGIN);
        }
    }

    /**
     * Brings a freshly loaded user online on this connection. Returns false when the login was refused and the
     * connection closed.
     */
    private boolean connectFreshLogin(User user) {
        try {
            user.setClient(this.client);
            this.client.setUser(user);

            if (user.getUserInfo().getRank() == null) {
                throw new NullPointerException(user.getUserInfo().getUsername() + " has a NON EXISTING RANK!");
            }

            // Maintenance mode: only hotel.maintenance.min_rank and above may
            // log in. The HTTP auth API refuses earlier, but the socket login
            // is a separate door (SSO ticket, recovery token), so gate here too.
            // Before connect(): a refused user never comes online, so friends
            // are not told and the closed connection leaves no session to park.
            if (!MaintenanceMode.canLogin(rankIdOf(user))) {
                LOGGER.info(
                        "[Maintenance] Rejected login for user id={} (rank below hotel.maintenance.min_rank)",
                        user.getUserInfo().getId());
                this.client.sendResponse(new GenericAlertComposer(MaintenanceMode.getMessage()));
                clients().disposeClient(this.client);
                return false;
            }

            if (!user.connect()) {
                clients().disposeClient(this.client);
                return false;
            }

            // If the machine fingerprint already arrived (UniqueID before login),
            // persist it so machine/super bans can target this user.
            if (this.client.getMachineId() != null
                    && !this.client.getMachineId().isEmpty()) {
                user.getUserInfo().setMachineID(this.client.getMachineId());
            }

            Emulator.getThreading().run(user);
            Emulator.getGameEnvironment().getUserManager().addUser(user);
            return true;
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
            // A login that failed half way is closed, not parked for a resume.
            clients().forceDisposeClient(this.client);
            return false;
        }
    }

    private void sendLoginState(User user, boolean isSessionResume) {
        ArrayList<ServerMessage> messages = new ArrayList<>();
        this.addSessionMessages(messages, user, isSessionResume);
        addHotelMessages(messages, user);
        this.client.sendResponses(messages);
    }

    private void addSessionMessages(ArrayList<ServerMessage> messages, User user, boolean isSessionResume) {
        Room resumedRoom = isSessionResume ? user.getUserInfo().getCurrentRoom() : null;
        int resumedRoomId = resumedRoom != null ? resumedRoom.getId() : 0;
        String nextRecoveryToken =
                SessionRecoveryRuntime.issue(user.getUserInfo().getId());
        messages.add(new SecureLoginOKComposer(isSessionResume, resumedRoomId, nextRecoveryToken).compose());

        // On session resume no room is entered: the client keeps its room view alive and the user is
        // still in the room on the server, so a room to enter would tear the view down and rebuild it.
        if (resumedRoom != null) {
            LOGGER.info(
                    "[SessionResume] {} is still in room {} — client will resume in-place",
                    user.getUserInfo().getUsername(),
                    resumedRoom.getId());
        }
        int roomIdToEnter = isSessionResume ? 0 : roomToEnter(user);
        messages.add(new UserHomeRoomComposer(user.getUserInfo().getHomeRoom(), roomIdToEnter).compose());
        messages.add(new UserEffectsListComposer(
                        user, user.getInventory().getEffectsComponent().effects.values())
                .compose());
        CollectibleWardrobe.load(user);
        messages.add(new UserClothesComposer(user).compose());
        addHabbicons(messages, user);

        // Which effect is on: without it the window forgets the choice at every reconnect.
        messages.add(
                new AvatarEffectSelectedComposer(user.getInventory().getEffectsComponent().activatedEffect).compose());
        messages.add(new NewUserIdentityComposer(user).compose());

        // A CMS account's first login: the look and starter room its register page never asked for.
        FirstLoginSetup.atLogin(user, messages);

        // The gift offer of the first days: the client shows it only once it is told the
        // new user experience has not been finished.
        if (!user.getUserStats().nuxReward && Emulator.getConfig().getBoolean("hotel.nux.gifts.enabled")) {
            messages.add(new NewUserExperienceNotCompleteComposer().compose());
        }
    }

    /**
     * The room a fresh login enters: the user's own home room while the new user tour is not done or, with the retro
     * home room style, when one is set; otherwise the hotel's home room with the retro style.
     */
    private static int roomToEnter(User user) {
        boolean retroHomeRoom = Emulator.getConfig().getBoolean("retro.style.homeroom");
        int homeRoom = user.getUserInfo().getHomeRoom();
        if (!user.getUserStats().nux || retroHomeRoom && homeRoom != 0) return homeRoom;
        if (retroHomeRoom && RoomManager.HOME_ROOM_ID > 0) return RoomManager.HOME_ROOM_ID;
        return 0;
    }

    private static void addHabbicons(ArrayList<ServerMessage> messages, User user) {
        HabbiconService habbiconService = user.getHabbiconService();
        if (habbiconService == null) return;

        HabbiconService.Snapshot habbicons;
        try {
            habbicons = habbiconService.load(user.getUserInfo().getId());
        } catch (SQLException e) {
            // The collection is an extra: a table that cannot be read leaves it out of the login.
            LOGGER.error(
                    "Could not load the habbicons of user id={}",
                    user.getUserInfo().getId(),
                    e);
            return;
        }
        if (habbicons == null) return;

        messages.add(new UserHabbiconsComposer(habbicons).compose());
        if (!habbicons.unseen().isEmpty()) {
            messages.add(new AddUserItemComposer(
                            habbicons.unseen().stream()
                                    .mapToInt(Integer::intValue)
                                    .toArray(),
                            AddUserItemComposer.AddUserItemCategory.HABBICON)
                    .compose());
        }
    }

    private static void addHotelMessages(ArrayList<ServerMessage> messages, User user) {
        messages.add(new UserPermissionsComposer(user).compose());
        messages.add(new AvailableCommandsComposer(Emulator.getGameEnvironment()
                        .getCommandHandler()
                        .getCommandsForRank(user.getUserInfo().getRank().getId()))
                .compose());
        messages.add(new AvailabilityStatusMessageComposer(true, false, true).compose());
        messages.add(new PingComposer().compose());
        messages.add(new EnableNotificationsComposer(Emulator.getConfig().getBoolean("bubblealerts.enabled", true))
                .compose());
        messages.add(new UserAchievementScoreComposer(user).compose());
        messages.add(new IsFirstLoginOfDayComposer(UsernameEvent.isFirstLoginOfDay(
                        user.getUserInfo().getLastOnline(), (int) (System.currentTimeMillis() / 1000L)))
                .compose());
        messages.add(new MysteryBoxKeysComposer().compose());
        messages.add(new BuildersClubSubscriptionStatusComposer(user).compose());
        messages.add(new CfhTopicsMessageComposer().compose());
        messages.add(new FavoriteRoomsCountComposer(user).compose());
        messages.add(new GameCenterGameListComposer().compose());
        messages.add(new GameCenterAccountInfoComposer(3, 100).compose());
        // Game 0 (SnowWar) has a real games-left counter; the hub asks for
        // it when it opens (Game2GetAccountGameStatus), no placeholder here.

        messages.add(new UserClubComposer(user, SubscriptionClub.CLUB, UserClubComposer.RESPONSE_TYPE_LOGIN).compose());

        if (user.hasPermission(Permission.ACC_SUPPORTTOOL)) {
            messages.add(new ModToolComposer(user).compose());
        }
    }

    /** Syncs the Builders Club rooms of a fresh login and warns about a membership running out or run out. */
    private static void notifyBuildersClubMembership(User user) {
        int userId = user.getUserInfo().getId();
        BuildersClubRoomSupport.syncOwnedRooms(userId);

        UserStats stats = user.getUserStats();
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

    private void sendAccountState(User user) {
        this.client.sendResponse(new InventoryAchievementsComposer());

        // Official TalentTrackLevel (1203): TalentPromoCtrl needs the level pair of both
        // tracks before the hotel view decides whether to promote them.
        if (Emulator.getConfig().getBoolean("hotel.talenttrack.enabled")) {
            for (TalentTrackType talentTrackType : TalentTrackType.values()) {
                this.client.sendResponse(UserCitizinShipComposer.forUser(user, talentTrackType));
            }
        }

        // Official AccountSafetyLockStatusChange (1243): the toolbar badge stays up for as
        // long as the account is locked, so replay the stored status on every login.
        this.client.sendResponse(new AccountSafetyLockStatusChangeComposer(
                user.getUserStats().safetyLocked ? SAFETY_LOCK_LOCKED : SAFETY_LOCK_UNLOCKED));

        // Official RewardTracks push: the client never requests the tracks, the progress
        // menu ("Introduction") and its claimable-rewards badge read the pushed list.
        QuestProgressEvents.loggedIn(user);

        // Official ModToolPreferences (31) round trip: replay where the moderator left the
        // issue handler window.
        if (user.hasPermission(Permission.ACC_SUPPORTTOOL) && user.getUserStats().modToolWindowWidth > 0) {
            this.client.sendResponse(new ModToolIssueHandlerDimensionsComposer(
                    user.getUserStats().modToolWindowX,
                    user.getUserStats().modToolWindowY,
                    user.getUserStats().modToolWindowWidth,
                    user.getUserStats().modToolWindowHeight));
        }
    }

    /** Applies the user's latest sanction: an ongoing probation is shown, an ended trade lock or mute is lifted. */
    private void replaySanctions(User user) {
        if (!Emulator.getConfig().getBoolean("hotel.sanctions.enabled")) return;

        ModToolSanctions modToolSanctions = Emulator.getGameEnvironment().getModToolSanctions();
        int userId = user.getUserInfo().getId();
        ArrayList<ModToolSanctionItem> modToolSanctionItems =
                modToolSanctions.getSanctions(userId).get(userId);
        if (modToolSanctionItems == null || modToolSanctionItems.isEmpty()) return;

        ModToolSanctionItem item = modToolSanctionItems.get(modToolSanctionItems.size() - 1);
        int now = Emulator.getIntUnixTimestamp();

        if (item.sanctionLevel > 0 && item.probationTimestamp != 0 && item.probationTimestamp > now) {
            this.client.sendResponse(new ModToolSanctionInfoComposer(user));
        } else if (item.sanctionLevel > 0 && item.probationTimestamp != 0 && item.probationTimestamp <= now) {
            modToolSanctions.updateSanction(item.id, 0);
        }

        if (item.tradeLockedUntil > 0 && item.tradeLockedUntil <= now) {
            modToolSanctions.updateTradeLockedUntil(item.id, 0);
            user.getUserStats().setAllowTrade(true);
        } else if (item.tradeLockedUntil > 0 && item.tradeLockedUntil > now) {
            user.getUserStats().setAllowTrade(false);
        }

        if (item.isMuted && item.muteDuration <= now) {
            modToolSanctions.updateMuteDuration(item.id, 0);
            user.unMute();
        } else if (item.isMuted && item.muteDuration > now) {
            // mute() counts in seconds and adds to a mute already stored, which usually holds this
            // sanction already: extend it only up to the sanction's end.
            int missing = item.muteDuration - Math.max(user.getUserStats().getMuteEndTime(), now);
            if (missing > 0) user.mute(missing, false);
        }
    }

    private void completeFreshLogin(User user) throws Exception {
        UsernameEvent.completeLogin(this.client);

        UserLoginEvent userLoginEvent =
                new UserLoginEvent(user, user.getUserInfo().getIpLogin());
        Emulator.getPluginManager().fireEvent(userLoginEvent);

        if (userLoginEvent.isCancelled()) {
            clients().forceDisposeClient(this.client);
            return;
        }

        if (Emulator.getConfig().getBoolean("hotel.welcome.alert.enabled")) {
            this.scheduleWelcomeAlert(user);
        }

        if (SubscriptionClub.HC_PAYDAY_ENABLED) {
            SubscriptionClub.processUnclaimed(user);
        }

        SubscriptionClub.processClubBadge(user);

        Messenger.checkFriendSizeProgress(user);

        if (!user.getUserStats().hasGottenDefaultSavedSearches) {
            this.giveDefaultSavedSearches(user);
        }
    }

    private void scheduleWelcomeAlert(User user) {
        Emulator.getThreading()
                .run(
                        () -> {
                            String username = user.getUserInfo().getUsername();
                            String message = UserManager.WELCOME_MESSAGE
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

    private void giveDefaultSavedSearches(User user) {
        user.getUserStats().hasGottenDefaultSavedSearches = true;
        Emulator.getThreading().run(user.getUserStats());

        user.getUserInfo().addSavedSearch(new NavigatorSavedSearch("official-root", ""));
        user.getUserInfo().addSavedSearch(new NavigatorSavedSearch("my", ""));
        user.getUserInfo().addSavedSearch(new NavigatorSavedSearch("favorites", ""));

        this.client.sendResponse(
                new NewNavigatorSavedSearchesComposer(user.getUserInfo().getSavedSearches()));
    }
}
