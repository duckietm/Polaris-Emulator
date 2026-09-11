package com.eu.habbo.messages.incoming.handshake;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.MaintenanceMode;
import com.eu.habbo.habbohotel.achievements.TalentTrackType;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.gameclients.GameClientManager;
import com.eu.habbo.habbohotel.gameclients.SessionResumeManager;
import com.eu.habbo.habbohotel.messenger.Messenger;
import com.eu.habbo.habbohotel.modtool.ModToolSanctionItem;
import com.eu.habbo.habbohotel.modtool.ModToolSanctions;
import com.eu.habbo.habbohotel.navigation.NavigatorSavedSearch;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.BuildersClubRoomSupport;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.habbohotel.users.clothingvalidation.ClothingValidationManager;
import com.eu.habbo.habbohotel.users.subscriptions.Subscription;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionHabboClub;
import com.eu.habbo.messages.NoAuthMessage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.BuildersClubSubscriptionStatusComposer;
import com.eu.habbo.messages.outgoing.commands.AvailableCommandsComposer;
import com.eu.habbo.messages.outgoing.gamecenter.GameCenterAccountInfoComposer;
import com.eu.habbo.messages.outgoing.gamecenter.GameCenterGameListComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.MessagesForYouComposer;
import com.eu.habbo.messages.outgoing.habboway.nux.NewUserExperienceNotCompleteComposer;
import com.eu.habbo.messages.outgoing.habboway.nux.NewUserIdentityComposer;
import com.eu.habbo.messages.outgoing.handshake.AvailabilityStatusMessageComposer;
import com.eu.habbo.messages.outgoing.handshake.EnableNotificationsComposer;
import com.eu.habbo.messages.outgoing.handshake.PingComposer;
import com.eu.habbo.messages.outgoing.handshake.SecureLoginOKComposer;
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
import com.eu.habbo.plugin.events.users.UserLoginEvent;
import com.eu.habbo.resilience.RuntimeResilienceController;
import com.eu.habbo.resilience.RuntimeResilienceRuntime;
import com.eu.habbo.session.SessionRecoveryRuntime;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
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

    @Override
    public void handle() throws Exception {
        if (!this.client.getChannel().isOpen()) {
            Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
            return;
        }

        if (!Emulator.isReady) return;

        if (Emulator.getConfig().getBoolean("encryption.forced", false)
                && Emulator.getCrypto().isEnabled()
                && !this.client.isHandshakeFinished()) {
            Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
            LOGGER.warn("Encryption is forced and TLS Handshake isn't finished! Closed connection...");
            return;
        }

        String allowedReleases =
                Emulator.getConfig().getValue("client.release.allowed", ClientReleaseGuard.DEFAULT_ALLOWED_RELEASES);
        if (!ClientReleaseGuard.isAllowed(this.client.getReleaseVersion(), allowedReleases)) {
            LOGGER.warn(
                    "Rejected client release '{}' (allowed: '{}')", this.client.getReleaseVersion(), allowedReleases);
            Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
            return;
        }

        RuntimeResilienceController.Admission admission =
                RuntimeResilienceRuntime.admit(RuntimeResilienceController.WorkClass.INTERACTIVE);
        if (admission.effectiveAction() == RuntimeResilienceController.Action.REJECT) {
            this.client.sendResponse(new GenericAlertComposer("The hotel is temporarily busy. Please try again."));
            this.client.getChannel().close();
            return;
        }

        String sso = SecureLoginInputGuard.normalizeSsoTicket(this.packet.readString());
        this.packet.readInt(); // client timestamp; retained for wire compatibility
        String recoveryToken = "";
        try {
            recoveryToken = this.packet.readString();
        } catch (IndexOutOfBoundsException ignored) {
            // Older clients end the packet after the timestamp.
        }

        if (!SecureLoginInputGuard.isValidSsoTicket(sso)) {
            Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
            LOGGER.debug("Client is trying to connect with an invalid SSO ticket! Closed connection...");
            return;
        }

        if (sso.isEmpty() || sso.length() > MAX_SSO_TICKET_LENGTH) {
            Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
            LOGGER.debug("Client is trying to connect with missing or invalid SSO ticket! Closed connection...");
            return;
        }

        if (this.client.getHabbo() == null) {
            // Store SSO ticket on client for grace period tracking
            this.client.setSsoTicket(sso);

            GameClient existingClient =
                    Emulator.getGameServer().getGameClientManager().findClientBySsoTicket(sso);
            if (existingClient != null && existingClient != this.client) {
                LOGGER.info(
                        "[SessionResume] Found existing client with same SSO ticket — disposing old connection to trigger parking");
                Emulator.getGameServer().getGameClientManager().disposeClient(existingClient);
            }

            int lookupUserId = 0;
            try (java.sql.Connection conn =
                            Emulator.getDatabase().getDataSource().getConnection();
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

            Habbo habbo = null;
            boolean isSessionResume = false;

            if (lookupUserId > 0) {
                habbo = SessionResumeManager.getInstance().resumeSession(lookupUserId);
            }

            if (habbo != null) {
                isSessionResume = true;
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
                    Emulator.getGameServer().getGameClientManager().forceDisposeClient(this.client);
                    return;
                }

                if (!MaintenanceMode.canLogin(rankIdOf(habbo))) {
                    LOGGER.info(
                            "[Maintenance] Rejected resumed session for user id={} (rank below hotel.maintenance.min_rank)",
                            habbo.getHabboInfo().getId());
                    this.client.sendResponse(new GenericAlertComposer(MaintenanceMode.getMessage()));
                    Emulator.getGameServer().getGameClientManager().forceDisposeClient(this.client);
                    return;
                }

                Emulator.getGameEnvironment()
                        .getHabboManager()
                        .consumeSsoTicket(habbo.getHabboInfo().getId());
            } else {
                HabboManager habboManager = Emulator.getGameEnvironment().getHabboManager();
                habbo = habboManager.loadHabbo(sso);
                if (habbo == null && !recoveryToken.isEmpty()) {
                    java.util.OptionalInt recoveredUser = SessionRecoveryRuntime.consume(recoveryToken);
                    if (recoveredUser.isPresent()) {
                        habbo = habboManager.loadHabboById(recoveredUser.getAsInt());
                        LOGGER.info("Recovered authenticated session for user id={}", recoveredUser.getAsInt());
                    }
                }
            }

            if (habbo != null) {
                GameClientManager clientManager = Emulator.getGameServer().getGameClientManager();
                GameClient previousClient = clientManager.claimAuthenticatedSession(
                        habbo.getHabboInfo().getId(), this.client);
                if (previousClient != null && previousClient != this.client) {
                    LOGGER.info(
                            "Replacing duplicate active session for user {}",
                            habbo.getHabboInfo().getId());
                    clientManager.forceDisposeClient(previousClient);
                }

                if (!isSessionResume) {
                    try {
                        habbo.setClient(this.client);
                        this.client.setHabbo(habbo);
                        if (!this.client.getHabbo().connect()) {
                            Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
                            return;
                        }

                        if (this.client.getHabbo().getHabboInfo() == null) {
                            Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
                            return;
                        }

                        if (this.client.getHabbo().getHabboInfo().getRank() == null) {
                            throw new NullPointerException(
                                    habbo.getHabboInfo().getUsername() + " has a NON EXISTING RANK!");
                        }

                        // Maintenance mode: only hotel.maintenance.min_rank and above may
                        // log in. The HTTP auth API refuses earlier, but the socket login
                        // is a separate door (SSO ticket, recovery token), so gate here too.
                        if (!MaintenanceMode.canLogin(rankIdOf(this.client.getHabbo()))) {
                            LOGGER.info(
                                    "[Maintenance] Rejected login for user id={} (rank below hotel.maintenance.min_rank)",
                                    this.client.getHabbo().getHabboInfo().getId());
                            this.client.sendResponse(new GenericAlertComposer(MaintenanceMode.getMessage()));
                            Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
                            return;
                        }

                        // If the machine fingerprint already arrived (UniqueID before login),
                        // persist it so machine/super bans can target this user.
                        if (this.client.getMachineId() != null
                                && !this.client.getMachineId().isEmpty()) {
                            this.client.getHabbo().getHabboInfo().setMachineID(this.client.getMachineId());
                        }

                        Emulator.getThreading().run(habbo);
                        Emulator.getGameEnvironment().getHabboManager().addHabbo(habbo);
                    } catch (Exception e) {
                        LOGGER.error("Caught exception", e);
                        Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
                        return;
                    }
                }

                if (ClothingValidationManager.VALIDATE_ON_LOGIN) {
                    String validated = ClothingValidationManager.validateLook(this.client.getHabbo());
                    if (!validated.equals(this.client.getHabbo().getHabboInfo().getLook())) {
                        this.client.getHabbo().getHabboInfo().setLook(validated);
                    }
                }

                ArrayList<ServerMessage> messages = new ArrayList<>();

                Room resumedRoom = isSessionResume ? habbo.getHabboInfo().getCurrentRoom() : null;
                int resumedRoomId = resumedRoom != null ? resumedRoom.getId() : 0;
                String nextRecoveryToken =
                        SessionRecoveryRuntime.issue(habbo.getHabboInfo().getId());
                messages.add(new SecureLoginOKComposer(isSessionResume, resumedRoomId, nextRecoveryToken).compose());

                int roomIdToEnter = 0;

                if (isSessionResume) {
                    // On session resume, DON'T set roomIdToEnter. The client keeps its
                    // existing room view alive and the habbo is already in the room on
                    // the server. Setting roomIdToEnter = 0 prevents UserHomeRoomComposer
                    // from triggering a full room re-entry on the client (which would
                    // tear down and rebuild the room view).
                    Room currentRoom = resumedRoom;
                    if (currentRoom != null) {
                        LOGGER.info(
                                "[SessionResume] {} is still in room {} — client will resume in-place",
                                habbo.getHabboInfo().getUsername(),
                                currentRoom.getId());
                    }
                } else if (!this.client.getHabbo().getHabboStats().nux
                        || Emulator.getConfig().getBoolean("retro.style.homeroom")
                                && this.client.getHabbo().getHabboInfo().getHomeRoom() != 0)
                    roomIdToEnter = this.client.getHabbo().getHabboInfo().getHomeRoom();
                else if (!this.client.getHabbo().getHabboStats().nux
                        || Emulator.getConfig().getBoolean("retro.style.homeroom") && RoomManager.HOME_ROOM_ID > 0)
                    roomIdToEnter = RoomManager.HOME_ROOM_ID;

                messages.add(new UserHomeRoomComposer(
                                this.client.getHabbo().getHabboInfo().getHomeRoom(), roomIdToEnter)
                        .compose());
                messages.add(new UserEffectsListComposer(
                                habbo,
                                this.client
                                        .getHabbo()
                                        .getInventory()
                                        .getEffectsComponent()
                                        .effects
                                        .values())
                        .compose());
                messages.add(new UserClothesComposer(this.client.getHabbo()).compose());

                // Which effect is on: without it the window forgets the choice at every reconnect.
                messages.add(new AvatarEffectSelectedComposer(
                                this.client.getHabbo().getInventory().getEffectsComponent().activatedEffect)
                        .compose());
                messages.add(new NewUserIdentityComposer(habbo).compose());

                // The gift offer of the first days: the client shows it only once it is told the
                // new user experience has not been finished.
                if (!this.client.getHabbo().getHabboStats().nuxReward
                        && Emulator.getConfig().getBoolean("hotel.nux.gifts.enabled")) {
                    messages.add(new NewUserExperienceNotCompleteComposer().compose());
                }
                messages.add(new UserPermissionsComposer(this.client.getHabbo()).compose());
                messages.add(new AvailableCommandsComposer(Emulator.getGameEnvironment()
                                .getCommandHandler()
                                .getCommandsForRank(this.client
                                        .getHabbo()
                                        .getHabboInfo()
                                        .getRank()
                                        .getId()))
                        .compose());
                messages.add(new AvailabilityStatusMessageComposer(true, false, true).compose());
                messages.add(new PingComposer().compose());
                messages.add(
                        new EnableNotificationsComposer(Emulator.getConfig().getBoolean("bubblealerts.enabled", true))
                                .compose());
                messages.add(new UserAchievementScoreComposer(this.client.getHabbo()).compose());
                messages.add(new IsFirstLoginOfDayComposer(true).compose());
                messages.add(new MysteryBoxKeysComposer().compose());
                messages.add(new BuildersClubSubscriptionStatusComposer(this.client.getHabbo()).compose());
                messages.add(new CfhTopicsMessageComposer().compose());
                messages.add(new FavoriteRoomsCountComposer(this.client.getHabbo()).compose());
                messages.add(new GameCenterGameListComposer().compose());
                messages.add(new GameCenterAccountInfoComposer(3, 100).compose());
                messages.add(new GameCenterAccountInfoComposer(0, 100).compose());

                messages.add(new UserClubComposer(
                                this.client.getHabbo(),
                                SubscriptionHabboClub.HABBO_CLUB,
                                UserClubComposer.RESPONSE_TYPE_LOGIN)
                        .compose());

                if (this.client.getHabbo().hasPermission(Permission.ACC_SUPPORTTOOL)) {
                    messages.add(new ModToolComposer(this.client.getHabbo()).compose());
                }

                this.client.sendResponses(messages);

                if (!isSessionResume) {
                    BuildersClubRoomSupport.syncOwnedRooms(
                            this.client.getHabbo().getHabboInfo().getId());

                    boolean hasActiveBuildersClub =
                            this.client.getHabbo().getHabboStats().hasSubscription(Subscription.BUILDERS_CLUB);
                    boolean hadBuildersClubBefore = false;

                    for (com.eu.habbo.habbohotel.users.subscriptions.Subscription subscription :
                            this.client.getHabbo().getHabboStats().subscriptions) {
                        if (subscription.getSubscriptionType().equalsIgnoreCase(Subscription.BUILDERS_CLUB)) {
                            hadBuildersClubBefore = true;
                            break;
                        }
                    }

                    if (hasActiveBuildersClub) {
                        int remaining = BuildersClubRoomSupport.getMembershipSecondsLeft(
                                this.client.getHabbo().getHabboInfo().getId());

                        if (remaining > 0 && remaining <= (72 * 3600)) {
                            BuildersClubRoomSupport.sendMembershipExpiringAlert(
                                    this.client.getHabbo().getHabboInfo().getId());
                        }
                    } else if (hadBuildersClubBefore) {
                        BuildersClubRoomSupport.sendMembershipExpiredAlert(
                                this.client.getHabbo().getHabboInfo().getId(),
                                BuildersClubRoomSupport.hasTrackedItemsInOwnedRooms(
                                        this.client.getHabbo().getHabboInfo().getId()));
                    }
                }

                // Hardcoded
                // this.client.sendResponse(new ForumsTestComposer());
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

                // Official ModToolPreferences (31) round trip: replay where the moderator left the
                // issue handler window.
                if (habbo.hasPermission(Permission.ACC_SUPPORTTOOL) && habbo.getHabboStats().modToolWindowWidth > 0) {
                    this.client.sendResponse(new ModToolIssueHandlerDimensionsComposer(
                            habbo.getHabboStats().modToolWindowX,
                            habbo.getHabboStats().modToolWindowY,
                            habbo.getHabboStats().modToolWindowWidth,
                            habbo.getHabboStats().modToolWindowHeight));
                }

                ModToolSanctions modToolSanctions =
                        Emulator.getGameEnvironment().getModToolSanctions();

                if (Emulator.getConfig().getBoolean("hotel.sanctions.enabled")) {
                    Map<Integer, ArrayList<ModToolSanctionItem>> modToolSanctionItemsHashMap =
                            Emulator.getGameEnvironment()
                                    .getModToolSanctions()
                                    .getSanctions(habbo.getHabboInfo().getId());
                    ArrayList<ModToolSanctionItem> modToolSanctionItems =
                            modToolSanctionItemsHashMap.get(habbo.getHabboInfo().getId());

                    if (modToolSanctionItems != null && !modToolSanctionItems.isEmpty()) {
                        ModToolSanctionItem item = modToolSanctionItems.get(modToolSanctionItems.size() - 1);

                        if (item.sanctionLevel > 0
                                && item.probationTimestamp != 0
                                && item.probationTimestamp > Emulator.getIntUnixTimestamp()) {
                            this.client.sendResponse(new ModToolSanctionInfoComposer(this.client.getHabbo()));
                        } else if (item.sanctionLevel > 0
                                && item.probationTimestamp != 0
                                && item.probationTimestamp <= Emulator.getIntUnixTimestamp()) {
                            modToolSanctions.updateSanction(item.id, 0);
                        }

                        if (item.tradeLockedUntil > 0 && item.tradeLockedUntil <= Emulator.getIntUnixTimestamp()) {
                            modToolSanctions.updateTradeLockedUntil(item.id, 0);
                            habbo.getHabboStats().setAllowTrade(true);
                        } else if (item.tradeLockedUntil > 0
                                && item.tradeLockedUntil > Emulator.getIntUnixTimestamp()) {
                            habbo.getHabboStats().setAllowTrade(false);
                        }

                        if (item.isMuted && item.muteDuration <= Emulator.getIntUnixTimestamp()) {
                            modToolSanctions.updateMuteDuration(item.id, 0);
                            habbo.unMute();
                        } else if (item.isMuted && item.muteDuration > Emulator.getIntUnixTimestamp()) {
                            Date muteDuration = new Date((long) item.muteDuration * 1000);
                            long diff =
                                    muteDuration.getTime() - Emulator.getDate().getTime();
                            habbo.mute(Math.toIntExact(diff), false);
                        }
                    }
                }

                // Skip login-only events on session resume (welcome alerts, login events, etc.)
                if (!isSessionResume) {
                    UsernameEvent.completeLogin(this.client);

                    UserLoginEvent userLoginEvent = new UserLoginEvent(
                            habbo, this.client.getHabbo().getHabboInfo().getIpLogin());
                    Emulator.getPluginManager().fireEvent(userLoginEvent);

                    if (userLoginEvent.isCancelled()) {
                        Emulator.getGameServer().getGameClientManager().forceDisposeClient(this.client);
                        return;
                    }

                    if (Emulator.getConfig().getBoolean("hotel.welcome.alert.enabled")) {
                        final Habbo finalHabbo = habbo;
                        Emulator.getThreading()
                                .run(
                                        () -> {
                                            if (Emulator.getConfig().getBoolean("hotel.welcome.alert.oldstyle")) {
                                                SecureLoginEvent.this.client.sendResponse(
                                                        new MessagesForYouComposer(HabboManager.WELCOME_MESSAGE
                                                                .replace(
                                                                        "%username%",
                                                                        finalHabbo
                                                                                .getHabboInfo()
                                                                                .getUsername())
                                                                .replace(
                                                                        "%user%",
                                                                        finalHabbo
                                                                                .getHabboInfo()
                                                                                .getUsername())
                                                                .split("<br/>")));
                                            } else {
                                                SecureLoginEvent.this.client.sendResponse(
                                                        new GenericAlertComposer(HabboManager.WELCOME_MESSAGE
                                                                .replace(
                                                                        "%username%",
                                                                        finalHabbo
                                                                                .getHabboInfo()
                                                                                .getUsername())
                                                                .replace(
                                                                        "%user%",
                                                                        finalHabbo
                                                                                .getHabboInfo()
                                                                                .getUsername())));
                                            }
                                        },
                                        Emulator.getConfig().getInt("hotel.welcome.alert.delay", 5000));
                    }

                    if (SubscriptionHabboClub.HC_PAYDAY_ENABLED) {
                        SubscriptionHabboClub.processUnclaimed(habbo);
                    }

                    SubscriptionHabboClub.processClubBadge(habbo);

                    Messenger.checkFriendSizeProgress(habbo);

                    if (!habbo.getHabboStats().hasGottenDefaultSavedSearches) {
                        habbo.getHabboStats().hasGottenDefaultSavedSearches = true;
                        Emulator.getThreading().run(habbo.getHabboStats());

                        habbo.getHabboInfo().addSavedSearch(new NavigatorSavedSearch("official-root", ""));
                        habbo.getHabboInfo().addSavedSearch(new NavigatorSavedSearch("my", ""));
                        habbo.getHabboInfo().addSavedSearch(new NavigatorSavedSearch("favorites", ""));

                        this.client.sendResponse(new NewNavigatorSavedSearchesComposer(
                                this.client.getHabbo().getHabboInfo().getSavedSearches()));
                    }
                }
            } else {
                Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
                LOGGER.warn("Someone tried to login with a non-existing SSO token! Closed connection...");
            }
        } else {
            Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
        }
    }
}
