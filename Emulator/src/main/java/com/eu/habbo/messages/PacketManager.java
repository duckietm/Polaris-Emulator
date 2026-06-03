package com.eu.habbo.messages;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.monitoring.EmulatorNetworkStats;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.achievements.RequestAchievementConfigurationEvent;
import com.eu.habbo.messages.incoming.achievements.RequestAchievementsEvent;
import com.eu.habbo.messages.incoming.ambassadors.AmbassadorAlertCommandEvent;
import com.eu.habbo.messages.incoming.ambassadors.AmbassadorVisitCommandEvent;
import com.eu.habbo.messages.incoming.camera.*;
import com.eu.habbo.messages.incoming.catalog.*;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.*;
import com.eu.habbo.messages.incoming.furnieditor.*;
import com.eu.habbo.messages.incoming.catalog.marketplace.*;
import com.eu.habbo.messages.incoming.catalog.recycler.OpenRecycleBoxEvent;
import com.eu.habbo.messages.incoming.catalog.recycler.RecycleEvent;
import com.eu.habbo.messages.incoming.catalog.recycler.ReloadRecyclerEvent;
import com.eu.habbo.messages.incoming.catalog.recycler.RequestRecyclerLogicEvent;
import com.eu.habbo.messages.incoming.crafting.*;
import com.eu.habbo.messages.incoming.events.calendar.AdventCalendarForceOpenEvent;
import com.eu.habbo.messages.incoming.events.calendar.AdventCalendarOpenDayEvent;
import com.eu.habbo.messages.incoming.floorplaneditor.FloorPlanEditorRequestBlockedTilesEvent;
import com.eu.habbo.messages.incoming.floorplaneditor.FloorPlanEditorRequestDoorSettingsEvent;
import com.eu.habbo.messages.incoming.floorplaneditor.FloorPlanEditorSaveEvent;
import com.eu.habbo.messages.incoming.friends.*;
import com.eu.habbo.messages.incoming.gamecenter.*;
import com.eu.habbo.messages.incoming.guardians.GuardianAcceptRequestEvent;
import com.eu.habbo.messages.incoming.guardians.GuardianNoUpdatesWantedEvent;
import com.eu.habbo.messages.incoming.guardians.GuardianVoteEvent;
import com.eu.habbo.messages.incoming.guides.*;
import com.eu.habbo.messages.incoming.guilds.*;
import com.eu.habbo.messages.incoming.guilds.forums.*;
import com.eu.habbo.messages.incoming.handshake.*;
import com.eu.habbo.messages.incoming.helper.MySanctionStatusEvent;
import com.eu.habbo.messages.incoming.helper.RequestTalentTrackEvent;
import com.eu.habbo.messages.incoming.hotelview.*;
import com.eu.habbo.messages.incoming.inventory.*;
import com.eu.habbo.messages.incoming.inventory.nickicons.*;
import com.eu.habbo.messages.incoming.inventory.prefixes.*;
import com.eu.habbo.messages.incoming.mentions.*;
import com.eu.habbo.messages.incoming.modtool.*;
import com.eu.habbo.messages.incoming.navigator.*;
import com.eu.habbo.messages.incoming.polls.AnswerPollEvent;
import com.eu.habbo.messages.incoming.polls.CancelPollEvent;
import com.eu.habbo.messages.incoming.polls.GetPollDataEvent;
import com.eu.habbo.messages.incoming.rooms.*;
import com.eu.habbo.messages.incoming.rooms.bots.BotPickupEvent;
import com.eu.habbo.messages.incoming.rooms.bots.BotPlaceEvent;
import com.eu.habbo.messages.incoming.rooms.bots.BotSaveSettingsEvent;
import com.eu.habbo.messages.incoming.rooms.bots.BotSettingsEvent;
import com.eu.habbo.messages.incoming.rooms.items.*;
import com.eu.habbo.messages.incoming.rooms.items.jukebox.*;
import com.eu.habbo.messages.incoming.rooms.items.lovelock.LoveLockStartConfirmEvent;
import com.eu.habbo.messages.incoming.rooms.items.rentablespace.RentSpaceCancelEvent;
import com.eu.habbo.messages.incoming.rooms.items.rentablespace.RentSpaceEvent;
import com.eu.habbo.messages.incoming.rooms.items.youtube.YoutubeRequestPlaylistChange;
import com.eu.habbo.messages.incoming.rooms.items.youtube.YoutubeRequestPlaylists;
import com.eu.habbo.messages.incoming.rooms.items.youtube.YoutubeRequestStateChange;
import com.eu.habbo.messages.incoming.rooms.pets.*;
import com.eu.habbo.messages.incoming.rooms.promotions.BuyRoomPromotionEvent;
import com.eu.habbo.messages.incoming.rooms.promotions.RequestPromotionRoomsEvent;
import com.eu.habbo.messages.incoming.rooms.promotions.UpdateRoomPromotionEvent;
import com.eu.habbo.messages.incoming.rooms.users.*;
import com.eu.habbo.messages.incoming.trading.*;
import com.eu.habbo.messages.incoming.translation.TranslationLanguagesRequestEvent;
import com.eu.habbo.messages.incoming.translation.TranslationTextRequestEvent;
import com.eu.habbo.messages.incoming.unknown.RequestResolutionEvent;
import com.eu.habbo.messages.incoming.unknown.UnknownEvent1;
import com.eu.habbo.messages.incoming.users.*;
import com.eu.habbo.messages.incoming.wired.WiredApplySetConditionsEvent;
import com.eu.habbo.messages.incoming.wired.WiredConditionSaveDataEvent;
import com.eu.habbo.messages.incoming.wired.WiredEffectSaveDataEvent;
import com.eu.habbo.messages.incoming.wired.WiredMonitorRequestEvent;
import com.eu.habbo.messages.incoming.wired.WiredRoomSettingsRequestEvent;
import com.eu.habbo.messages.incoming.wired.WiredRoomSettingsSaveEvent;
import com.eu.habbo.messages.incoming.wired.WiredUserInspectMoveEvent;
import com.eu.habbo.messages.incoming.wired.WiredUserVariableManageEvent;
import com.eu.habbo.messages.incoming.wired.WiredUserVariableUpdateEvent;
import com.eu.habbo.messages.incoming.wired.WiredUserVariablesRequestEvent;
import com.eu.habbo.messages.incoming.wired.WiredTriggerSaveDataEvent;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.events.emulator.EmulatorConfigUpdatedEvent;
import gnu.trove.map.hash.THashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class PacketManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PacketManager.class);

    private static final List<Integer> logList = new ArrayList<>();
    public static boolean DEBUG_SHOW_PACKETS = false;
    public static boolean MULTI_THREADED_PACKET_HANDLING = false;
    private final THashMap<Integer, Class<? extends MessageHandler>> incoming;
    private final THashMap<Integer, List<ICallable>> callables;
    private final PacketNames names;

    public PacketManager() throws Exception {
        this.incoming = new THashMap<>();
        this.callables = new THashMap<>();
        this.names = new PacketNames();
        this.names.initialize();

        this.registerHandshake();
        this.registerCatalog();
        this.registerEvent();
        this.registerFriends();
        this.registerNavigator();
        this.registerUsers();
        this.registerHotelview();
        this.registerInventory();
        this.registerRooms();
        this.registerPolls();
        this.registerUnknown();
        this.registerModTool();
        this.registerTrading();
        this.registerGuilds();
        this.registerPets();
        this.registerWired();
        this.registerTranslation();
        this.registerAchievements();
        this.registerFloorPlanEditor();
        this.registerAmbassadors();
        this.registerGuides();
        this.registerCrafting();
        this.registerCamera();
        this.registerGameCenter();
    }

    public PacketNames getNames() {
        return names;
    }

    @EventHandler
    public static void onConfigurationUpdated(EmulatorConfigUpdatedEvent event) {
        logList.clear();

        for (String s : Emulator.getConfig().getValue("debug.show.headers").split(";")) {
            try {
                logList.add(Integer.parseInt(s));
            } catch (NumberFormatException e) {

            }
        }
    }

    public void registerHandler(Integer header, Class<? extends MessageHandler> handler) throws Exception {
        if (header < 0)
            return;

        if (this.incoming.containsKey(header)) {
            throw new Exception("Header already registered. Failed to register " + handler.getName() + " with header " + header);
        }

        this.incoming.putIfAbsent(header, handler);
    }

    public void registerCallable(Integer header, ICallable callable) {
        this.callables.putIfAbsent(header, new ArrayList<>());
        this.callables.get(header).add(callable);
    }

    public void unregisterCallables(Integer header, ICallable callable) {
        if (this.callables.containsKey(header)) {
            this.callables.get(header).remove(callable);
        }
    }

    public void unregisterCallables(Integer header) {
        if (this.callables.containsKey(header)) {
            this.callables.clear();
        }
    }

    public void handlePacket(GameClient client, ClientMessage packet) {
        if (client == null || Emulator.isShuttingDown)
            return;

        try {
            EmulatorNetworkStats.recordIncoming(packet.bytesAvailable() + 6);

            if (this.isRegistered(packet.getMessageId())) {
                Class<? extends MessageHandler> handlerClass = this.incoming.get(packet.getMessageId());

                if (handlerClass == null) throw new Exception("Unknown message " + packet.getMessageId());

                if (client.getHabbo() == null && !handlerClass.isAnnotationPresent(NoAuthMessage.class)) {
                    if (DEBUG_SHOW_PACKETS) {
                        LOGGER.warn("Client packet {} requires an authenticated session.", packet.getMessageId());
                    }

                    return;
                }

                final MessageHandler handler = handlerClass.getDeclaredConstructor().newInstance();

                if (handler.getRatelimit() > 0) {
                    if (client.messageTimestamps.containsKey(handlerClass) && System.currentTimeMillis() - client.messageTimestamps.get(handlerClass) < handler.getRatelimit()) {
                        if (PacketManager.DEBUG_SHOW_PACKETS) {
                            LOGGER.warn("Client packet {} was ratelimited.", packet.getMessageId());
                        }

                        return;
                    } else {
                        client.messageTimestamps.put(handlerClass, System.currentTimeMillis());
                    }
                }

                if (logList.contains(packet.getMessageId()) && client.getHabbo() != null) {
                    LOGGER.info("User {} sent packet {} with body {}", client.getHabbo().getHabboInfo().getUsername(), packet.getMessageId(), packet.getMessageBody());
                }

                handler.client = client;
                handler.packet = packet;

                if (this.callables.containsKey(packet.getMessageId())) {
                    for (ICallable callable : this.callables.get(packet.getMessageId())) {
                        callable.call(handler);
                    }
                }

                if (!handler.isCancelled) {
                    handler.handle();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }

    boolean isRegistered(int header) {
        return this.incoming.containsKey(header);
    }

    private void registerAmbassadors() throws Exception {
        this.registerHandler(Incoming.AmbassadorAlertCommandEvent, AmbassadorAlertCommandEvent.class);
        this.registerHandler(Incoming.AmbassadorVisitCommandEvent, AmbassadorVisitCommandEvent.class);
    }

    private void registerCatalog() throws Exception {
        this.registerHandler(Incoming.RequestRecylerLogicEvent, RequestRecyclerLogicEvent.class);
        this.registerHandler(Incoming.RequestDiscountEvent, RequestDiscountEvent.class);
        this.registerHandler(Incoming.RequestGiftConfigurationEvent, RequestGiftConfigurationEvent.class);
        this.registerHandler(Incoming.GetMarketplaceConfigEvent, RequestMarketplaceConfigEvent.class);
        this.registerHandler(Incoming.RequestCatalogModeEvent, RequestCatalogModeEvent.class);
        this.registerHandler(Incoming.BuildersClubQueryFurniCountEvent, BuildersClubQueryFurniCountEvent.class);
        this.registerHandler(Incoming.BuildersClubPlaceRoomItemEvent, BuildersClubPlaceRoomItemEvent.class);
        this.registerHandler(Incoming.BuildersClubPlaceWallItemEvent, BuildersClubPlaceWallItemEvent.class);
        this.registerHandler(Incoming.RequestCatalogPageEvent, RequestCatalogPageEvent.class);
        this.registerHandler(Incoming.CatalogBuyItemAsGiftEvent, CatalogBuyItemAsGiftEvent.class);
        this.registerHandler(Incoming.CatalogBuyItemEvent, CatalogBuyItemEvent.class);
        this.registerHandler(Incoming.RedeemVoucherEvent, RedeemVoucherEvent.class);
        this.registerHandler(Incoming.ReloadRecyclerEvent, ReloadRecyclerEvent.class);
        this.registerHandler(Incoming.RecycleEvent, RecycleEvent.class);
        this.registerHandler(Incoming.OpenRecycleBoxEvent, OpenRecycleBoxEvent.class);
        this.registerHandler(Incoming.RequestOwnItemsEvent, RequestOwnItemsEvent.class);
        this.registerHandler(Incoming.TakeBackItemEvent, TakeBackItemEvent.class);
        this.registerHandler(Incoming.RequestOffersEvent, RequestOffersEvent.class);
        this.registerHandler(Incoming.RequestItemInfoEvent, RequestItemInfoEvent.class);
        this.registerHandler(Incoming.BuyItemEvent, BuyItemEvent.class);
        this.registerHandler(Incoming.RequestSellItemEvent, RequestSellItemEvent.class);
        this.registerHandler(Incoming.SellItemEvent, SellItemEvent.class);
        this.registerHandler(Incoming.RequestCreditsEvent, RequestCreditsEvent.class);
        this.registerHandler(Incoming.RequestPetBreedsEvent, RequestPetBreedsEvent.class);
        this.registerHandler(Incoming.CheckPetNameEvent, CheckPetNameEvent.class);
        this.registerHandler(Incoming.GetClubDataEvent, RequestClubDataEvent.class);
        this.registerHandler(Incoming.RequestClubGiftsEvent, RequestClubGiftsEvent.class);
        this.registerHandler(Incoming.CatalogSearchedItemEvent, CatalogSearchedItemEvent.class);
        this.registerHandler(Incoming.PurchaseTargetOfferEvent, PurchaseTargetOfferEvent.class);
        this.registerHandler(Incoming.TargetOfferStateEvent, TargetOfferStateEvent.class);
        this.registerHandler(Incoming.CatalogSelectClubGiftEvent, CatalogSelectClubGiftEvent.class);
        this.registerHandler(Incoming.RequestClubCenterEvent, RequestClubCenterEvent.class);
        this.registerHandler(Incoming.CatalogRequestClubDiscountEvent, CatalogRequestClubDiscountEvent.class);
        this.registerHandler(Incoming.CatalogBuyClubDiscountEvent, CatalogBuyClubDiscountEvent.class);

        // Furni Editor
        this.registerHandler(Incoming.FurniEditorSearchEvent, FurniEditorSearchEvent.class);
        this.registerHandler(Incoming.FurniEditorDetailEvent, FurniEditorDetailEvent.class);
        this.registerHandler(Incoming.FurniEditorBySpriteEvent, FurniEditorBySpriteEvent.class);
        this.registerHandler(Incoming.FurniEditorInteractionsEvent, FurniEditorInteractionsEvent.class);
        this.registerHandler(Incoming.FurniEditorUpdateEvent, FurniEditorUpdateEvent.class);
        this.registerHandler(Incoming.FurniEditorDeleteEvent, FurniEditorDeleteEvent.class);

        // Catalog Admin
        this.registerHandler(Incoming.CatalogAdminSavePageEvent, CatalogAdminSavePageEvent.class);
        this.registerHandler(Incoming.CatalogAdminCreatePageEvent, CatalogAdminCreatePageEvent.class);
        this.registerHandler(Incoming.CatalogAdminDeletePageEvent, CatalogAdminDeletePageEvent.class);
        this.registerHandler(Incoming.CatalogAdminSaveOfferEvent, CatalogAdminSaveOfferEvent.class);
        this.registerHandler(Incoming.CatalogAdminCreateOfferEvent, CatalogAdminCreateOfferEvent.class);
        this.registerHandler(Incoming.CatalogAdminDeleteOfferEvent, CatalogAdminDeleteOfferEvent.class);
        this.registerHandler(Incoming.CatalogAdminMoveOfferEvent, CatalogAdminMoveOfferEvent.class);
        this.registerHandler(Incoming.CatalogAdminMovePageEvent, CatalogAdminMovePageEvent.class);
        this.registerHandler(Incoming.CatalogAdminPublishEvent, CatalogAdminPublishEvent.class);
        this.registerHandler(Incoming.CatalogAdminSavePageImagesEvent, CatalogAdminSavePageImagesEvent.class);
        this.registerHandler(Incoming.CatalogAdminSavePageIconEvent, CatalogAdminSavePageIconEvent.class);
    }

    private void registerEvent() throws Exception {
        this.registerHandler(Incoming.AdventCalendarOpenDayEvent, AdventCalendarOpenDayEvent.class);
        this.registerHandler(Incoming.AdventCalendarForceOpenEvent, AdventCalendarForceOpenEvent.class);
    }

    private void registerHandshake() throws Exception {
        this.registerHandler(Incoming.ReleaseVersionEvent, ReleaseVersionEvent.class);
        this.registerHandler(Incoming.InitDiffieHandshake, InitDiffieHandshakeEvent.class);
        this.registerHandler(Incoming.CompleteDiffieHandshake, CompleteDiffieHandshakeEvent.class);
        this.registerHandler(Incoming.SecureLoginEvent, SecureLoginEvent.class);
        this.registerHandler(Incoming.MachineIDEvent, MachineIDEvent.class);
        this.registerHandler(Incoming.UsernameEvent, UsernameEvent.class);
        this.registerHandler(Incoming.PingEvent, PingEvent.class);
    }

    private void registerFriends() throws Exception {
        this.registerHandler(Incoming.RequestFriendsEvent, RequestFriendsEvent.class);
        this.registerHandler(Incoming.ChangeRelationEvent, ChangeRelationEvent.class);
        this.registerHandler(Incoming.RemoveFriendEvent, RemoveFriendEvent.class);
        this.registerHandler(Incoming.SearchUserEvent, SearchUserEvent.class);
        this.registerHandler(Incoming.FriendRequestEvent, FriendRequestEvent.class);
        this.registerHandler(Incoming.AcceptFriendRequest, AcceptFriendRequestEvent.class);
        this.registerHandler(Incoming.DeclineFriendRequest, DeclineFriendRequestEvent.class);
        this.registerHandler(Incoming.FriendPrivateMessageEvent, FriendPrivateMessageEvent.class);
        this.registerHandler(Incoming.RequestFriendRequestEvent, RequestFriendRequestsEvent.class);
        this.registerHandler(Incoming.StalkFriendEvent, StalkFriendEvent.class);
        this.registerHandler(Incoming.RequestInitFriendsEvent, RequestInitFriendsEvent.class);
        this.registerHandler(Incoming.FindNewFriendsEvent, FindNewFriendsEvent.class);
        this.registerHandler(Incoming.InviteFriendsEvent, InviteFriendsEvent.class);
    }

    private void registerUsers() throws Exception {
        this.registerHandler(Incoming.RequestUserDataEvent, RequestUserDataEvent.class);
        this.registerHandler(Incoming.RequestUserCreditsEvent, RequestUserCreditsEvent.class);
        this.registerHandler(Incoming.RequestUserClubEvent, RequestUserClubEvent.class);
        this.registerHandler(Incoming.RequestMeMenuSettingsEvent, RequestMeMenuSettingsEvent.class);
        this.registerHandler(Incoming.RequestUserCitizinShipEvent, RequestUserCitizinShipEvent.class);
        this.registerHandler(Incoming.RequestUserProfileEvent, RequestUserProfileEvent.class);
        this.registerHandler(Incoming.RequestProfileFriendsEvent, RequestProfileFriendsEvent.class);
        this.registerHandler(Incoming.RequestUserWardrobeEvent, RequestUserWardrobeEvent.class);
        this.registerHandler(Incoming.SaveWardrobeEvent, SaveWardrobeEvent.class);
        this.registerHandler(Incoming.SaveMottoEvent, SaveMottoEvent.class);
        this.registerHandler(Incoming.UserSaveLookEvent, UserSaveLookEvent.class);
        this.registerHandler(Incoming.UserWearBadgeEvent, UserWearBadgeEvent.class);
        this.registerHandler(Incoming.RequestWearingBadgesEvent, RequestWearingBadgesEvent.class);
        this.registerHandler(Incoming.SaveUserVolumesEvent, SaveUserVolumesEvent.class);
        this.registerHandler(Incoming.SaveBlockCameraFollowEvent, SaveBlockCameraFollowEvent.class);
        this.registerHandler(Incoming.SaveIgnoreRoomInvitesEvent, SaveIgnoreRoomInvitesEvent.class);
        this.registerHandler(Incoming.SavePreferOldChatEvent, SavePreferOldChatEvent.class);
        this.registerHandler(Incoming.ActivateEffectEvent, ActivateEffectEvent.class);
        this.registerHandler(Incoming.EnableEffectEvent, EnableEffectEvent.class);
        this.registerHandler(Incoming.UserActivityEvent, UserActivityEvent.class);
        this.registerHandler(Incoming.UserNuxEvent, UserNuxEvent.class);
        this.registerHandler(Incoming.PickNewUserGiftEvent, PickNewUserGiftEvent.class);
        this.registerHandler(Incoming.ChangeNameCheckUsernameEvent, ChangeNameCheckUsernameEvent.class);
        this.registerHandler(Incoming.ConfirmChangeNameEvent, ConfirmChangeNameEvent.class);
        this.registerHandler(Incoming.ChangeChatBubbleEvent, ChangeChatBubbleEvent.class);
        this.registerHandler(Incoming.ChangeInfostandBgEvent, ChangeInfostandBgEvent.class);
        this.registerHandler(Incoming.UpdateUIFlagsEvent, UpdateUIFlagsEvent.class);
    }

    private void registerNavigator() throws Exception {
        this.registerHandler(Incoming.RequestRoomCategoriesEvent, RequestRoomCategoriesEvent.class);
        this.registerHandler(Incoming.RequestPopularRoomsEvent, RequestPopularRoomsEvent.class);
        this.registerHandler(Incoming.RequestHighestScoreRoomsEvent, RequestHighestScoreRoomsEvent.class);
        this.registerHandler(Incoming.RequestMyRoomsEvent, RequestMyRoomsEvent.class);
        this.registerHandler(Incoming.RequestCanCreateRoomEvent, RequestCanCreateRoomEvent.class);
        this.registerHandler(Incoming.RequestPromotedRoomsEvent, RequestPromotedRoomsEvent.class);
        this.registerHandler(Incoming.RequestCreateRoomEvent, RequestCreateRoomEvent.class);
        this.registerHandler(Incoming.RequestTagsEvent, RequestTagsEvent.class);
        this.registerHandler(Incoming.SearchRoomsByTagEvent, SearchRoomsByTagEvent.class);
        this.registerHandler(Incoming.SearchRoomsEvent, SearchRoomsEvent.class);
        this.registerHandler(Incoming.SearchRoomsFriendsNowEvent, SearchRoomsFriendsNowEvent.class);
        this.registerHandler(Incoming.SearchRoomsFriendsOwnEvent, SearchRoomsFriendsOwnEvent.class);
        this.registerHandler(Incoming.SearchRoomsWithRightsEvent, SearchRoomsWithRightsEvent.class);
        this.registerHandler(Incoming.SearchRoomsInGroupEvent, SearchRoomsInGroupEvent.class);
        this.registerHandler(Incoming.SearchRoomsMyFavoriteEvent, SearchRoomsMyFavouriteEvent.class);
        this.registerHandler(Incoming.SearchRoomsVisitedEvent, SearchRoomsVisitedEvent.class);
        this.registerHandler(Incoming.RequestNewNavigatorDataEvent, RequestNewNavigatorDataEvent.class);
        this.registerHandler(Incoming.RequestNewNavigatorRoomsEvent, RequestNewNavigatorRoomsEvent.class);
        this.registerHandler(Incoming.NewNavigatorActionEvent, NewNavigatorActionEvent.class);
        this.registerHandler(Incoming.RequestNavigatorSettingsEvent, RequestNavigatorSettingsEvent.class);
        this.registerHandler(Incoming.SaveWindowSettingsEvent, SaveWindowSettingsEvent.class);
        this.registerHandler(Incoming.RequestDeleteRoomEvent, RequestDeleteRoomEvent.class);
        this.registerHandler(Incoming.NavigatorCategoryListModeEvent, NavigatorCategoryListModeEvent.class);
        this.registerHandler(Incoming.NavigatorCollapseCategoryEvent, NavigatorCollapseCategoryEvent.class);
        this.registerHandler(Incoming.NavigatorUncollapseCategoryEvent, NavigatorUncollapseCategoryEvent.class);
        this.registerHandler(Incoming.AddSavedSearchEvent, AddSavedSearchEvent.class);
        this.registerHandler(Incoming.DeleteSavedSearchEvent, DeleteSavedSearchEvent.class);
    }

    private void registerHotelview() throws Exception {
        this.registerHandler(Incoming.HotelViewEvent, HotelViewEvent.class);
        this.registerHandler(Incoming.HotelViewRequestBonusRareEvent, HotelViewRequestBonusRareEvent.class);
        this.registerHandler(Incoming.RequestNewsListEvent, RequestNewsListEvent.class);
        this.registerHandler(Incoming.HotelViewDataEvent, HotelViewDataEvent.class);
        this.registerHandler(Incoming.HotelViewRequestBadgeRewardEvent, HotelViewRequestBadgeRewardEvent.class);
        this.registerHandler(Incoming.HotelViewClaimBadgeRewardEvent, HotelViewClaimBadgeRewardEvent.class);
        this.registerHandler(Incoming.HotelViewRequestLTDAvailabilityEvent, HotelViewRequestLTDAvailabilityEvent.class);
        this.registerHandler(Incoming.HotelViewRequestSecondsUntilEvent, HotelViewRequestSecondsUntilEvent.class);
    }

    private void registerInventory() throws Exception {
        this.registerHandler(Incoming.RequestInventoryBadgesEvent, RequestInventoryBadgesEvent.class);
        this.registerHandler(Incoming.RequestInventoryBotsEvent, RequestInventoryBotsEvent.class);
        this.registerHandler(Incoming.RequestInventoryItemsDelete, RequestInventoryItemsDelete.class);
        this.registerHandler(Incoming.RequestInventoryItemsEvent, RequestInventoryItemsEvent.class);
        this.registerHandler(Incoming.HotelViewInventoryEvent, RequestInventoryItemsEvent.class);
        this.registerHandler(Incoming.RequestInventoryPetsEvent, RequestInventoryPetsEvent.class);
        this.registerHandler(Incoming.RequestInventoryPetDelete, RequestInventoryPetDelete.class);
        this.registerHandler(Incoming.RequestInventoryBadgeDelete, RequestInventoryBadgeDelete.class);

        // Custom Prefixes
        this.registerHandler(Incoming.RequestUserPrefixesEvent, RequestUserPrefixesEvent.class);
        this.registerHandler(Incoming.SetActivePrefixEvent, SetActivePrefixEvent.class);
        this.registerHandler(Incoming.DeletePrefixEvent, DeletePrefixEvent.class);
        this.registerHandler(Incoming.PurchasePrefixEvent, PurchasePrefixEvent.class);
        this.registerHandler(Incoming.PurchaseCatalogPrefixEvent, PurchaseCatalogPrefixEvent.class);
        this.registerHandler(Incoming.SetDisplayOrderEvent, SetDisplayOrderEvent.class);

        // Nick Icons
        this.registerHandler(Incoming.RequestUserNickIconsEvent, RequestUserNickIconsEvent.class);
        this.registerHandler(Incoming.PurchaseNickIconEvent, PurchaseNickIconEvent.class);
        this.registerHandler(Incoming.SetActiveNickIconEvent, SetActiveNickIconEvent.class);
    }

    void registerRooms() throws Exception {
        this.registerHandler(Incoming.RequestMentionsEvent, RequestMentionsEvent.class);
        this.registerHandler(Incoming.MarkMentionsReadEvent, MarkMentionsReadEvent.class);
        this.registerHandler(Incoming.DeleteMentionEvent, DeleteMentionEvent.class);
        this.registerHandler(Incoming.RequestRoomLoadEvent, RequestRoomLoadEvent.class);
        this.registerHandler(Incoming.RequestHeightmapEvent, RequestRoomHeightmapEvent.class);
        this.registerHandler(Incoming.RequestRoomHeightmapEvent, RequestRoomHeightmapEvent.class);
        this.registerHandler(Incoming.RoomVoteEvent, RoomVoteEvent.class);
        this.registerHandler(Incoming.RequestRoomDataEvent, RequestRoomDataEvent.class);
        this.registerHandler(Incoming.RoomSettingsSaveEvent, RoomSettingsSaveEvent.class);
        this.registerHandler(Incoming.RoomPlaceItemEvent, RoomPlaceItemEvent.class);
        this.registerHandler(Incoming.RotateMoveItemEvent, RotateMoveItemEvent.class);
        this.registerHandler(Incoming.MoveWallItemEvent, MoveWallItemEvent.class);
        this.registerHandler(Incoming.RoomPickupItemEvent, RoomPickupItemEvent.class);
		this.registerHandler(Incoming.RoomPickupChooserEvent, RoomPickupChooserEvent.class);
        this.registerHandler(Incoming.RoomPlacePaintEvent, RoomPlacePaintEvent.class);
        this.registerHandler(Incoming.RoomUserStartTypingEvent, RoomUserStartTypingEvent.class);
        this.registerHandler(Incoming.RoomUserStopTypingEvent, RoomUserStopTypingEvent.class);
        this.registerHandler(Incoming.ClickFurniEvent, ClickFurniEvent.class);
        this.registerHandler(Incoming.ClickUserEvent, ClickUserEvent.class);
        this.registerHandler(Incoming.ToggleFloorItemEvent, ToggleFloorItemEvent.class);
        this.registerHandler(Incoming.ToggleWallItemEvent, ToggleWallItemEvent.class);
        this.registerHandler(Incoming.RoomBackgroundEvent, RoomBackgroundEvent.class);
        this.registerHandler(Incoming.MannequinSaveNameEvent, MannequinSaveNameEvent.class);
        this.registerHandler(Incoming.MannequinSaveLookEvent, MannequinSaveLookEvent.class);
        this.registerHandler(Incoming.FootballGateSaveLookEvent, FootballGateSaveLookEvent.class);
        this.registerHandler(Incoming.AdvertisingSaveEvent, AdvertisingSaveEvent.class);
        this.registerHandler(Incoming.RequestRoomSettingsEvent, RequestRoomSettingsEvent.class);
        this.registerHandler(Incoming.MoodLightSettingsEvent, MoodLightSettingsEvent.class);
        this.registerHandler(Incoming.MoodLightTurnOnEvent, MoodLightTurnOnEvent.class);
        this.registerHandler(Incoming.RoomUserDropHandItemEvent, RoomUserDropHandItemEvent.class);
        this.registerHandler(Incoming.RoomUserLookAtPoint, RoomUserLookAtPoint.class);
        this.registerHandler(Incoming.RoomUserTalkEvent, RoomUserTalkEvent.class);
        this.registerHandler(Incoming.RoomUserShoutEvent, RoomUserShoutEvent.class);
        this.registerHandler(Incoming.RoomUserWhisperEvent, RoomUserWhisperEvent.class);
        this.registerHandler(Incoming.RoomUserActionEvent, RoomUserActionEvent.class);
        this.registerHandler(Incoming.RoomUserSitEvent, RoomUserSitEvent.class);
        this.registerHandler(Incoming.RoomUserDanceEvent, RoomUserDanceEvent.class);
        this.registerHandler(Incoming.RoomUserSignEvent, RoomUserSignEvent.class);
        this.registerHandler(Incoming.RoomUserWalkEvent, RoomUserWalkEvent.class);
        this.registerHandler(Incoming.RoomUserGiveRespectEvent, RoomUserGiveRespectEvent.class);
        this.registerHandler(Incoming.RoomUserGiveRightsEvent, RoomUserGiveRightsEvent.class);
        this.registerHandler(Incoming.RoomRemoveRightsEvent, RoomRemoveRightsEvent.class);
        this.registerHandler(Incoming.RequestRoomRightsEvent, RequestRoomRightsEvent.class);
        this.registerHandler(Incoming.RoomRemoveAllRightsEvent, RoomRemoveAllRightsEvent.class);
        this.registerHandler(Incoming.RoomUserRemoveRightsEvent, RoomUserRemoveRightsEvent.class);
        this.registerHandler(Incoming.BotPlaceEvent, BotPlaceEvent.class);
        this.registerHandler(Incoming.BotPickupEvent, BotPickupEvent.class);
        this.registerHandler(Incoming.BotSaveSettingsEvent, BotSaveSettingsEvent.class);
        this.registerHandler(Incoming.BotSettingsEvent, BotSettingsEvent.class);
        this.registerHandler(Incoming.TriggerDiceEvent, TriggerDiceEvent.class);
        this.registerHandler(Incoming.CloseDiceEvent, CloseDiceEvent.class);
        this.registerHandler(Incoming.TriggerColorWheelEvent, TriggerColorWheelEvent.class);
        this.registerHandler(Incoming.RedeemItemEvent, RedeemItemEvent.class);
        this.registerHandler(Incoming.PetPlaceEvent, PetPlaceEvent.class);
        this.registerHandler(Incoming.RoomUserKickEvent, RoomUserKickEvent.class);
        this.registerHandler(Incoming.SetStackHelperHeightEvent, SetStackHelperHeightEvent.class);
        this.registerHandler(Incoming.TriggerOneWayGateEvent, TriggerOneWayGateEvent.class);
        this.registerHandler(Incoming.HandleDoorbellEvent, HandleDoorbellEvent.class);
        this.registerHandler(Incoming.RedeemClothingEvent, RedeemClothingEvent.class);
        this.registerHandler(Incoming.PostItPlaceEvent, PostItPlaceEvent.class);
        this.registerHandler(Incoming.PostItRequestDataEvent, PostItRequestDataEvent.class);
        this.registerHandler(Incoming.PostItSaveDataEvent, PostItSaveDataEvent.class);
        this.registerHandler(Incoming.PostItDeleteEvent, PostItDeleteEvent.class);
        this.registerHandler(Incoming.MoodLightSaveSettingsEvent, MoodLightSaveSettingsEvent.class);
        this.registerHandler(Incoming.RentSpaceEvent, RentSpaceEvent.class);
        this.registerHandler(Incoming.RentSpaceCancelEvent, RentSpaceCancelEvent.class);
        this.registerHandler(Incoming.SetHomeRoomEvent, SetHomeRoomEvent.class);
        this.registerHandler(Incoming.RoomUserGiveHandItemEvent, RoomUserGiveHandItemEvent.class);
        this.registerHandler(Incoming.RoomMuteEvent, RoomMuteEvent.class);
        this.registerHandler(Incoming.RequestRoomWordFilterEvent, RequestRoomWordFilterEvent.class);
        this.registerHandler(Incoming.RoomWordFilterModifyEvent, RoomWordFilterModifyEvent.class);
        this.registerHandler(Incoming.RoomStaffPickEvent, RoomStaffPickEvent.class);
        this.registerHandler(Incoming.RoomRequestBannedUsersEvent, RoomRequestBannedUsersEvent.class);
        this.registerHandler(Incoming.JukeBoxRequestTrackCodeEvent, JukeBoxRequestTrackCodeEvent.class);
        this.registerHandler(Incoming.JukeBoxRequestTrackDataEvent, JukeBoxRequestTrackDataEvent.class);
        this.registerHandler(Incoming.JukeBoxAddSoundTrackEvent, JukeBoxAddSoundTrackEvent.class);
        this.registerHandler(Incoming.JukeBoxRemoveSoundTrackEvent, JukeBoxRemoveSoundTrackEvent.class);
        this.registerHandler(Incoming.JukeBoxRequestPlayListEvent, JukeBoxRequestPlayListEvent.class);
        this.registerHandler(Incoming.JukeBoxEventOne, JukeBoxEventOne.class);
        this.registerHandler(Incoming.JukeBoxEventTwo, JukeBoxEventTwo.class);
        this.registerHandler(Incoming.SavePostItStickyPoleEvent, SavePostItStickyPoleEvent.class);
        this.registerHandler(Incoming.RequestPromotionRoomsEvent, RequestPromotionRoomsEvent.class);
        this.registerHandler(Incoming.BuyRoomPromotionEvent, BuyRoomPromotionEvent.class);
        this.registerHandler(Incoming.EditRoomPromotionMessageEvent, UpdateRoomPromotionEvent.class);
        this.registerHandler(Incoming.IgnoreRoomUserEvent, IgnoreRoomUserEvent.class);
        this.registerHandler(Incoming.UnIgnoreRoomUserEvent, UnIgnoreRoomUserEvent.class);
        this.registerHandler(Incoming.RoomUserMuteEvent, RoomUserMuteEvent.class);
        this.registerHandler(Incoming.RoomUserBanEvent, RoomUserBanEvent.class);
        this.registerHandler(Incoming.UnbanRoomUserEvent, UnbanRoomUserEvent.class);
        this.registerHandler(Incoming.RequestRoomUserTagsEvent, RequestRoomUserTagsEvent.class);
        this.registerHandler(Incoming.YoutubeRequestPlaylists, YoutubeRequestPlaylists.class);
        this.registerHandler(Incoming.YoutubeRequestStateChange, YoutubeRequestStateChange.class);
        this.registerHandler(Incoming.YoutubeRequestPlaylistChange, YoutubeRequestPlaylistChange.class);
        this.registerHandler(Incoming.RoomFavoriteEvent, RoomFavoriteEvent.class);
        this.registerHandler(Incoming.LoveLockStartConfirmEvent, LoveLockStartConfirmEvent.class);
        this.registerHandler(Incoming.RoomUnFavoriteEvent, RoomUnFavoriteEvent.class);
        this.registerHandler(Incoming.UseRandomStateItemEvent, UseRandomStateItemEvent.class);
        this.registerHandler(Incoming.UpdateFurniturePositionEvent, UpdateFurniturePositionEvent.class);
    }

    void registerPolls() throws Exception {
        this.registerHandler(Incoming.CancelPollEvent, CancelPollEvent.class);
        this.registerHandler(Incoming.GetPollDataEvent, GetPollDataEvent.class);
        this.registerHandler(Incoming.AnswerPollEvent, AnswerPollEvent.class);
    }

    void registerModTool() throws Exception {
        this.registerHandler(Incoming.ModToolRequestRoomInfoEvent, ModToolRequestRoomInfoEvent.class);
        this.registerHandler(Incoming.ModToolRequestRoomChatlogEvent, ModToolRequestRoomChatlogEvent.class);
        this.registerHandler(Incoming.ModToolRequestUserInfoEvent, ModToolRequestUserInfoEvent.class);
        this.registerHandler(Incoming.ModToolPickTicketEvent, ModToolPickTicketEvent.class);
        this.registerHandler(Incoming.ModToolCloseTicketEvent, ModToolCloseTicketEvent.class);
        this.registerHandler(Incoming.ModToolReleaseTicketEvent, ModToolReleaseTicketEvent.class);
        this.registerHandler(Incoming.ModToolAlertEvent, ModToolAlertEvent.class);
        this.registerHandler(Incoming.ModToolWarnEvent, ModToolWarnEvent.class);
        this.registerHandler(Incoming.ModToolKickEvent, ModToolKickEvent.class);
        this.registerHandler(Incoming.ModToolRoomAlertEvent, ModToolRoomAlertEvent.class);
        this.registerHandler(Incoming.ModToolChangeRoomSettingsEvent, ModToolChangeRoomSettingsEvent.class);
        this.registerHandler(Incoming.ModToolRequestRoomVisitsEvent, ModToolRequestRoomVisitsEvent.class);
        this.registerHandler(Incoming.ModToolRequestIssueChatlogEvent, ModToolRequestIssueChatlogEvent.class);
        this.registerHandler(Incoming.ModToolRequestRoomUserChatlogEvent, ModToolRequestRoomUserChatlogEvent.class);
        this.registerHandler(Incoming.ModToolRequestUserChatlogEvent, ModToolRequestUserChatlogEvent.class);
        this.registerHandler(Incoming.ModToolSanctionAlertEvent, ModToolSanctionAlertEvent.class);
        this.registerHandler(Incoming.ModToolSanctionMuteEvent, ModToolSanctionMuteEvent.class);
        this.registerHandler(Incoming.ModToolSanctionBanEvent, ModToolSanctionBanEvent.class);
        this.registerHandler(Incoming.ModToolSanctionTradeLockEvent, ModToolSanctionTradeLockEvent.class);
        this.registerHandler(Incoming.ModToolIssueChangeTopicEvent, ModToolIssueChangeTopicEvent.class);
        this.registerHandler(Incoming.ModToolIssueDefaultSanctionEvent, ModToolIssueDefaultSanctionEvent.class);

        this.registerHandler(Incoming.RequestReportRoomEvent, RequestReportRoomEvent.class);
        this.registerHandler(Incoming.RequestReportUserBullyingEvent, RequestReportUserBullyingEvent.class);
        this.registerHandler(Incoming.ReportBullyEvent, ReportBullyEvent.class);
        this.registerHandler(Incoming.ReportEvent, ReportEvent.class);
        this.registerHandler(Incoming.ReportFriendPrivateChatEvent, ReportFriendPrivateChatEvent.class);
        this.registerHandler(Incoming.ReportThreadEvent, ReportThreadEvent.class);
        this.registerHandler(Incoming.ReportCommentEvent, ReportCommentEvent.class);
        this.registerHandler(Incoming.ReportPhotoEvent, ReportPhotoEvent.class);
    }

    void registerTrading() throws Exception {
        this.registerHandler(Incoming.TradeStartEvent, TradeStartEvent.class);
        this.registerHandler(Incoming.TradeOfferItemEvent, TradeOfferItemEvent.class);
        this.registerHandler(Incoming.TradeOfferMultipleItemsEvent, TradeOfferMultipleItemsEvent.class);
        this.registerHandler(Incoming.TradeCancelOfferItemEvent, TradeCancelOfferItemEvent.class);
        this.registerHandler(Incoming.TradeAcceptEvent, TradeAcceptEvent.class);
        this.registerHandler(Incoming.TradeUnAcceptEvent, TradeUnAcceptEvent.class);
        this.registerHandler(Incoming.TradeConfirmEvent, TradeConfirmEvent.class);
        this.registerHandler(Incoming.TradeCloseEvent, TradeCloseEvent.class);
        this.registerHandler(Incoming.TradeCancelEvent, TradeCancelEvent.class);
    }

    void registerGuilds() throws Exception {
        this.registerHandler(Incoming.RequestGuildBuyRoomsEvent, RequestGuildBuyRoomsEvent.class);
        this.registerHandler(Incoming.RequestGuildPartsEvent, RequestGuildPartsEvent.class);
        this.registerHandler(Incoming.RequestGuildBuyEvent, RequestGuildBuyEvent.class);
        this.registerHandler(Incoming.RequestGuildInfoEvent, RequestGuildInfoEvent.class);
        this.registerHandler(Incoming.RequestGuildManageEvent, RequestGuildManageEvent.class);
        this.registerHandler(Incoming.RequestGuildMembersEvent, RequestGuildMembersEvent.class);
        this.registerHandler(Incoming.RequestGuildJoinEvent, RequestGuildJoinEvent.class);
        this.registerHandler(Incoming.GuildChangeNameDescEvent, GuildChangeNameDescEvent.class);
        this.registerHandler(Incoming.GuildChangeBadgeEvent, GuildChangeBadgeEvent.class);
        this.registerHandler(Incoming.GuildChangeColorsEvent, GuildChangeColorsEvent.class);
        this.registerHandler(Incoming.GuildRemoveAdminEvent, GuildRemoveAdminEvent.class);
        this.registerHandler(Incoming.GuildRemoveMemberEvent, GuildRemoveMemberEvent.class);
        this.registerHandler(Incoming.GuildChangeSettingsEvent, GuildChangeSettingsEvent.class);
        this.registerHandler(Incoming.GuildAcceptMembershipEvent, GuildAcceptMembershipEvent.class);
        this.registerHandler(Incoming.GuildDeclineMembershipEvent, GuildDeclineMembershipEvent.class);
        this.registerHandler(Incoming.GuildSetAdminEvent, GuildSetAdminEvent.class);
        this.registerHandler(Incoming.GuildSetFavoriteEvent, GuildSetFavoriteEvent.class);
        this.registerHandler(Incoming.RequestOwnGuildsEvent, RequestOwnGuildsEvent.class);
        this.registerHandler(Incoming.RequestGuildFurniWidgetEvent, RequestGuildFurniWidgetEvent.class);
        this.registerHandler(Incoming.GuildConfirmRemoveMemberEvent, GuildConfirmRemoveMemberEvent.class);
        this.registerHandler(Incoming.GuildRemoveFavoriteEvent, GuildRemoveFavoriteEvent.class);
        this.registerHandler(Incoming.GuildDeleteEvent, GuildDeleteEvent.class);
        this.registerHandler(Incoming.GuildForumListEvent, GuildForumListEvent.class);
        this.registerHandler(Incoming.GuildForumThreadsEvent, GuildForumThreadsEvent.class);
        this.registerHandler(Incoming.GuildForumDataEvent, GuildForumDataEvent.class);
        this.registerHandler(Incoming.GuildForumPostThreadEvent, GuildForumPostThreadEvent.class);
        this.registerHandler(Incoming.GuildForumUpdateSettingsEvent, GuildForumUpdateSettingsEvent.class);
        this.registerHandler(Incoming.GuildForumThreadsMessagesEvent, GuildForumThreadsMessagesEvent.class);
        this.registerHandler(Incoming.GuildForumModerateMessageEvent, GuildForumModerateMessageEvent.class);
        this.registerHandler(Incoming.GuildForumModerateThreadEvent, GuildForumModerateThreadEvent.class);
        this.registerHandler(Incoming.GuildForumThreadUpdateEvent, GuildForumThreadUpdateEvent.class);
        this.registerHandler(Incoming.GuildForumMarkAsReadEvent, GuildForumMarkAsReadEvent.class);
        this.registerHandler(Incoming.GetHabboGuildBadgesMessageEvent, GetHabboGuildBadgesMessageEvent.class);

//        this.registerHandler(Incoming.GuildForumDataEvent,              GuildForumModerateMessageEvent.class);
//        this.registerHandler(Incoming.GuildForumDataEvent,              GuildForumModerateThreadEvent.class);
//        this.registerHandler(Incoming.GuildForumDataEvent,              GuildForumPostThreadEvent.class);
//        this.registerHandler(Incoming.GuildForumDataEvent,              GuildForumThreadsEvent.class);
//        this.registerHandler(Incoming.GuildForumDataEvent,              GuildForumThreadsMessagesEvent.class);
//        this.registerHandler(Incoming.GuildForumDataEvent,              GuildForumUpdateSettingsEvent.class);
    }

    void registerPets() throws Exception {
        this.registerHandler(Incoming.RequestPetInformationEvent, RequestPetInformationEvent.class);
        this.registerHandler(Incoming.PetPickupEvent, PetPickupEvent.class);
        this.registerHandler(Incoming.ScratchPetEvent, ScratchPetEvent.class);
        this.registerHandler(Incoming.RequestPetTrainingPanelEvent, RequestPetTrainingPanelEvent.class);
        this.registerHandler(Incoming.PetUseItemEvent, PetUseItemEvent.class);
        this.registerHandler(Incoming.HorseRideSettingsEvent, PetRideSettingsEvent.class);
        this.registerHandler(Incoming.HorseRideEvent, PetRideEvent.class);
        this.registerHandler(Incoming.HorseRemoveSaddleEvent, HorseRemoveSaddleEvent.class);
        this.registerHandler(Incoming.ToggleMonsterplantBreedableEvent, ToggleMonsterplantBreedableEvent.class);
        this.registerHandler(Incoming.CompostMonsterplantEvent, CompostMonsterplantEvent.class);
        this.registerHandler(Incoming.BreedMonsterplantsEvent, BreedMonsterplantsEvent.class);
        this.registerHandler(Incoming.MovePetEvent, MovePetEvent.class);
        this.registerHandler(Incoming.PetPackageNameEvent, PetPackageNameEvent.class);
        this.registerHandler(Incoming.StopBreedingEvent, StopBreedingEvent.class);
        this.registerHandler(Incoming.ConfirmPetBreedingEvent, ConfirmPetBreedingEvent.class);
    }

    void registerWired() throws Exception {
        this.registerHandler(Incoming.WiredTriggerSaveDataEvent, WiredTriggerSaveDataEvent.class);
        this.registerHandler(Incoming.WiredEffectSaveDataEvent, WiredEffectSaveDataEvent.class);
        this.registerHandler(Incoming.WiredConditionSaveDataEvent, WiredConditionSaveDataEvent.class);
        this.registerHandler(Incoming.WiredApplySetConditionsEvent, WiredApplySetConditionsEvent.class);
        this.registerHandler(Incoming.WiredMonitorRequestEvent, WiredMonitorRequestEvent.class);
        this.registerHandler(Incoming.WiredRoomSettingsRequestEvent, WiredRoomSettingsRequestEvent.class);
        this.registerHandler(Incoming.WiredRoomSettingsSaveEvent, WiredRoomSettingsSaveEvent.class);
        this.registerHandler(Incoming.WiredUserVariablesRequestEvent, WiredUserVariablesRequestEvent.class);
        this.registerHandler(Incoming.WiredUserVariableUpdateEvent, WiredUserVariableUpdateEvent.class);
        this.registerHandler(Incoming.WiredUserVariableManageEvent, WiredUserVariableManageEvent.class);
        this.registerHandler(Incoming.WiredUserInspectMoveEvent, WiredUserInspectMoveEvent.class);
    }

    void registerTranslation() throws Exception {
        this.registerHandler(Incoming.TranslationLanguagesRequestEvent, TranslationLanguagesRequestEvent.class);
        this.registerHandler(Incoming.TranslationTextRequestEvent, TranslationTextRequestEvent.class);
    }

    void registerUnknown() throws Exception {
        this.registerHandler(Incoming.RequestResolutionEvent, RequestResolutionEvent.class);
        this.registerHandler(Incoming.RequestTalenTrackEvent, RequestTalentTrackEvent.class);
        this.registerHandler(Incoming.UnknownEvent1, UnknownEvent1.class);
        this.registerHandler(Incoming.MySanctionStatusEvent, MySanctionStatusEvent.class);
    }

    void registerFloorPlanEditor() throws Exception {
        this.registerHandler(Incoming.FloorPlanEditorSaveEvent, FloorPlanEditorSaveEvent.class);
        this.registerHandler(Incoming.FloorPlanEditorRequestBlockedTilesEvent, FloorPlanEditorRequestBlockedTilesEvent.class);
        this.registerHandler(Incoming.FloorPlanEditorRequestDoorSettingsEvent, FloorPlanEditorRequestDoorSettingsEvent.class);
    }

    void registerAchievements() throws Exception {
        this.registerHandler(Incoming.RequestAchievementsEvent, RequestAchievementsEvent.class);
        this.registerHandler(Incoming.RequestAchievementConfigurationEvent, RequestAchievementConfigurationEvent.class);
    }

    void registerGuides() throws Exception {
        this.registerHandler(Incoming.RequestGuideToolEvent, RequestGuideToolEvent.class);
        this.registerHandler(Incoming.RequestGuideAssistanceEvent, RequestGuideAssistanceEvent.class);
        this.registerHandler(Incoming.GuideUserTypingEvent, GuideUserTypingEvent.class);
        this.registerHandler(Incoming.GuideReportHelperEvent, GuideReportHelperEvent.class);
        this.registerHandler(Incoming.GuideRecommendHelperEvent, GuideRecommendHelperEvent.class);
        this.registerHandler(Incoming.GuideUserMessageEvent, GuideUserMessageEvent.class);
        this.registerHandler(Incoming.GuideCancelHelpRequestEvent, GuideCancelHelpRequestEvent.class);
        this.registerHandler(Incoming.GuideHandleHelpRequestEvent, GuideHandleHelpRequestEvent.class);
        this.registerHandler(Incoming.GuideInviteUserEvent, GuideInviteUserEvent.class);
        this.registerHandler(Incoming.GuideVisitUserEvent, GuideVisitUserEvent.class);
        this.registerHandler(Incoming.GuideCloseHelpRequestEvent, GuideCloseHelpRequestEvent.class);

        this.registerHandler(Incoming.GuardianNoUpdatesWantedEvent, GuardianNoUpdatesWantedEvent.class);
        this.registerHandler(Incoming.GuardianAcceptRequestEvent, GuardianAcceptRequestEvent.class);
        this.registerHandler(Incoming.GuardianVoteEvent, GuardianVoteEvent.class);
    }

    void registerCrafting() throws Exception {
        this.registerHandler(Incoming.RequestCraftingRecipesEvent, RequestCraftingRecipesEvent.class);
        this.registerHandler(Incoming.CraftingAddRecipeEvent, CraftingAddRecipeEvent.class);
        this.registerHandler(Incoming.CraftingCraftItemEvent, CraftingCraftItemEvent.class);
        this.registerHandler(Incoming.CraftingCraftSecretEvent, CraftingCraftSecretEvent.class);
        this.registerHandler(Incoming.RequestCraftingRecipesAvailableEvent, RequestCraftingRecipesAvailableEvent.class);
    }

    void registerCamera() throws Exception {
        this.registerHandler(Incoming.CameraRoomPictureEvent, CameraRoomPictureEvent.class);
        this.registerHandler(Incoming.RequestCameraConfigurationEvent, RequestCameraConfigurationEvent.class);
        this.registerHandler(Incoming.CameraPurchaseEvent, CameraPurchaseEvent.class);
        this.registerHandler(Incoming.CameraRoomThumbnailEvent, CameraRoomThumbnailEvent.class);
        this.registerHandler(Incoming.CameraPublishToWebEvent, CameraPublishToWebEvent.class);
    }

    void registerGameCenter() throws Exception {
        this.registerHandler(Incoming.GameCenterRequestGamesEvent, GameCenterRequestGamesEvent.class);
        this.registerHandler(Incoming.GameCenterRequestAccountStatusEvent, GameCenterRequestAccountStatusEvent.class);
        this.registerHandler(Incoming.GameCenterJoinGameEvent, GameCenterJoinGameEvent.class);
        this.registerHandler(Incoming.GameCenterLoadGameEvent, GameCenterLoadGameEvent.class);
        this.registerHandler(Incoming.GameCenterLeaveGameEvent, GameCenterLeaveGameEvent.class);
        this.registerHandler(Incoming.GameCenterEvent, GameCenterEvent.class);
        this.registerHandler(Incoming.GameCenterRequestGameStatusEvent, GameCenterRequestGameStatusEvent.class);

        // YouTube Room Broadcast
        this.registerHandler(Incoming.YouTubeRoomPlayEvent, com.eu.habbo.messages.incoming.rooms.youtube.YouTubeRoomPlayEvent.class);
        this.registerHandler(Incoming.YouTubeRoomWatchingEvent, com.eu.habbo.messages.incoming.rooms.youtube.YouTubeRoomWatchingEvent.class);
        this.registerHandler(Incoming.YouTubeRoomSettingsEvent, com.eu.habbo.messages.incoming.rooms.youtube.YouTubeRoomSettingsEvent.class);

        // Housekeeping (in-client admin panel)
        this.registerHandler(Incoming.HousekeepingFindUserByNameEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingFindUserByNameEvent.class);
        this.registerHandler(Incoming.HousekeepingFindUserByIdEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingFindUserByIdEvent.class);
        this.registerHandler(Incoming.HousekeepingBanUserEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingBanUserEvent.class);
        this.registerHandler(Incoming.HousekeepingUnbanUserEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingUnbanUserEvent.class);
        this.registerHandler(Incoming.HousekeepingMuteUserEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingMuteUserEvent.class);
        this.registerHandler(Incoming.HousekeepingKickUserEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingKickUserEvent.class);
        this.registerHandler(Incoming.HousekeepingForceDisconnectUserEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingForceDisconnectUserEvent.class);
        this.registerHandler(Incoming.HousekeepingSetUserRankEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingSetUserRankEvent.class);
        this.registerHandler(Incoming.HousekeepingTradeLockUserEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingTradeLockUserEvent.class);
        this.registerHandler(Incoming.HousekeepingResetUserPasswordEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingResetUserPasswordEvent.class);
        this.registerHandler(Incoming.HousekeepingFindRoomByIdEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingFindRoomByIdEvent.class);
        this.registerHandler(Incoming.HousekeepingSearchRoomsEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingSearchRoomsEvent.class);
        this.registerHandler(Incoming.HousekeepingRoomStateEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingRoomStateEvent.class);
        this.registerHandler(Incoming.HousekeepingMuteRoomEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingMuteRoomEvent.class);
        this.registerHandler(Incoming.HousekeepingKickAllFromRoomEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingKickAllFromRoomEvent.class);
        this.registerHandler(Incoming.HousekeepingTransferRoomOwnershipEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingTransferRoomOwnershipEvent.class);
        this.registerHandler(Incoming.HousekeepingDeleteRoomEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingDeleteRoomEvent.class);
        this.registerHandler(Incoming.HousekeepingGiveCreditsEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingGiveCreditsEvent.class);
        this.registerHandler(Incoming.HousekeepingGiveCurrencyEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingGiveCurrencyEvent.class);
        this.registerHandler(Incoming.HousekeepingGrantItemEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingGrantItemEvent.class);
        this.registerHandler(Incoming.HousekeepingSetHcSubscriptionEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingSetHcSubscriptionEvent.class);
        this.registerHandler(Incoming.HousekeepingSendHotelAlertEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingSendHotelAlertEvent.class);
        this.registerHandler(Incoming.HousekeepingGetDashboardEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingGetDashboardEvent.class);
        this.registerHandler(Incoming.HousekeepingListActionLogEvent, com.eu.habbo.messages.incoming.housekeeping.HousekeepingListActionLogEvent.class);

        this.registerHandler(Incoming.RequestRareValuesEvent, com.eu.habbo.messages.incoming.rarevalues.RequestRareValuesEvent.class);

        this.registerHandler(Incoming.WheelOpenEvent, com.eu.habbo.messages.incoming.wheel.WheelOpenEvent.class);
        this.registerHandler(Incoming.WheelSpinEvent, com.eu.habbo.messages.incoming.wheel.WheelSpinEvent.class);
        this.registerHandler(Incoming.WheelBuySpinEvent, com.eu.habbo.messages.incoming.wheel.WheelBuySpinEvent.class);
        this.registerHandler(Incoming.WheelAdminGetPrizesEvent, com.eu.habbo.messages.incoming.wheel.WheelAdminGetPrizesEvent.class);
        this.registerHandler(Incoming.WheelAdminSavePrizesEvent, com.eu.habbo.messages.incoming.wheel.WheelAdminSavePrizesEvent.class);

        this.registerHandler(Incoming.SoundboardPlayEvent, com.eu.habbo.messages.incoming.soundboard.SoundboardPlayEvent.class);
        this.registerHandler(Incoming.SoundboardSetEnabledEvent, com.eu.habbo.messages.incoming.soundboard.SoundboardSetEnabledEvent.class);
    }
}
