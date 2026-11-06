package polaris.messages;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.messages.incoming.Incoming;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.UnsupportedIncoming;
import polaris.messages.incoming.achievements.RequestAchievementConfigurationEvent;
import polaris.messages.incoming.achievements.RequestAchievementsEvent;
import polaris.messages.incoming.ambassadors.AmbassadorAlertCommandEvent;
import polaris.messages.incoming.ambassadors.AmbassadorVisitCommandEvent;
import polaris.messages.incoming.camera.CameraPublishToWebEvent;
import polaris.messages.incoming.camera.CameraPurchaseEvent;
import polaris.messages.incoming.camera.CameraRoomPictureEvent;
import polaris.messages.incoming.camera.CameraRoomThumbnailEvent;
import polaris.messages.incoming.camera.PhotoCompetitionEvent;
import polaris.messages.incoming.camera.RequestCameraConfigurationEvent;
import polaris.messages.incoming.catalog.BuildersClubPlaceRoomItemEvent;
import polaris.messages.incoming.catalog.BuildersClubPlaceWallItemEvent;
import polaris.messages.incoming.catalog.BuildersClubQueryFurniCountEvent;
import polaris.messages.incoming.catalog.CatalogBuyClubDiscountEvent;
import polaris.messages.incoming.catalog.CatalogBuyItemAsGiftEvent;
import polaris.messages.incoming.catalog.CatalogBuyItemEvent;
import polaris.messages.incoming.catalog.CatalogProductMetadataEvent;
import polaris.messages.incoming.catalog.CatalogRequestClubDiscountEvent;
import polaris.messages.incoming.catalog.CatalogRuntimeConfigurationEvent;
import polaris.messages.incoming.catalog.CatalogSearchedItemEvent;
import polaris.messages.incoming.catalog.CatalogSelectClubGiftEvent;
import polaris.messages.incoming.catalog.CheckPetNameEvent;
import polaris.messages.incoming.catalog.GetSeasonalCalendarDailyOfferEvent;
import polaris.messages.incoming.catalog.JukeBoxRequestTrackCodeEvent;
import polaris.messages.incoming.catalog.JukeBoxRequestTrackDataEvent;
import polaris.messages.incoming.catalog.MarkCatalogNewAdditionsPageOpenedEvent;
import polaris.messages.incoming.catalog.PurchaseTargetOfferEvent;
import polaris.messages.incoming.catalog.RedeemVoucherEvent;
import polaris.messages.incoming.catalog.RequestCatalogModeEvent;
import polaris.messages.incoming.catalog.RequestCatalogPageEvent;
import polaris.messages.incoming.catalog.RequestClubDataEvent;
import polaris.messages.incoming.catalog.RequestClubExtendConfirmEvent;
import polaris.messages.incoming.catalog.RequestClubGiftsEvent;
import polaris.messages.incoming.catalog.RequestDiscountEvent;
import polaris.messages.incoming.catalog.RequestGiftConfigurationEvent;
import polaris.messages.incoming.catalog.RequestMarketplaceConfigEvent;
import polaris.messages.incoming.catalog.RequestPetBreedsEvent;
import polaris.messages.incoming.catalog.RequestTargetOfferEvent;
import polaris.messages.incoming.catalog.TargetOfferStateEvent;
import polaris.messages.incoming.catalog.catalogadmin.CatalogAdminCreateOfferEvent;
import polaris.messages.incoming.catalog.catalogadmin.CatalogAdminCreatePageEvent;
import polaris.messages.incoming.catalog.catalogadmin.CatalogAdminDeleteOfferEvent;
import polaris.messages.incoming.catalog.catalogadmin.CatalogAdminDeletePageEvent;
import polaris.messages.incoming.catalog.catalogadmin.CatalogAdminLoadOfferEvent;
import polaris.messages.incoming.catalog.catalogadmin.CatalogAdminLoadPageEvent;
import polaris.messages.incoming.catalog.catalogadmin.CatalogAdminMoveOfferEvent;
import polaris.messages.incoming.catalog.catalogadmin.CatalogAdminMovePageEvent;
import polaris.messages.incoming.catalog.catalogadmin.CatalogAdminPublishEvent;
import polaris.messages.incoming.catalog.catalogadmin.CatalogAdminReorderOffersEvent;
import polaris.messages.incoming.catalog.catalogadmin.CatalogAdminSaveOfferEvent;
import polaris.messages.incoming.catalog.catalogadmin.CatalogAdminSavePageEvent;
import polaris.messages.incoming.catalog.catalogadmin.CatalogAdminSavePageIconEvent;
import polaris.messages.incoming.catalog.catalogadmin.CatalogAdminSavePageImagesEvent;
import polaris.messages.incoming.catalog.catalogadmin.CatalogAdminSetPageEnabledEvent;
import polaris.messages.incoming.catalog.catalogadmin.CatalogAdminSetPageVisibleEvent;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioDocumentApplyEvent;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioDocumentDryRunEvent;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioExportEvent;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioLoadHistoryEvent;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioOpenSessionEvent;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioUndoEvent;
import polaris.messages.incoming.catalog.catalogadmin.studio.CatalogStudioValidateEvent;
import polaris.messages.incoming.catalog.marketplace.BuyItemEvent;
import polaris.messages.incoming.catalog.marketplace.BuyMarketplaceTokensEvent;
import polaris.messages.incoming.catalog.marketplace.CancelAllOwnItemsEvent;
import polaris.messages.incoming.catalog.marketplace.ClearOwnHistoryEvent;
import polaris.messages.incoming.catalog.marketplace.RequestCreditsEvent;
import polaris.messages.incoming.catalog.marketplace.RequestItemInfoEvent;
import polaris.messages.incoming.catalog.marketplace.RequestOffersEvent;
import polaris.messages.incoming.catalog.marketplace.RequestOwnItemsEvent;
import polaris.messages.incoming.catalog.marketplace.RequestSellItemEvent;
import polaris.messages.incoming.catalog.marketplace.SellItemEvent;
import polaris.messages.incoming.catalog.marketplace.SellMultipleItemsEvent;
import polaris.messages.incoming.catalog.marketplace.TakeBackItemEvent;
import polaris.messages.incoming.catalog.recycler.OpenRecycleBoxEvent;
import polaris.messages.incoming.catalog.recycler.RecycleEvent;
import polaris.messages.incoming.catalog.recycler.ReloadRecyclerEvent;
import polaris.messages.incoming.catalog.recycler.RequestRecyclerLogicEvent;
import polaris.messages.incoming.catalog.specialitems.ClaimFreeProductEvent;
import polaris.messages.incoming.catalog.specialitems.RequestHasClaimedProductEvent;
import polaris.messages.incoming.collectibles.ClaimCollectibleRewardsEvent;
import polaris.messages.incoming.collectibles.ClaimCollectionBonusEvent;
import polaris.messages.incoming.collectibles.ClaimCollectionRewardEvent;
import polaris.messages.incoming.collectibles.GetCollectibleCollectionsEvent;
import polaris.messages.incoming.collectibles.GetCollectibleRewardClaimsEvent;
import polaris.messages.incoming.collectibles.GetCollectibleShopEvent;
import polaris.messages.incoming.collectibles.GetCollectibleTransferFeeEvent;
import polaris.messages.incoming.collectibles.GetCollectibleWalletsEvent;
import polaris.messages.incoming.collectibles.GetCollectiblesInventoryEvent;
import polaris.messages.incoming.collectibles.GetMintTokenOffersEvent;
import polaris.messages.incoming.collectibles.GetMintTokensEvent;
import polaris.messages.incoming.collectibles.GetMintableItemsEvent;
import polaris.messages.incoming.collectibles.GetMintingEnabledEvent;
import polaris.messages.incoming.collectibles.GetSelectedNftWardrobeOutfitEvent;
import polaris.messages.incoming.collectibles.GetUserNftWardrobeEvent;
import polaris.messages.incoming.collectibles.MintCollectibleEvent;
import polaris.messages.incoming.collectibles.OpenCollectibleRewardBoxEvent;
import polaris.messages.incoming.collectibles.PurchaseCollectibleEvent;
import polaris.messages.incoming.collectibles.PurchaseMintTokensEvent;
import polaris.messages.incoming.collectibles.SaveUserNftWardrobeEvent;
import polaris.messages.incoming.collectibles.SelectCollectibleWalletEvent;
import polaris.messages.incoming.collectibles.TransferCollectiblesEvent;
import polaris.messages.incoming.communitygoals.CommunityGoalVoteEvent;
import polaris.messages.incoming.communitygoals.GetCommunityGoalEarnedPrizesEvent;
import polaris.messages.incoming.communitygoals.GetCommunityGoalHallOfFameEvent;
import polaris.messages.incoming.communitygoals.HotelViewConcurrentUsersButtonEvent;
import polaris.messages.incoming.communitygoals.HotelViewRequestCommunityGoalEvent;
import polaris.messages.incoming.communitygoals.HotelViewRequestConcurrentUsersEvent;
import polaris.messages.incoming.communitygoals.RedeemCommunityGoalPrizeEvent;
import polaris.messages.incoming.crafting.CraftingAddRecipeEvent;
import polaris.messages.incoming.crafting.CraftingCraftItemEvent;
import polaris.messages.incoming.crafting.CraftingCraftSecretEvent;
import polaris.messages.incoming.crafting.RequestCraftingRecipesAvailableEvent;
import polaris.messages.incoming.crafting.RequestCraftingRecipesEvent;
import polaris.messages.incoming.discord.GetDiscordPreferencesEvent;
import polaris.messages.incoming.discord.UpdateDiscordPreferencesEvent;
import polaris.messages.incoming.earnings.ClaimAllEarningsRewardsEvent;
import polaris.messages.incoming.earnings.ClaimEarningsRewardEvent;
import polaris.messages.incoming.earnings.RequestEarningsCenterEvent;
import polaris.messages.incoming.events.calendar.AdventCalendarForceOpenEvent;
import polaris.messages.incoming.events.calendar.AdventCalendarOpenDayEvent;
import polaris.messages.incoming.floorplaneditor.FloorPlanEditorRequestBlockedTilesEvent;
import polaris.messages.incoming.floorplaneditor.FloorPlanEditorRequestDoorSettingsEvent;
import polaris.messages.incoming.floorplaneditor.FloorPlanEditorSaveEvent;
import polaris.messages.incoming.friends.AcceptFriendRequestEvent;
import polaris.messages.incoming.friends.AddFriendCategoryEvent;
import polaris.messages.incoming.friends.ChangeRelationEvent;
import polaris.messages.incoming.friends.ConsoleTypingEvent;
import polaris.messages.incoming.friends.DeclineFriendRequestEvent;
import polaris.messages.incoming.friends.FindNewFriendsEvent;
import polaris.messages.incoming.friends.FriendPrivateMessageEvent;
import polaris.messages.incoming.friends.FriendRequestEvent;
import polaris.messages.incoming.friends.InviteFriendsEvent;
import polaris.messages.incoming.friends.MarkConsoleReadEvent;
import polaris.messages.incoming.friends.MarkMessengerReadEvent;
import polaris.messages.incoming.friends.MoveFriendToCategoryEvent;
import polaris.messages.incoming.friends.RefreshFriendListEvent;
import polaris.messages.incoming.friends.RemoveFriendCategoryEvent;
import polaris.messages.incoming.friends.RemoveFriendEvent;
import polaris.messages.incoming.friends.RenameFriendCategoryEvent;
import polaris.messages.incoming.friends.RequestFriendRequestsEvent;
import polaris.messages.incoming.friends.RequestFriendsEvent;
import polaris.messages.incoming.friends.RequestInitFriendsEvent;
import polaris.messages.incoming.friends.RequestMessengerConversationsEvent;
import polaris.messages.incoming.friends.RequestMessengerHistoryEvent;
import polaris.messages.incoming.friends.RequestOfflineMessagesEvent;
import polaris.messages.incoming.friends.SearchUserEvent;
import polaris.messages.incoming.friends.SendMessengerMessageEvent;
import polaris.messages.incoming.friends.StalkFriendEvent;
import polaris.messages.incoming.furnieditor.FurniEditorBySpriteEvent;
import polaris.messages.incoming.furnieditor.FurniEditorDeleteEvent;
import polaris.messages.incoming.furnieditor.FurniEditorDetailEvent;
import polaris.messages.incoming.furnieditor.FurniEditorImportTextEvent;
import polaris.messages.incoming.furnieditor.FurniEditorInteractionsEvent;
import polaris.messages.incoming.furnieditor.FurniEditorRevertFurnidataEvent;
import polaris.messages.incoming.furnieditor.FurniEditorSearchEvent;
import polaris.messages.incoming.furnieditor.FurniEditorUpdateEvent;
import polaris.messages.incoming.furnieditor.FurniEditorUpdateFurnidataEvent;
import polaris.messages.incoming.gamecenter.GameCenterCheckDirectoryStatusEvent;
import polaris.messages.incoming.gamecenter.GameCenterEvent;
import polaris.messages.incoming.gamecenter.GameCenterJoinGameEvent;
import polaris.messages.incoming.gamecenter.GameCenterLeaveGameEvent;
import polaris.messages.incoming.gamecenter.GameCenterLoadGameEvent;
import polaris.messages.incoming.gamecenter.GameCenterRequestAccountStatusEvent;
import polaris.messages.incoming.gamecenter.GameCenterRequestGameStatusEvent;
import polaris.messages.incoming.gamecenter.GameCenterRequestGamesEvent;
import polaris.messages.incoming.gamecenter.GetSnowWarGameTokensOfferEvent;
import polaris.messages.incoming.gamecenter.PurchaseSnowWarGameTokensOfferEvent;
import polaris.messages.incoming.guardians.GuardianAcceptRequestEvent;
import polaris.messages.incoming.guardians.GuardianNoUpdatesWantedEvent;
import polaris.messages.incoming.guardians.GuardianVoteEvent;
import polaris.messages.incoming.guides.GuideCancelHelpRequestEvent;
import polaris.messages.incoming.guides.GuideCloseHelpRequestEvent;
import polaris.messages.incoming.guides.GuideHandleHelpRequestEvent;
import polaris.messages.incoming.guides.GuideInviteUserEvent;
import polaris.messages.incoming.guides.GuideRecommendHelperEvent;
import polaris.messages.incoming.guides.GuideReportHelperEvent;
import polaris.messages.incoming.guides.GuideUserMessageEvent;
import polaris.messages.incoming.guides.GuideUserTypingEvent;
import polaris.messages.incoming.guides.GuideVisitUserEvent;
import polaris.messages.incoming.guides.RequestGuideAssistanceEvent;
import polaris.messages.incoming.guides.RequestGuideToolEvent;
import polaris.messages.incoming.guilds.GetUserGuildBadgesMessageEvent;
import polaris.messages.incoming.guilds.GuildAcceptAllMembershipsEvent;
import polaris.messages.incoming.guilds.GuildAcceptMembershipEvent;
import polaris.messages.incoming.guilds.GuildChangeBadgeEvent;
import polaris.messages.incoming.guilds.GuildChangeColorsEvent;
import polaris.messages.incoming.guilds.GuildChangeNameDescEvent;
import polaris.messages.incoming.guilds.GuildChangeSettingsEvent;
import polaris.messages.incoming.guilds.GuildConfirmRemoveMemberEvent;
import polaris.messages.incoming.guilds.GuildDeclineMembershipEvent;
import polaris.messages.incoming.guilds.GuildDeleteEvent;
import polaris.messages.incoming.guilds.GuildRemoveAdminEvent;
import polaris.messages.incoming.guilds.GuildRemoveFavoriteEvent;
import polaris.messages.incoming.guilds.GuildRemoveMemberEvent;
import polaris.messages.incoming.guilds.GuildSetAdminEvent;
import polaris.messages.incoming.guilds.GuildSetFavoriteEvent;
import polaris.messages.incoming.guilds.GuildUnblockMemberEvent;
import polaris.messages.incoming.guilds.RequestGuildBuyEvent;
import polaris.messages.incoming.guilds.RequestGuildBuyRoomsEvent;
import polaris.messages.incoming.guilds.RequestGuildFurniWidgetEvent;
import polaris.messages.incoming.guilds.RequestGuildInfoEvent;
import polaris.messages.incoming.guilds.RequestGuildJoinEvent;
import polaris.messages.incoming.guilds.RequestGuildManageEvent;
import polaris.messages.incoming.guilds.RequestGuildMembersEvent;
import polaris.messages.incoming.guilds.RequestGuildPartsEvent;
import polaris.messages.incoming.guilds.RequestOwnGuildsEvent;
import polaris.messages.incoming.guilds.forums.GetUnreadForumsCountEvent;
import polaris.messages.incoming.guilds.forums.GuildForumDataEvent;
import polaris.messages.incoming.guilds.forums.GuildForumListEvent;
import polaris.messages.incoming.guilds.forums.GuildForumMarkAsReadEvent;
import polaris.messages.incoming.guilds.forums.GuildForumModerateMessageEvent;
import polaris.messages.incoming.guilds.forums.GuildForumModerateThreadEvent;
import polaris.messages.incoming.guilds.forums.GuildForumPostThreadEvent;
import polaris.messages.incoming.guilds.forums.GuildForumThreadUpdateEvent;
import polaris.messages.incoming.guilds.forums.GuildForumThreadsEvent;
import polaris.messages.incoming.guilds.forums.GuildForumThreadsMessagesEvent;
import polaris.messages.incoming.guilds.forums.GuildForumUpdateSettingsEvent;
import polaris.messages.incoming.habbicons.HabbiconRequestEvent;
import polaris.messages.incoming.habbicons.TriggerHabbiconEvent;
import polaris.messages.incoming.handshake.CompleteDiffieHandshakeEvent;
import polaris.messages.incoming.handshake.DisconnectEvent;
import polaris.messages.incoming.handshake.InitDiffieHandshakeEvent;
import polaris.messages.incoming.handshake.MachineIDEvent;
import polaris.messages.incoming.handshake.PingEvent;
import polaris.messages.incoming.handshake.ReleaseVersionEvent;
import polaris.messages.incoming.handshake.SecureLoginEvent;
import polaris.messages.incoming.helper.AppealReportEvent;
import polaris.messages.incoming.helper.GetMyReportsStatusEvent;
import polaris.messages.incoming.helper.MySanctionStatusEvent;
import polaris.messages.incoming.helper.RequestTalentTrackEvent;
import polaris.messages.incoming.hotelview.GetIsBadgeRequestFulfilledEvent;
import polaris.messages.incoming.hotelview.HotelViewClaimBadgeEvent;
import polaris.messages.incoming.hotelview.HotelViewClaimBadgeRewardEvent;
import polaris.messages.incoming.hotelview.HotelViewDataEvent;
import polaris.messages.incoming.hotelview.HotelViewEvent;
import polaris.messages.incoming.hotelview.HotelViewLandingRequestEvent;
import polaris.messages.incoming.hotelview.HotelViewLandingResetVotesEvent;
import polaris.messages.incoming.hotelview.HotelViewLandingSaveEvent;
import polaris.messages.incoming.hotelview.HotelViewLandingSaveSceneEvent;
import polaris.messages.incoming.hotelview.HotelViewLandingVoteEvent;
import polaris.messages.incoming.hotelview.HotelViewRequestBadgeRewardEvent;
import polaris.messages.incoming.hotelview.HotelViewRequestBonusRareEvent;
import polaris.messages.incoming.hotelview.HotelViewRequestLTDAvailabilityEvent;
import polaris.messages.incoming.hotelview.HotelViewRequestSecondsUntilEvent;
import polaris.messages.incoming.hotelview.RequestNewsListEvent;
import polaris.messages.incoming.hotelway.GetQuizQuestionsEvent;
import polaris.messages.incoming.hotelway.PostQuizAnswersEvent;
import polaris.messages.incoming.inventory.HotelViewInventoryEvent;
import polaris.messages.incoming.inventory.RequestInventoryBadgeDelete;
import polaris.messages.incoming.inventory.RequestInventoryBadgesEvent;
import polaris.messages.incoming.inventory.RequestInventoryBotsEvent;
import polaris.messages.incoming.inventory.RequestInventoryItemsDelete;
import polaris.messages.incoming.inventory.RequestInventoryItemsEvent;
import polaris.messages.incoming.inventory.RequestInventoryPetDelete;
import polaris.messages.incoming.inventory.RequestInventoryPetsEvent;
import polaris.messages.incoming.inventory.UnseenResetCategoryEvent;
import polaris.messages.incoming.inventory.UnseenResetItemsEvent;
import polaris.messages.incoming.inventory.nickicons.PurchaseNickIconEvent;
import polaris.messages.incoming.inventory.nickicons.RequestUserNickIconsEvent;
import polaris.messages.incoming.inventory.nickicons.SetActiveNickIconEvent;
import polaris.messages.incoming.inventory.prefixes.DeletePrefixEvent;
import polaris.messages.incoming.inventory.prefixes.PurchaseCatalogPrefixEvent;
import polaris.messages.incoming.inventory.prefixes.PurchasePrefixEvent;
import polaris.messages.incoming.inventory.prefixes.RequestUserPrefixesEvent;
import polaris.messages.incoming.inventory.prefixes.SetActivePrefixEvent;
import polaris.messages.incoming.inventory.prefixes.SetDisplayOrderEvent;
import polaris.messages.incoming.mentions.DeleteMentionEvent;
import polaris.messages.incoming.mentions.MarkMentionsReadEvent;
import polaris.messages.incoming.mentions.RequestMentionsEvent;
import polaris.messages.incoming.mentions.RequestMentionsPageEvent;
import polaris.messages.incoming.modtool.DeletePendingCallsForHelpEvent;
import polaris.messages.incoming.modtool.ModToolAlertEvent;
import polaris.messages.incoming.modtool.ModToolChangeRoomSettingsEvent;
import polaris.messages.incoming.modtool.ModToolCloseTicketEvent;
import polaris.messages.incoming.modtool.ModToolDefaultSanctionEvent;
import polaris.messages.incoming.modtool.ModToolIssueChangeTopicEvent;
import polaris.messages.incoming.modtool.ModToolIssueDefaultSanctionEvent;
import polaris.messages.incoming.modtool.ModToolKickEvent;
import polaris.messages.incoming.modtool.ModToolPickTicketEvent;
import polaris.messages.incoming.modtool.ModToolPreferencesEvent;
import polaris.messages.incoming.modtool.ModToolReleaseTicketEvent;
import polaris.messages.incoming.modtool.ModToolRequestIssueChatlogEvent;
import polaris.messages.incoming.modtool.ModToolRequestRoomChatlogEvent;
import polaris.messages.incoming.modtool.ModToolRequestRoomInfoEvent;
import polaris.messages.incoming.modtool.ModToolRequestRoomUserChatlogEvent;
import polaris.messages.incoming.modtool.ModToolRequestRoomVisitsEvent;
import polaris.messages.incoming.modtool.ModToolRequestUserChatlogEvent;
import polaris.messages.incoming.modtool.ModToolRequestUserInfoEvent;
import polaris.messages.incoming.modtool.ModToolRoomAlertEvent;
import polaris.messages.incoming.modtool.ModToolSanctionAlertEvent;
import polaris.messages.incoming.modtool.ModToolSanctionBanEvent;
import polaris.messages.incoming.modtool.ModToolSanctionMuteEvent;
import polaris.messages.incoming.modtool.ModToolSanctionTradeLockEvent;
import polaris.messages.incoming.modtool.ModToolWarnEvent;
import polaris.messages.incoming.modtool.ReportBullyEvent;
import polaris.messages.incoming.modtool.ReportCommentEvent;
import polaris.messages.incoming.modtool.ReportEvent;
import polaris.messages.incoming.modtool.ReportFriendPrivateChatEvent;
import polaris.messages.incoming.modtool.ReportPhotoEvent;
import polaris.messages.incoming.modtool.ReportThreadEvent;
import polaris.messages.incoming.modtool.RequestReportRoomEvent;
import polaris.messages.incoming.modtool.RequestReportUserBullyingEvent;
import polaris.messages.incoming.navigator.AddSavedSearchEvent;
import polaris.messages.incoming.navigator.ConvertGlobalRoomIdEvent;
import polaris.messages.incoming.navigator.DeleteSavedSearchEvent;
import polaris.messages.incoming.navigator.GetCategoriesWithUserCountEvent;
import polaris.messages.incoming.navigator.NavigatorCategoryListModeEvent;
import polaris.messages.incoming.navigator.NavigatorCollapseCategoryEvent;
import polaris.messages.incoming.navigator.NavigatorUncollapseCategoryEvent;
import polaris.messages.incoming.navigator.NewNavigatorActionEvent;
import polaris.messages.incoming.navigator.RequestCanCreateRoomEvent;
import polaris.messages.incoming.navigator.RequestCreateRoomEvent;
import polaris.messages.incoming.navigator.RequestDeleteRoomEvent;
import polaris.messages.incoming.navigator.RequestHighestScoreRoomsEvent;
import polaris.messages.incoming.navigator.RequestMyRoomsEvent;
import polaris.messages.incoming.navigator.RequestNavigatorSettingsEvent;
import polaris.messages.incoming.navigator.RequestNewNavigatorDataEvent;
import polaris.messages.incoming.navigator.RequestNewNavigatorRoomsEvent;
import polaris.messages.incoming.navigator.RequestPopularRoomsEvent;
import polaris.messages.incoming.navigator.RequestPublicRoomsEvent;
import polaris.messages.incoming.navigator.RequestRoomCategoriesEvent;
import polaris.messages.incoming.navigator.RequestTagsEvent;
import polaris.messages.incoming.navigator.RoomNetworkOpenConnectionEvent;
import polaris.messages.incoming.navigator.SaveWindowSettingsEvent;
import polaris.messages.incoming.navigator.SearchRoomsByTagEvent;
import polaris.messages.incoming.navigator.SearchRoomsEvent;
import polaris.messages.incoming.navigator.SearchRoomsFriendsNowEvent;
import polaris.messages.incoming.navigator.SearchRoomsFriendsOwnEvent;
import polaris.messages.incoming.navigator.SearchRoomsInGroupEvent;
import polaris.messages.incoming.navigator.SearchRoomsMyFavouriteEvent;
import polaris.messages.incoming.navigator.SearchRoomsVisitedEvent;
import polaris.messages.incoming.navigator.SearchRoomsWithRightsEvent;
import polaris.messages.incoming.notifications.ActivateNotificationsEvent;
import polaris.messages.incoming.polls.AnswerPollEvent;
import polaris.messages.incoming.polls.CancelPollEvent;
import polaris.messages.incoming.polls.GetPollDataEvent;
import polaris.messages.incoming.quests.AcceptQuestEvent;
import polaris.messages.incoming.quests.ActivateQuestEvent;
import polaris.messages.incoming.quests.CancelDailyQuestEvent;
import polaris.messages.incoming.quests.ClaimDailyTaskEvent;
import polaris.messages.incoming.quests.ClaimRewardTrackPrizeEvent;
import polaris.messages.incoming.quests.GetDailyQuestEvent;
import polaris.messages.incoming.quests.GetDailyTasksEvent;
import polaris.messages.incoming.quests.GetQuestsEvent;
import polaris.messages.incoming.quests.GetRewardTracksEvent;
import polaris.messages.incoming.quests.GetSeasonalQuestsOnlyEvent;
import polaris.messages.incoming.quests.OpenQuestTrackerEvent;
import polaris.messages.incoming.quests.PurchaseRewardTrackPremiumEvent;
import polaris.messages.incoming.quests.RejectQuestEvent;
import polaris.messages.incoming.quests.StartCampaignEvent;
import polaris.messages.incoming.quests.admin.DeleteRewardTrackEntityEvent;
import polaris.messages.incoming.quests.admin.GetRewardTrackAdminDataEvent;
import polaris.messages.incoming.quests.admin.SaveRewardTrackEvent;
import polaris.messages.incoming.quests.admin.SaveRewardTrackPrizeEvent;
import polaris.messages.incoming.quests.admin.SaveRewardTrackTaskEvent;
import polaris.messages.incoming.quests.admin.SaveRewardTrackTextsEvent;
import polaris.messages.incoming.quests.admin.SearchRewardTrackFurniEvent;
import polaris.messages.incoming.rooms.ChangeQueueEvent;
import polaris.messages.incoming.rooms.HandleDoorbellEvent;
import polaris.messages.incoming.rooms.RequestRoomDataEvent;
import polaris.messages.incoming.rooms.RequestRoomHeightmapEvent;
import polaris.messages.incoming.rooms.RequestRoomLoadEvent;
import polaris.messages.incoming.rooms.RequestRoomRightsEvent;
import polaris.messages.incoming.rooms.RequestRoomSettingsEvent;
import polaris.messages.incoming.rooms.RequestRoomWordFilterEvent;
import polaris.messages.incoming.rooms.RoomBackgroundEvent;
import polaris.messages.incoming.rooms.RoomFavoriteEvent;
import polaris.messages.incoming.rooms.RoomMuteEvent;
import polaris.messages.incoming.rooms.RoomPlacePaintEvent;
import polaris.messages.incoming.rooms.RoomRemoveAllRightsEvent;
import polaris.messages.incoming.rooms.RoomRemoveBackgroundEvent;
import polaris.messages.incoming.rooms.RoomRemovePaintEvent;
import polaris.messages.incoming.rooms.RoomRemoveRightsEvent;
import polaris.messages.incoming.rooms.RoomRequestBannedUsersEvent;
import polaris.messages.incoming.rooms.RoomSettingsSaveEvent;
import polaris.messages.incoming.rooms.RoomStaffPickEvent;
import polaris.messages.incoming.rooms.RoomUnFavoriteEvent;
import polaris.messages.incoming.rooms.RoomVoteEvent;
import polaris.messages.incoming.rooms.RoomWordFilterModifyEvent;
import polaris.messages.incoming.rooms.SetHomeRoomEvent;
import polaris.messages.incoming.rooms.UpdateRoomCategoryAndTradeSettingsEvent;
import polaris.messages.incoming.rooms.bots.BotPickupEvent;
import polaris.messages.incoming.rooms.bots.BotPlaceEvent;
import polaris.messages.incoming.rooms.bots.BotSaveSettingsEvent;
import polaris.messages.incoming.rooms.bots.BotSettingsEvent;
import polaris.messages.incoming.rooms.competition.CompetitionRoomsSearchEvent;
import polaris.messages.incoming.rooms.competition.ForwardToACompetitionRoomEvent;
import polaris.messages.incoming.rooms.competition.ForwardToASubmittableRoomEvent;
import polaris.messages.incoming.rooms.competition.ForwardToRandomCompetitionRoomEvent;
import polaris.messages.incoming.rooms.competition.GetIsUserPartOfCompetitionEvent;
import polaris.messages.incoming.rooms.competition.RoomCompetitionInitEvent;
import polaris.messages.incoming.rooms.competition.SubmitRoomToCompetitionEvent;
import polaris.messages.incoming.rooms.competition.VoteForRoomEvent;
import polaris.messages.incoming.rooms.items.AdvertisingSaveEvent;
import polaris.messages.incoming.rooms.items.ChestCloseEvent;
import polaris.messages.incoming.rooms.items.ChestOpenEvent;
import polaris.messages.incoming.rooms.items.ChestRequestLogEvent;
import polaris.messages.incoming.rooms.items.ChestSaveNotificationsEvent;
import polaris.messages.incoming.rooms.items.ChestSaveOptionsEvent;
import polaris.messages.incoming.rooms.items.ChestSaveSettingsEvent;
import polaris.messages.incoming.rooms.items.ChestStartDepositEvent;
import polaris.messages.incoming.rooms.items.ChestUpgradeCapacityEvent;
import polaris.messages.incoming.rooms.items.ChestWithdrawAllFurniEvent;
import polaris.messages.incoming.rooms.items.ChestWithdrawEvent;
import polaris.messages.incoming.rooms.items.ChestWithdrawFurniEvent;
import polaris.messages.incoming.rooms.items.ClickFurniEvent;
import polaris.messages.incoming.rooms.items.CloseDiceEvent;
import polaris.messages.incoming.rooms.items.FootballGateSaveLookEvent;
import polaris.messages.incoming.rooms.items.MannequinSaveLookEvent;
import polaris.messages.incoming.rooms.items.MannequinSaveNameEvent;
import polaris.messages.incoming.rooms.items.MoodLightSaveSettingsEvent;
import polaris.messages.incoming.rooms.items.MoodLightSettingsEvent;
import polaris.messages.incoming.rooms.items.MoodLightTurnOnEvent;
import polaris.messages.incoming.rooms.items.MoveWallItemEvent;
import polaris.messages.incoming.rooms.items.MysteryBoxWaitingCanceledEvent;
import polaris.messages.incoming.rooms.items.OpenMysteryTrophyEvent;
import polaris.messages.incoming.rooms.items.PostItDeleteEvent;
import polaris.messages.incoming.rooms.items.PostItPlaceEvent;
import polaris.messages.incoming.rooms.items.PostItRequestDataEvent;
import polaris.messages.incoming.rooms.items.PostItSaveDataEvent;
import polaris.messages.incoming.rooms.items.RedeemClothingEvent;
import polaris.messages.incoming.rooms.items.RedeemEmeraldFurniEvent;
import polaris.messages.incoming.rooms.items.RedeemItemEvent;
import polaris.messages.incoming.rooms.items.RoomPickupChooserEvent;
import polaris.messages.incoming.rooms.items.RoomPickupItemEvent;
import polaris.messages.incoming.rooms.items.RoomPlaceItemEvent;
import polaris.messages.incoming.rooms.items.RotateMoveItemEvent;
import polaris.messages.incoming.rooms.items.SavePostItStickyPoleEvent;
import polaris.messages.incoming.rooms.items.SetBuildHeightEvent;
import polaris.messages.incoming.rooms.items.SetBuildUnderpassEvent;
import polaris.messages.incoming.rooms.items.SetStackHelperAdjacentHeightEvent;
import polaris.messages.incoming.rooms.items.SetStackHelperHeightEvent;
import polaris.messages.incoming.rooms.items.ToggleFloorItemEvent;
import polaris.messages.incoming.rooms.items.ToggleWallItemEvent;
import polaris.messages.incoming.rooms.items.TriggerColorWheelEvent;
import polaris.messages.incoming.rooms.items.TriggerDiceEvent;
import polaris.messages.incoming.rooms.items.TriggerOneWayGateEvent;
import polaris.messages.incoming.rooms.items.UpdateFurniturePositionEvent;
import polaris.messages.incoming.rooms.items.UseRandomStateItemEvent;
import polaris.messages.incoming.rooms.items.WiredChestLockEvent;
import polaris.messages.incoming.rooms.items.WiredChestRoomLogsEvent;
import polaris.messages.incoming.rooms.items.WiredChestTransactionDetailsEvent;
import polaris.messages.incoming.rooms.items.WiredTradeAcceptEvent;
import polaris.messages.incoming.rooms.items.WiredTradeCancelEvent;
import polaris.messages.incoming.rooms.items.WiredTradeOfferItemsEvent;
import polaris.messages.incoming.rooms.items.jukebox.JukeBoxAddSoundTrackEvent;
import polaris.messages.incoming.rooms.items.jukebox.JukeBoxEventOne;
import polaris.messages.incoming.rooms.items.jukebox.JukeBoxEventTwo;
import polaris.messages.incoming.rooms.items.jukebox.JukeBoxRemoveSoundTrackEvent;
import polaris.messages.incoming.rooms.items.jukebox.JukeBoxRequestPlayListEvent;
import polaris.messages.incoming.rooms.items.lovelock.LoveLockStartConfirmEvent;
import polaris.messages.incoming.rooms.items.rentable.ExtendRentOrBuyoutFurniEvent;
import polaris.messages.incoming.rooms.items.rentable.ExtendRentOrBuyoutStripItemEvent;
import polaris.messages.incoming.rooms.items.rentable.GetRentOrBuyoutOfferEvent;
import polaris.messages.incoming.rooms.items.rentablespace.GetRentableSpaceStatusEvent;
import polaris.messages.incoming.rooms.items.rentablespace.RentSpaceCancelEvent;
import polaris.messages.incoming.rooms.items.rentablespace.RentSpaceEvent;
import polaris.messages.incoming.rooms.items.youtube.YoutubeRequestPlaylistChange;
import polaris.messages.incoming.rooms.items.youtube.YoutubeRequestPlaylists;
import polaris.messages.incoming.rooms.items.youtube.YoutubeRequestStateChange;
import polaris.messages.incoming.rooms.pets.BreedMonsterplantsEvent;
import polaris.messages.incoming.rooms.pets.CompostMonsterplantEvent;
import polaris.messages.incoming.rooms.pets.ConfirmPetBreedingEvent;
import polaris.messages.incoming.rooms.pets.HorseRemoveSaddleEvent;
import polaris.messages.incoming.rooms.pets.MovePetEvent;
import polaris.messages.incoming.rooms.pets.PetPackageNameEvent;
import polaris.messages.incoming.rooms.pets.PetPickupEvent;
import polaris.messages.incoming.rooms.pets.PetPlaceEvent;
import polaris.messages.incoming.rooms.pets.PetRideEvent;
import polaris.messages.incoming.rooms.pets.PetRideSettingsEvent;
import polaris.messages.incoming.rooms.pets.PetSupplementEvent;
import polaris.messages.incoming.rooms.pets.PetUseItemEvent;
import polaris.messages.incoming.rooms.pets.RequestPetInformationEvent;
import polaris.messages.incoming.rooms.pets.RequestPetTrainingPanelEvent;
import polaris.messages.incoming.rooms.pets.RoomUserGiveHandItemPetEvent;
import polaris.messages.incoming.rooms.pets.ScratchPetEvent;
import polaris.messages.incoming.rooms.pets.StopBreedingEvent;
import polaris.messages.incoming.rooms.pets.ToggleMonsterplantBreedableEvent;
import polaris.messages.incoming.rooms.promotions.BuyRoomPromotionEvent;
import polaris.messages.incoming.rooms.promotions.CancelRoomPromotionEvent;
import polaris.messages.incoming.rooms.promotions.RequestPromotionRoomsEvent;
import polaris.messages.incoming.rooms.promotions.UpdateRoomPromotionEvent;
import polaris.messages.incoming.rooms.raidprotection.GetRaidProtectionSettingsEvent;
import polaris.messages.incoming.rooms.raidprotection.SaveRaidProtectionSettingsEvent;
import polaris.messages.incoming.rooms.users.ClickUserEvent;
import polaris.messages.incoming.rooms.users.IgnoreRoomUserEvent;
import polaris.messages.incoming.rooms.users.IgnoreUserIdEvent;
import polaris.messages.incoming.rooms.users.RequestRoomUserTagsEvent;
import polaris.messages.incoming.rooms.users.RoomUserActionEvent;
import polaris.messages.incoming.rooms.users.RoomUserBanEvent;
import polaris.messages.incoming.rooms.users.RoomUserDanceEvent;
import polaris.messages.incoming.rooms.users.RoomUserDropHandItemEvent;
import polaris.messages.incoming.rooms.users.RoomUserGiveHandItemEvent;
import polaris.messages.incoming.rooms.users.RoomUserGiveRespectEvent;
import polaris.messages.incoming.rooms.users.RoomUserGiveRightsEvent;
import polaris.messages.incoming.rooms.users.RoomUserKickEvent;
import polaris.messages.incoming.rooms.users.RoomUserLookAtPoint;
import polaris.messages.incoming.rooms.users.RoomUserMuteEvent;
import polaris.messages.incoming.rooms.users.RoomUserRemoveRightsEvent;
import polaris.messages.incoming.rooms.users.RoomUserShoutEvent;
import polaris.messages.incoming.rooms.users.RoomUserSignEvent;
import polaris.messages.incoming.rooms.users.RoomUserSitEvent;
import polaris.messages.incoming.rooms.users.RoomUserStartTypingEvent;
import polaris.messages.incoming.rooms.users.RoomUserStopTypingEvent;
import polaris.messages.incoming.rooms.users.RoomUserTalkEvent;
import polaris.messages.incoming.rooms.users.RoomUserWalkEvent;
import polaris.messages.incoming.rooms.users.RoomUserWhisperEvent;
import polaris.messages.incoming.rooms.users.UnIgnoreRoomUserEvent;
import polaris.messages.incoming.rooms.users.UnbanRoomUserEvent;
import polaris.messages.incoming.rooms.users.UnmuteUserEvent;
import polaris.messages.incoming.selfdonation.SelfDonationEvent;
import polaris.messages.incoming.trading.TradeAcceptEvent;
import polaris.messages.incoming.trading.TradeCancelEvent;
import polaris.messages.incoming.trading.TradeCancelOfferItemEvent;
import polaris.messages.incoming.trading.TradeCloseEvent;
import polaris.messages.incoming.trading.TradeConfirmEvent;
import polaris.messages.incoming.trading.TradeOfferItemEvent;
import polaris.messages.incoming.trading.TradeOfferMultipleItemsEvent;
import polaris.messages.incoming.trading.TradeStartEvent;
import polaris.messages.incoming.trading.TradeUnAcceptEvent;
import polaris.messages.incoming.translation.TranslationLanguagesRequestEvent;
import polaris.messages.incoming.translation.TranslationTextRequestEvent;
import polaris.messages.incoming.unknown.RequestResolutionEvent;
import polaris.messages.incoming.unknown.ResetResolutionAchievementEvent;
import polaris.messages.incoming.unknown.UnknownEvent1;
import polaris.messages.incoming.users.ActivateEffectEvent;
import polaris.messages.incoming.users.AddCustomWordFilterWordEvent;
import polaris.messages.incoming.users.BlockUserEvent;
import polaris.messages.incoming.users.ChangeChatBubbleEvent;
import polaris.messages.incoming.users.ChangeEmailEvent;
import polaris.messages.incoming.users.ChangeInfostandBgEvent;
import polaris.messages.incoming.users.ChangeNameCheckUsernameEvent;
import polaris.messages.incoming.users.ConfirmChangeNameEvent;
import polaris.messages.incoming.users.EnableEffectEvent;
import polaris.messages.incoming.users.GetBadgeInfoEvent;
import polaris.messages.incoming.users.GetBlockedUsersEvent;
import polaris.messages.incoming.users.GetEmailStatusEvent;
import polaris.messages.incoming.users.GetIgnoredUsersEvent;
import polaris.messages.incoming.users.PickNewUserGiftEvent;
import polaris.messages.incoming.users.RemoveCustomWordFilterWordEvent;
import polaris.messages.incoming.users.ReplenishRespectEvent;
import polaris.messages.incoming.users.RequestClubCenterEvent;
import polaris.messages.incoming.users.RequestCustomWordFilterEvent;
import polaris.messages.incoming.users.RequestMeMenuSettingsEvent;
import polaris.messages.incoming.users.RequestProfileFriendsEvent;
import polaris.messages.incoming.users.RequestUserCitizinShipEvent;
import polaris.messages.incoming.users.RequestUserClubEvent;
import polaris.messages.incoming.users.RequestUserCreditsEvent;
import polaris.messages.incoming.users.RequestUserDataEvent;
import polaris.messages.incoming.users.RequestUserProfileEvent;
import polaris.messages.incoming.users.RequestUserWardrobeEvent;
import polaris.messages.incoming.users.RequestWearingBadgesEvent;
import polaris.messages.incoming.users.SaveBlockCameraFollowEvent;
import polaris.messages.incoming.users.SaveChatPreferencesEvent;
import polaris.messages.incoming.users.SaveGamePrivacySettingsEvent;
import polaris.messages.incoming.users.SaveIgnoreRoomInvitesEvent;
import polaris.messages.incoming.users.SaveMottoEvent;
import polaris.messages.incoming.users.SaveOnlineIndicatorPreferenceEvent;
import polaris.messages.incoming.users.SavePreferOldChatEvent;
import polaris.messages.incoming.users.SaveUserVolumesEvent;
import polaris.messages.incoming.users.SaveWardrobeEvent;
import polaris.messages.incoming.users.SaveWiredMenuSettingsEvent;
import polaris.messages.incoming.users.UnblockUserEvent;
import polaris.messages.incoming.users.UpdateUIFlagsEvent;
import polaris.messages.incoming.users.UserActivityEvent;
import polaris.messages.incoming.users.UserNuxEvent;
import polaris.messages.incoming.users.UserSaveLookEvent;
import polaris.messages.incoming.users.UserWearBadgeEvent;
import polaris.messages.incoming.wired.GetWiredContractContentsEvent;
import polaris.messages.incoming.wired.UpdateWiredContractEvent;
import polaris.messages.incoming.wired.WiredAddonSaveDataEvent;
import polaris.messages.incoming.wired.WiredAddonShareSynchronizeEvent;
import polaris.messages.incoming.wired.WiredAllVariablesRequestEvent;
import polaris.messages.incoming.wired.WiredApplySetConditionsEvent;
import polaris.messages.incoming.wired.WiredArrayInspectionRequestEvent;
import polaris.messages.incoming.wired.WiredArrayInspectionUpdateEvent;
import polaris.messages.incoming.wired.WiredConditionSaveDataEvent;
import polaris.messages.incoming.wired.WiredEffectSaveDataEvent;
import polaris.messages.incoming.wired.WiredErrorLogsClearEvent;
import polaris.messages.incoming.wired.WiredErrorLogsRequestEvent;
import polaris.messages.incoming.wired.WiredFeatureCapabilitiesEvent;
import polaris.messages.incoming.wired.WiredFurniRuntimeStateRequestEvent;
import polaris.messages.incoming.wired.WiredGenerateWebApiKeyEvent;
import polaris.messages.incoming.wired.WiredGetVariablesForObjectEvent;
import polaris.messages.incoming.wired.WiredLabelSaveSettingsEvent;
import polaris.messages.incoming.wired.WiredLabelSaveTextEvent;
import polaris.messages.incoming.wired.WiredLeaderboardSubscribeEvent;
import polaris.messages.incoming.wired.WiredLinkCreateNetworkEvent;
import polaris.messages.incoming.wired.WiredLinkDeleteNetworkEvent;
import polaris.messages.incoming.wired.WiredLinkGetConnectionsEvent;
import polaris.messages.incoming.wired.WiredLinkRenameNetworkEvent;
import polaris.messages.incoming.wired.WiredLinkRequestConfigEvent;
import polaris.messages.incoming.wired.WiredLinkSaveSelectionEvent;
import polaris.messages.incoming.wired.WiredMenuPermissionsSaveEvent;
import polaris.messages.incoming.wired.WiredMonitorRequestEvent;
import polaris.messages.incoming.wired.WiredOpenEvent;
import polaris.messages.incoming.wired.WiredRoomLogsPageEvent;
import polaris.messages.incoming.wired.WiredRoomSettingsRequestEvent;
import polaris.messages.incoming.wired.WiredRoomSettingsSaveEvent;
import polaris.messages.incoming.wired.WiredRoomStateActionEvent;
import polaris.messages.incoming.wired.WiredRoomStatsRequestEvent;
import polaris.messages.incoming.wired.WiredSelectorSaveDataEvent;
import polaris.messages.incoming.wired.WiredSetupExportEvent;
import polaris.messages.incoming.wired.WiredSetupImportEvent;
import polaris.messages.incoming.wired.WiredTriggerSaveDataEvent;
import polaris.messages.incoming.wired.WiredUserInspectMoveEvent;
import polaris.messages.incoming.wired.WiredUserSelectedEvent;
import polaris.messages.incoming.wired.WiredUserVariableManageEvent;
import polaris.messages.incoming.wired.WiredUserVariableUpdateEvent;
import polaris.messages.incoming.wired.WiredUserVariablesRequestEvent;
import polaris.messages.incoming.wired.WiredVariableHashesEvent;
import polaris.messages.incoming.wired.WiredVariableHoldersPageEvent;
import polaris.messages.incoming.wired.WiredVariableHoldersRequestEvent;
import polaris.messages.incoming.wired.WiredVariableSaveDataEvent;
import polaris.monitoring.EmulatorNetworkStats;
import polaris.plugin.EventHandler;
import polaris.plugin.events.emulator.EmulatorConfigUpdatedEvent;

public class PacketManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PacketManager.class);
    private static final ClassValue<Constructor<? extends MessageHandler>> HANDLER_CONSTRUCTORS = new ClassValue<>() {
        @Override
        protected Constructor<? extends MessageHandler> computeValue(Class<?> type) {
            try {
                return type.asSubclass(MessageHandler.class).getDeclaredConstructor();
            } catch (NoSuchMethodException exception) {
                throw new IllegalArgumentException(
                        "Incoming handler has no default constructor: " + type.getName(), exception);
            }
        }
    };

    private static final List<Integer> logList = new ArrayList<>();
    public static volatile boolean DEBUG_SHOW_PACKETS = false;
    public static volatile boolean MULTI_THREADED_PACKET_HANDLING = false;
    private final Map<Integer, Class<? extends MessageHandler>> incoming;
    private final Map<Integer, List<ICallable>> callables;
    private final PacketNames names;
    private final PacketErrorLogLimiter errorLog = new PacketErrorLogLimiter(60_000);

    public PacketManager() throws Exception {
        this.incoming = new HashMap<>();
        this.callables = new HashMap<>();
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
        this.registerSnowWar();
        this.registerEarnings();

        RuntimeValidationReport report = PacketRuntimeValidator.validateHandlers(this.incoming);
        report.logErrors(LOGGER, "Incoming packet handler validation");
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
        if (header < 0) return;

        if (this.incoming.containsKey(header)) {
            throw new Exception(
                    "Header already registered. Failed to register " + handler.getName() + " with header " + header);
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
        this.callables.remove(header);
    }

    public void handlePacket(GameClient client, ClientMessage packet) {
        if (client == null || Emulator.isShuttingDown) return;

        try {
            EmulatorNetworkStats.recordIncoming(packet.bytesAvailable() + 6);

            if (this.isRegistered(packet.getMessageId())) {
                Class<? extends MessageHandler> handlerClass = this.incoming.get(packet.getMessageId());

                if (handlerClass == null) throw new Exception("Unknown message " + packet.getMessageId());

                if (client.getUser() == null && !handlerClass.isAnnotationPresent(NoAuthMessage.class)) {
                    if (DEBUG_SHOW_PACKETS) {
                        LOGGER.warn("Client packet {} requires an authenticated session.", packet.getMessageId());
                    }

                    return;
                }

                final MessageHandler handler = constructorFor(handlerClass).newInstance();

                if (handler.getRatelimit() > 0) {
                    long now = System.currentTimeMillis();
                    String rateLimitGroup = handler.getRatelimitGroup();
                    boolean rateLimited;

                    if (rateLimitGroup != null && !rateLimitGroup.isBlank()) {
                        rateLimited = !acquireGroupedRateLimit(
                                client.groupedMessageRateLimitDeadlines, rateLimitGroup, handler.getRatelimit(), now);
                    } else {
                        rateLimited = client.messageTimestamps.containsKey(handlerClass)
                                && now - client.messageTimestamps.get(handlerClass) < handler.getRatelimit();
                    }

                    if (rateLimited) {
                        if (PacketManager.DEBUG_SHOW_PACKETS) {
                            LOGGER.warn("Client packet {} was ratelimited.", packet.getMessageId());
                        }

                        return;
                    }

                    if (rateLimitGroup == null || rateLimitGroup.isBlank()) {
                        client.messageTimestamps.put(handlerClass, now);
                    }
                }

                if (logList.contains(packet.getMessageId()) && client.getUser() != null) {
                    LOGGER.info(
                            "User {} sent packet {} with body {}",
                            client.getUser().getUserInfo().getUsername(),
                            packet.getMessageId(),
                            packet.getMessageBody());
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
            PacketErrorLogLimiter.Decision decision =
                    this.errorLog.record(packet.getMessageId(), e.getClass(), System.currentTimeMillis());
            if (decision.log()) {
                LOGGER.error(
                        "Caught exception handling packet {} ({} more like it in the last minute)",
                        packet.getMessageId(),
                        decision.suppressedSinceLast(),
                        e);
            }
        }
    }

    static boolean acquireGroupedRateLimit(Map<String, Long> deadlines, String group, long cooldownMs, long nowMs) {
        if (deadlines == null || group == null || group.isBlank() || cooldownMs <= 0) {
            return true;
        }

        AtomicBoolean acquired = new AtomicBoolean(false);
        deadlines.compute(group, (key, deadline) -> {
            if (deadline != null && deadline > nowMs) {
                return deadline;
            }

            acquired.set(true);
            return cooldownMs > Long.MAX_VALUE - nowMs ? Long.MAX_VALUE : nowMs + cooldownMs;
        });
        return acquired.get();
    }

    boolean isRegistered(int header) {
        return this.incoming.containsKey(header);
    }

    static Constructor<? extends MessageHandler> constructorFor(Class<? extends MessageHandler> handlerClass) {
        return HANDLER_CONSTRUCTORS.get(handlerClass);
    }

    private void registerAmbassadors() throws Exception {
        this.registerHandler(Incoming.AmbassadorAlertCommandEvent, AmbassadorAlertCommandEvent.class);
        this.registerHandler(Incoming.AmbassadorVisitCommandEvent, AmbassadorVisitCommandEvent.class);
    }

    private void registerCatalog() throws Exception {
        this.registerHandler(Incoming.RequestRecylerLogicEvent, RequestRecyclerLogicEvent.class);
        this.registerHandler(Incoming.RequestDiscountEvent, RequestDiscountEvent.class);
        this.registerHandler(Incoming.RequestGiftConfigurationEvent, RequestGiftConfigurationEvent.class);
        this.registerHandler(Incoming.RequestHasClaimedProductEvent, RequestHasClaimedProductEvent.class);
        this.registerHandler(Incoming.ClaimFreeProductEvent, ClaimFreeProductEvent.class);
        this.registerHandler(Incoming.GetCollectibleWalletsEvent, GetCollectibleWalletsEvent.class);
        this.registerHandler(Incoming.SelectCollectibleWalletEvent, SelectCollectibleWalletEvent.class);
        this.registerHandler(Incoming.GetCollectibleCollectionsEvent, GetCollectibleCollectionsEvent.class);
        this.registerHandler(Incoming.ClaimCollectionBonusEvent, ClaimCollectionBonusEvent.class);
        this.registerHandler(Incoming.ClaimCollectionRewardEvent, ClaimCollectionRewardEvent.class);
        this.registerHandler(Incoming.GetCollectibleShopEvent, GetCollectibleShopEvent.class);
        this.registerHandler(Incoming.PurchaseCollectibleEvent, PurchaseCollectibleEvent.class);
        this.registerHandler(Incoming.GetCollectibleTransferFeeEvent, GetCollectibleTransferFeeEvent.class);
        this.registerHandler(Incoming.TransferCollectiblesEvent, TransferCollectiblesEvent.class);
        this.registerHandler(Incoming.GetCollectibleRewardClaimsEvent, GetCollectibleRewardClaimsEvent.class);
        this.registerHandler(Incoming.ClaimCollectibleRewardsEvent, ClaimCollectibleRewardsEvent.class);
        this.registerHandler(Incoming.OpenCollectibleRewardBoxEvent, OpenCollectibleRewardBoxEvent.class);
        this.registerHandler(Incoming.GetCollectiblesInventoryEvent, GetCollectiblesInventoryEvent.class);
        this.registerHandler(Incoming.GetMintableItemsEvent, GetMintableItemsEvent.class);
        this.registerHandler(Incoming.GetMintingEnabledEvent, GetMintingEnabledEvent.class);
        this.registerHandler(Incoming.GetMintTokenOffersEvent, GetMintTokenOffersEvent.class);
        this.registerHandler(Incoming.GetMintTokensEvent, GetMintTokensEvent.class);
        this.registerHandler(Incoming.PurchaseMintTokensEvent, PurchaseMintTokensEvent.class);
        this.registerHandler(Incoming.MintCollectibleEvent, MintCollectibleEvent.class);
        this.registerHandler(Incoming.RedeemEmeraldFurniEvent, RedeemEmeraldFurniEvent.class);
        this.registerHandler(Incoming.GetUserNftWardrobeEvent, GetUserNftWardrobeEvent.class);
        this.registerHandler(Incoming.SaveUserNftWardrobeEvent, SaveUserNftWardrobeEvent.class);
        this.registerHandler(Incoming.GetSelectedNftWardrobeOutfitEvent, GetSelectedNftWardrobeOutfitEvent.class);
        this.registerHandler(Incoming.GetMarketplaceConfigEvent, RequestMarketplaceConfigEvent.class);
        this.registerHandler(Incoming.RequestCatalogModeEvent, RequestCatalogModeEvent.class);
        this.registerHandler(
                Incoming.MarkCatalogNewAdditionsPageOpenedEvent, MarkCatalogNewAdditionsPageOpenedEvent.class);
        this.registerHandler(Incoming.BuildersClubQueryFurniCountEvent, BuildersClubQueryFurniCountEvent.class);
        this.registerHandler(Incoming.BuildersClubPlaceRoomItemEvent, BuildersClubPlaceRoomItemEvent.class);
        this.registerHandler(Incoming.RecolorFurniEvent, polaris.messages.incoming.rooms.items.RecolorFurniEvent.class);
        this.registerHandler(Incoming.BuildersClubPlaceWallItemEvent, BuildersClubPlaceWallItemEvent.class);
        this.registerHandler(Incoming.RequestCatalogPageEvent, RequestCatalogPageEvent.class);
        this.registerHandler(Incoming.CatalogProductMetadataEvent, CatalogProductMetadataEvent.class);
        this.registerHandler(Incoming.CatalogRuntimeConfigurationEvent, CatalogRuntimeConfigurationEvent.class);
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
        this.registerHandler(Incoming.BuyMarketplaceTokensEvent, BuyMarketplaceTokensEvent.class);
        this.registerHandler(Incoming.SellItemEvent, SellItemEvent.class);
        this.registerHandler(Incoming.SellMultipleItemsEvent, SellMultipleItemsEvent.class);
        this.registerHandler(Incoming.CancelAllOwnItemsEvent, CancelAllOwnItemsEvent.class);
        this.registerHandler(Incoming.ClearOwnHistoryEvent, ClearOwnHistoryEvent.class);
        this.registerHandler(Incoming.RequestCreditsEvent, RequestCreditsEvent.class);
        this.registerHandler(Incoming.RequestPetBreedsEvent, RequestPetBreedsEvent.class);
        this.registerHandler(Incoming.CheckPetNameEvent, CheckPetNameEvent.class);
        this.registerHandler(Incoming.GetClubDataEvent, RequestClubDataEvent.class);
        this.registerHandler(Incoming.RequestClubGiftsEvent, RequestClubGiftsEvent.class);
        this.registerHandler(Incoming.CatalogSearchedItemEvent, CatalogSearchedItemEvent.class);
        this.registerHandler(Incoming.RequestTargetOfferEvent, RequestTargetOfferEvent.class);
        this.registerHandler(Incoming.PurchaseTargetOfferEvent, PurchaseTargetOfferEvent.class);
        this.registerHandler(Incoming.TargetOfferStateEvent, TargetOfferStateEvent.class);
        this.registerHandler(Incoming.CatalogSelectClubGiftEvent, CatalogSelectClubGiftEvent.class);
        this.registerHandler(Incoming.RequestClubCenterEvent, RequestClubCenterEvent.class);
        this.registerHandler(Incoming.CatalogRequestClubDiscountEvent, CatalogRequestClubDiscountEvent.class);
        this.registerHandler(Incoming.RequestClubExtendConfirmEvent, RequestClubExtendConfirmEvent.class);
        this.registerHandler(Incoming.CatalogBuyClubDiscountEvent, CatalogBuyClubDiscountEvent.class);

        // Furni Editor
        this.registerHandler(Incoming.FurniEditorSearchEvent, FurniEditorSearchEvent.class);
        this.registerHandler(Incoming.FurniEditorDetailEvent, FurniEditorDetailEvent.class);
        this.registerHandler(Incoming.FurniEditorBySpriteEvent, FurniEditorBySpriteEvent.class);
        this.registerHandler(Incoming.FurniEditorInteractionsEvent, FurniEditorInteractionsEvent.class);
        this.registerHandler(Incoming.FurniEditorUpdateEvent, FurniEditorUpdateEvent.class);
        this.registerHandler(Incoming.FurniEditorDeleteEvent, FurniEditorDeleteEvent.class);
        this.registerHandler(Incoming.FurniEditorUpdateFurnidataEvent, FurniEditorUpdateFurnidataEvent.class);
        this.registerHandler(Incoming.FurniEditorRevertFurnidataEvent, FurniEditorRevertFurnidataEvent.class);
        this.registerHandler(Incoming.FurniEditorImportTextEvent, FurniEditorImportTextEvent.class);

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
        this.registerHandler(Incoming.CatalogAdminLoadOfferEvent, CatalogAdminLoadOfferEvent.class);
        this.registerHandler(Incoming.CatalogAdminLoadPageEvent, CatalogAdminLoadPageEvent.class);
        this.registerHandler(Incoming.CatalogAdminSetPageEnabledEvent, CatalogAdminSetPageEnabledEvent.class);
        this.registerHandler(Incoming.CatalogAdminSetPageVisibleEvent, CatalogAdminSetPageVisibleEvent.class);
        this.registerHandler(Incoming.CatalogAdminReorderOffersEvent, CatalogAdminReorderOffersEvent.class);
        this.registerHandler(Incoming.CatalogStudioOpenSessionEvent, CatalogStudioOpenSessionEvent.class);
        this.registerHandler(Incoming.CatalogStudioLoadHistoryEvent, CatalogStudioLoadHistoryEvent.class);
        this.registerHandler(Incoming.CatalogStudioUndoEvent, CatalogStudioUndoEvent.class);
        this.registerHandler(Incoming.CatalogStudioValidateEvent, CatalogStudioValidateEvent.class);
        this.registerHandler(Incoming.CatalogStudioExportEvent, CatalogStudioExportEvent.class);
        this.registerHandler(Incoming.CatalogStudioDocumentDryRunEvent, CatalogStudioDocumentDryRunEvent.class);
        this.registerHandler(Incoming.CatalogStudioDocumentApplyEvent, CatalogStudioDocumentApplyEvent.class);
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
        this.registerHandler(Incoming.GetIgnoredUsersEvent, GetIgnoredUsersEvent.class);
        this.registerHandler(Incoming.GetBlockedUsersEvent, GetBlockedUsersEvent.class);
        this.registerHandler(Incoming.BlockUserEvent, BlockUserEvent.class);
        this.registerHandler(Incoming.UnblockUserEvent, UnblockUserEvent.class);
        this.registerHandler(Incoming.ReplenishRespectEvent, ReplenishRespectEvent.class);
        this.registerHandler(Incoming.ActivateNotificationsEvent, ActivateNotificationsEvent.class);
        this.registerHandler(Incoming.UnmuteUserEvent, UnmuteUserEvent.class);
        this.registerHandler(Incoming.GetDiscordPreferencesEvent, GetDiscordPreferencesEvent.class);
        this.registerHandler(Incoming.UpdateDiscordPreferencesEvent, UpdateDiscordPreferencesEvent.class);
        this.registerHandler(Incoming.PingEvent, PingEvent.class);
        this.registerHandler(Incoming.DisconnectEvent, DisconnectEvent.class);
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
        this.registerHandler(Incoming.RequestOfflineMessagesEvent, RequestOfflineMessagesEvent.class);
        this.registerHandler(Incoming.RequestFriendRequestEvent, RequestFriendRequestsEvent.class);
        this.registerHandler(Incoming.StalkFriendEvent, StalkFriendEvent.class);
        this.registerHandler(Incoming.RequestInitFriendsEvent, RequestInitFriendsEvent.class);
        this.registerHandler(Incoming.FindNewFriendsEvent, FindNewFriendsEvent.class);
        this.registerHandler(Incoming.InviteFriendsEvent, InviteFriendsEvent.class);
        this.registerHandler(Incoming.RequestMessengerConversationsEvent, RequestMessengerConversationsEvent.class);
        this.registerHandler(Incoming.RequestMessengerHistoryEvent, RequestMessengerHistoryEvent.class);
        this.registerHandler(Incoming.SendMessengerMessageEvent, SendMessengerMessageEvent.class);
        this.registerHandler(Incoming.MarkMessengerReadEvent, MarkMessengerReadEvent.class);
        this.registerHandler(Incoming.MarkConsoleReadEvent, MarkConsoleReadEvent.class);
        this.registerHandler(Incoming.ConsoleTypingEvent, ConsoleTypingEvent.class);
        this.registerHandler(Incoming.RefreshFriendListEvent, RefreshFriendListEvent.class);
        this.registerHandler(Incoming.AddFriendCategoryEvent, AddFriendCategoryEvent.class);
        this.registerHandler(Incoming.RenameFriendCategoryEvent, RenameFriendCategoryEvent.class);
        this.registerHandler(Incoming.RemoveFriendCategoryEvent, RemoveFriendCategoryEvent.class);
        this.registerHandler(Incoming.MoveFriendToCategoryEvent, MoveFriendToCategoryEvent.class);
    }

    private void registerUsers() throws Exception {
        this.registerHandler(Incoming.RequestUserDataEvent, RequestUserDataEvent.class);
        this.registerHandler(Incoming.RequestUserCreditsEvent, RequestUserCreditsEvent.class);
        this.registerHandler(Incoming.RequestUserClubEvent, RequestUserClubEvent.class);
        this.registerHandler(Incoming.RequestMeMenuSettingsEvent, RequestMeMenuSettingsEvent.class);
        this.registerHandler(Incoming.RequestUserCitizinShipEvent, RequestUserCitizinShipEvent.class);
        this.registerHandler(Incoming.GetEmailStatusEvent, GetEmailStatusEvent.class);
        this.registerHandler(Incoming.ChangeEmailEvent, ChangeEmailEvent.class);
        this.registerHandler(Incoming.RequestUserProfileEvent, RequestUserProfileEvent.class);
        this.registerHandler(Incoming.RequestProfileFriendsEvent, RequestProfileFriendsEvent.class);
        this.registerHandler(Incoming.RequestUserWardrobeEvent, RequestUserWardrobeEvent.class);
        this.registerHandler(Incoming.SaveWardrobeEvent, SaveWardrobeEvent.class);
        this.registerHandler(Incoming.SaveMottoEvent, SaveMottoEvent.class);
        this.registerHandler(Incoming.UserSaveLookEvent, UserSaveLookEvent.class);
        this.registerHandler(Incoming.UserWearBadgeEvent, UserWearBadgeEvent.class);
        this.registerHandler(Incoming.RequestWearingBadgesEvent, RequestWearingBadgesEvent.class);
        this.registerHandler(Incoming.GetBadgeInfoEvent, GetBadgeInfoEvent.class);
        this.registerHandler(Incoming.SaveUserVolumesEvent, SaveUserVolumesEvent.class);
        this.registerHandler(Incoming.SaveBlockCameraFollowEvent, SaveBlockCameraFollowEvent.class);
        this.registerHandler(Incoming.SaveIgnoreRoomInvitesEvent, SaveIgnoreRoomInvitesEvent.class);
        this.registerHandler(Incoming.SavePreferOldChatEvent, SavePreferOldChatEvent.class);
        this.registerHandler(Incoming.SaveGamePrivacySettingsEvent, SaveGamePrivacySettingsEvent.class);
        this.registerHandler(Incoming.SaveChatPreferencesEvent, SaveChatPreferencesEvent.class);
        this.registerHandler(Incoming.SaveOnlineIndicatorPreferenceEvent, SaveOnlineIndicatorPreferenceEvent.class);
        this.registerHandler(Incoming.SaveWiredMenuSettingsEvent, SaveWiredMenuSettingsEvent.class);
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
        this.registerHandler(Incoming.GetUnreadForumsCountEvent, GetUnreadForumsCountEvent.class);
        this.registerHandler(Incoming.RequestPublicRoomsEvent, RequestPublicRoomsEvent.class);
        this.registerHandler(Incoming.RoomNetworkOpenConnectionEvent, RoomNetworkOpenConnectionEvent.class);
        this.registerHandler(Incoming.RequestCreateRoomEvent, RequestCreateRoomEvent.class);
        this.registerHandler(Incoming.RequestTagsEvent, RequestTagsEvent.class);
        this.registerHandler(UnsupportedIncoming.SearchRoomsByTagEvent, SearchRoomsByTagEvent.class);
        this.registerHandler(Incoming.SearchRoomsEvent, SearchRoomsEvent.class);
        this.registerHandler(Incoming.SearchRoomsFriendsNowEvent, SearchRoomsFriendsNowEvent.class);
        this.registerHandler(Incoming.SearchRoomsFriendsOwnEvent, SearchRoomsFriendsOwnEvent.class);
        this.registerHandler(Incoming.SearchRoomsWithRightsEvent, SearchRoomsWithRightsEvent.class);
        this.registerHandler(Incoming.SearchRoomsInGroupEvent, SearchRoomsInGroupEvent.class);
        this.registerHandler(Incoming.SearchRoomsMyFavoriteEvent, SearchRoomsMyFavouriteEvent.class);
        this.registerHandler(Incoming.SearchRoomsVisitedEvent, SearchRoomsVisitedEvent.class);
        this.registerHandler(Incoming.RequestNewNavigatorDataEvent, RequestNewNavigatorDataEvent.class);
        this.registerHandler(Incoming.GetCategoriesWithUserCountEvent, GetCategoriesWithUserCountEvent.class);
        this.registerHandler(Incoming.RequestNewNavigatorRoomsEvent, RequestNewNavigatorRoomsEvent.class);
        this.registerHandler(Incoming.NewNavigatorActionEvent, NewNavigatorActionEvent.class);
        this.registerHandler(Incoming.RequestNavigatorSettingsEvent, RequestNavigatorSettingsEvent.class);
        this.registerHandler(Incoming.SaveWindowSettingsEvent, SaveWindowSettingsEvent.class);
        this.registerHandler(Incoming.RequestDeleteRoomEvent, RequestDeleteRoomEvent.class);
        this.registerHandler(Incoming.NavigatorCategoryListModeEvent, NavigatorCategoryListModeEvent.class);
        this.registerHandler(Incoming.NavigatorCollapseCategoryEvent, NavigatorCollapseCategoryEvent.class);
        this.registerHandler(Incoming.NavigatorUncollapseCategoryEvent, NavigatorUncollapseCategoryEvent.class);
        this.registerHandler(Incoming.AddSavedSearchEvent, AddSavedSearchEvent.class);
        this.registerHandler(Incoming.ConvertGlobalRoomIdEvent, ConvertGlobalRoomIdEvent.class);
        this.registerHandler(Incoming.DeleteSavedSearchEvent, DeleteSavedSearchEvent.class);
    }

    private void registerHotelview() throws Exception {
        this.registerHandler(Incoming.HotelViewEvent, HotelViewEvent.class);
        this.registerHandler(Incoming.HotelViewRequestBonusRareEvent, HotelViewRequestBonusRareEvent.class);
        this.registerHandler(Incoming.RequestNewsListEvent, RequestNewsListEvent.class);
        this.registerHandler(Incoming.HotelViewDataEvent, HotelViewDataEvent.class);
        this.registerHandler(Incoming.HotelViewRequestBadgeRewardEvent, HotelViewRequestBadgeRewardEvent.class);
        this.registerHandler(Incoming.HotelViewClaimBadgeEvent, HotelViewClaimBadgeEvent.class);
        this.registerHandler(Incoming.GetIsBadgeRequestFulfilledEvent, GetIsBadgeRequestFulfilledEvent.class);
        this.registerHandler(UnsupportedIncoming.HotelViewClaimBadgeRewardEvent, HotelViewClaimBadgeRewardEvent.class);
        this.registerHandler(Incoming.HotelViewRequestLTDAvailabilityEvent, HotelViewRequestLTDAvailabilityEvent.class);
        this.registerHandler(Incoming.HotelViewRequestSecondsUntilEvent, HotelViewRequestSecondsUntilEvent.class);
        this.registerHandler(Incoming.HotelViewLandingRequestEvent, HotelViewLandingRequestEvent.class);
        this.registerHandler(Incoming.HotelViewLandingSaveEvent, HotelViewLandingSaveEvent.class);
        this.registerHandler(Incoming.HotelViewLandingSaveSceneEvent, HotelViewLandingSaveSceneEvent.class);
        this.registerHandler(Incoming.HotelViewLandingVoteEvent, HotelViewLandingVoteEvent.class);
        this.registerHandler(Incoming.HotelViewLandingResetVotesEvent, HotelViewLandingResetVotesEvent.class);
        this.registerHandler(Incoming.HotelViewRequestCommunityGoalEvent, HotelViewRequestCommunityGoalEvent.class);
        this.registerHandler(Incoming.CommunityGoalVoteEvent, CommunityGoalVoteEvent.class);
        this.registerHandler(Incoming.HotelViewRequestConcurrentUsersEvent, HotelViewRequestConcurrentUsersEvent.class);
        this.registerHandler(Incoming.HotelViewConcurrentUsersButtonEvent, HotelViewConcurrentUsersButtonEvent.class);
        this.registerHandler(Incoming.GetCommunityGoalHallOfFameEvent, GetCommunityGoalHallOfFameEvent.class);
        this.registerHandler(Incoming.GetCommunityGoalEarnedPrizesEvent, GetCommunityGoalEarnedPrizesEvent.class);
        this.registerHandler(Incoming.RedeemCommunityGoalPrizeEvent, RedeemCommunityGoalPrizeEvent.class);
        this.registerHandler(Incoming.SelfDonationEvent, SelfDonationEvent.class);
    }

    private void registerInventory() throws Exception {
        this.registerHandler(Incoming.RequestInventoryBadgesEvent, RequestInventoryBadgesEvent.class);
        this.registerHandler(Incoming.UnseenResetCategoryEvent, UnseenResetCategoryEvent.class);
        this.registerHandler(Incoming.UnseenResetItemsEvent, UnseenResetItemsEvent.class);
        this.registerHandler(Incoming.RequestInventoryBotsEvent, RequestInventoryBotsEvent.class);
        this.registerHandler(Incoming.RequestInventoryItemsDelete, RequestInventoryItemsDelete.class);
        this.registerHandler(Incoming.RequestInventoryItemsEvent, RequestInventoryItemsEvent.class);
        this.registerHandler(Incoming.HotelViewInventoryEvent, HotelViewInventoryEvent.class);
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
        this.registerHandler(Incoming.RequestMentionsPageEvent, RequestMentionsPageEvent.class);
        this.registerHandler(Incoming.RequestRoomLoadEvent, RequestRoomLoadEvent.class);
        this.registerHandler(Incoming.RequestHeightmapEvent, RequestRoomHeightmapEvent.class);
        this.registerHandler(Incoming.RequestRoomHeightmapEvent, RequestRoomHeightmapEvent.class);
        this.registerHandler(Incoming.RoomVoteEvent, RoomVoteEvent.class);
        this.registerHandler(Incoming.RequestRoomDataEvent, RequestRoomDataEvent.class);
        this.registerHandler(Incoming.RoomSettingsSaveEvent, RoomSettingsSaveEvent.class);
        this.registerHandler(
                Incoming.UpdateRoomCategoryAndTradeSettingsEvent, UpdateRoomCategoryAndTradeSettingsEvent.class);
        this.registerHandler(Incoming.GetQuizQuestionsEvent, GetQuizQuestionsEvent.class);
        this.registerHandler(Incoming.PostQuizAnswersEvent, PostQuizAnswersEvent.class);
        this.registerHandler(Incoming.RoomPlaceItemEvent, RoomPlaceItemEvent.class);
        this.registerHandler(Incoming.RotateMoveItemEvent, RotateMoveItemEvent.class);
        this.registerHandler(Incoming.MoveWallItemEvent, MoveWallItemEvent.class);
        this.registerHandler(Incoming.RoomPickupItemEvent, RoomPickupItemEvent.class);
        this.registerHandler(Incoming.RoomPickupChooserEvent, RoomPickupChooserEvent.class);
        this.registerHandler(Incoming.RoomPlacePaintEvent, RoomPlacePaintEvent.class);
        this.registerHandler(Incoming.RoomRemoveBackgroundEvent, RoomRemoveBackgroundEvent.class);
        this.registerHandler(Incoming.RoomRemovePaintEvent, RoomRemovePaintEvent.class);
        this.registerHandler(Incoming.SetBuildUnderpassEvent, SetBuildUnderpassEvent.class);
        this.registerHandler(Incoming.SetBuildHeightEvent, SetBuildHeightEvent.class);
        this.registerHandler(Incoming.OpenMysteryTrophyEvent, OpenMysteryTrophyEvent.class);
        this.registerHandler(Incoming.MysteryBoxWaitingCanceledEvent, MysteryBoxWaitingCanceledEvent.class);
        this.registerHandler(Incoming.ForwardToACompetitionRoomEvent, ForwardToACompetitionRoomEvent.class);
        this.registerHandler(Incoming.ForwardToASubmittableRoomEvent, ForwardToASubmittableRoomEvent.class);
        this.registerHandler(Incoming.ForwardToRandomCompetitionRoomEvent, ForwardToRandomCompetitionRoomEvent.class);
        this.registerHandler(Incoming.GetIsUserPartOfCompetitionEvent, GetIsUserPartOfCompetitionEvent.class);
        this.registerHandler(Incoming.RoomCompetitionInitEvent, RoomCompetitionInitEvent.class);
        this.registerHandler(Incoming.SubmitRoomToCompetitionEvent, SubmitRoomToCompetitionEvent.class);
        this.registerHandler(Incoming.VoteForRoomEvent, VoteForRoomEvent.class);
        this.registerHandler(Incoming.CompetitionRoomsSearchEvent, CompetitionRoomsSearchEvent.class);
        this.registerHandler(Incoming.RoomUserStartTypingEvent, RoomUserStartTypingEvent.class);
        this.registerHandler(Incoming.RoomUserStopTypingEvent, RoomUserStopTypingEvent.class);
        this.registerHandler(Incoming.ClickFurniEvent, ClickFurniEvent.class);
        this.registerHandler(Incoming.ClickUserEvent, ClickUserEvent.class);
        this.registerHandler(Incoming.ToggleFloorItemEvent, ToggleFloorItemEvent.class);
        this.registerHandler(Incoming.ToggleWallItemEvent, ToggleWallItemEvent.class);
        // AIR chest model: deposits only go through the wired trade (ChestStartDepositEvent), and the
        // Wired upgrade is part of the settings save, so the wallet/inventory deposit messages and
        // ChestEnableWiredEvent are no longer registered.
        this.registerHandler(Incoming.ChestWithdrawEvent, ChestWithdrawEvent.class);
        this.registerHandler(Incoming.ChestWithdrawFurniEvent, ChestWithdrawFurniEvent.class);
        this.registerHandler(Incoming.ChestStartDepositEvent, ChestStartDepositEvent.class);
        this.registerHandler(Incoming.ChestWithdrawAllFurniEvent, ChestWithdrawAllFurniEvent.class);
        this.registerHandler(Incoming.ChestOpenEvent, ChestOpenEvent.class);
        this.registerHandler(Incoming.ChestSaveSettingsEvent, ChestSaveSettingsEvent.class);
        this.registerHandler(Incoming.ChestSaveNotificationsEvent, ChestSaveNotificationsEvent.class);
        this.registerHandler(Incoming.ChestSaveOptionsEvent, ChestSaveOptionsEvent.class);
        this.registerHandler(Incoming.ChestCloseEvent, ChestCloseEvent.class);
        this.registerHandler(Incoming.ChestUpgradeCapacityEvent, ChestUpgradeCapacityEvent.class);
        this.registerHandler(Incoming.ChestRequestLogEvent, ChestRequestLogEvent.class);
        this.registerHandler(Incoming.WiredChestRoomLogsEvent, WiredChestRoomLogsEvent.class);
        this.registerHandler(Incoming.WiredChestLockEvent, WiredChestLockEvent.class);
        this.registerHandler(Incoming.WiredTradeOfferItemsEvent, WiredTradeOfferItemsEvent.class);
        this.registerHandler(Incoming.WiredTradeAcceptEvent, WiredTradeAcceptEvent.class);
        this.registerHandler(Incoming.WiredTradeCancelEvent, WiredTradeCancelEvent.class);
        this.registerHandler(Incoming.WiredChestTransactionDetailsEvent, WiredChestTransactionDetailsEvent.class);
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
        this.registerHandler(Incoming.TriggerHabbiconEvent, TriggerHabbiconEvent.class);
        this.registerHandler(Incoming.GetHabbiconShopDataEvent, HabbiconRequestEvent.class);
        this.registerHandler(Incoming.GetHabbiconInfoEvent, HabbiconRequestEvent.class);
        this.registerHandler(Incoming.BuyHabbiconEvent, HabbiconRequestEvent.class);
        this.registerHandler(Incoming.BuyHabbiconCollectionEvent, HabbiconRequestEvent.class);
        this.registerHandler(Incoming.ClaimHabbiconEvent, HabbiconRequestEvent.class);
        this.registerHandler(Incoming.FavoriteHabbiconEvent, HabbiconRequestEvent.class);
        this.registerHandler(Incoming.UnfavoriteHabbiconEvent, HabbiconRequestEvent.class);
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
        this.registerHandler(Incoming.PressKeybindEvent, polaris.messages.incoming.rooms.items.PressKeybindEvent.class);
        this.registerHandler(Incoming.CloseDiceEvent, CloseDiceEvent.class);
        this.registerHandler(Incoming.TriggerColorWheelEvent, TriggerColorWheelEvent.class);
        this.registerHandler(Incoming.RedeemItemEvent, RedeemItemEvent.class);
        this.registerHandler(Incoming.PetPlaceEvent, PetPlaceEvent.class);
        this.registerHandler(Incoming.RoomUserKickEvent, RoomUserKickEvent.class);
        this.registerHandler(Incoming.SetStackHelperHeightEvent, SetStackHelperHeightEvent.class);
        this.registerHandler(Incoming.SetStackHelperAdjacentHeightEvent, SetStackHelperAdjacentHeightEvent.class);
        this.registerHandler(Incoming.TriggerOneWayGateEvent, TriggerOneWayGateEvent.class);
        this.registerHandler(Incoming.HandleDoorbellEvent, HandleDoorbellEvent.class);
        this.registerHandler(Incoming.ChangeQueueEvent, ChangeQueueEvent.class);
        this.registerHandler(Incoming.RedeemClothingEvent, RedeemClothingEvent.class);
        this.registerHandler(Incoming.PostItPlaceEvent, PostItPlaceEvent.class);
        this.registerHandler(Incoming.PostItRequestDataEvent, PostItRequestDataEvent.class);
        this.registerHandler(Incoming.PostItSaveDataEvent, PostItSaveDataEvent.class);
        this.registerHandler(Incoming.PostItDeleteEvent, PostItDeleteEvent.class);
        this.registerHandler(Incoming.MoodLightSaveSettingsEvent, MoodLightSaveSettingsEvent.class);
        this.registerHandler(Incoming.RentSpaceEvent, RentSpaceEvent.class);
        this.registerHandler(Incoming.RentSpaceCancelEvent, RentSpaceCancelEvent.class);
        this.registerHandler(Incoming.GetRentableSpaceStatusEvent, GetRentableSpaceStatusEvent.class);
        this.registerHandler(Incoming.GetRentOrBuyoutOfferEvent, GetRentOrBuyoutOfferEvent.class);
        this.registerHandler(Incoming.ExtendRentOrBuyoutFurniEvent, ExtendRentOrBuyoutFurniEvent.class);
        this.registerHandler(Incoming.ExtendRentOrBuyoutStripItemEvent, ExtendRentOrBuyoutStripItemEvent.class);
        this.registerHandler(Incoming.SetHomeRoomEvent, SetHomeRoomEvent.class);
        this.registerHandler(Incoming.RoomUserGiveHandItemEvent, RoomUserGiveHandItemEvent.class);
        this.registerHandler(Incoming.RoomUserGiveHandItemPetEvent, RoomUserGiveHandItemPetEvent.class);
        this.registerHandler(Incoming.RoomMuteEvent, RoomMuteEvent.class);
        this.registerHandler(Incoming.RequestRoomWordFilterEvent, RequestRoomWordFilterEvent.class);
        this.registerHandler(Incoming.RoomWordFilterModifyEvent, RoomWordFilterModifyEvent.class);
        this.registerHandler(Incoming.RequestCustomWordFilterEvent, RequestCustomWordFilterEvent.class);
        this.registerHandler(Incoming.AddCustomWordFilterWordEvent, AddCustomWordFilterWordEvent.class);
        this.registerHandler(Incoming.RemoveCustomWordFilterWordEvent, RemoveCustomWordFilterWordEvent.class);
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
        this.registerHandler(Incoming.CancelRoomPromotionEvent, CancelRoomPromotionEvent.class);
        this.registerHandler(Incoming.GetRaidProtectionSettingsEvent, GetRaidProtectionSettingsEvent.class);
        this.registerHandler(Incoming.SaveRaidProtectionSettingsEvent, SaveRaidProtectionSettingsEvent.class);
        this.registerHandler(Incoming.IgnoreRoomUserEvent, IgnoreRoomUserEvent.class);
        this.registerHandler(Incoming.IgnoreUserIdEvent, IgnoreUserIdEvent.class);
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
        this.registerHandler(UnsupportedIncoming.ModToolWarnEvent, ModToolWarnEvent.class);
        this.registerHandler(Incoming.ModToolKickEvent, ModToolKickEvent.class);
        this.registerHandler(Incoming.ModToolRoomAlertEvent, ModToolRoomAlertEvent.class);
        this.registerHandler(Incoming.ModToolChangeRoomSettingsEvent, ModToolChangeRoomSettingsEvent.class);
        this.registerHandler(Incoming.ModToolRequestRoomVisitsEvent, ModToolRequestRoomVisitsEvent.class);
        this.registerHandler(Incoming.ModToolRequestIssueChatlogEvent, ModToolRequestIssueChatlogEvent.class);
        this.registerHandler(
                UnsupportedIncoming.ModToolRequestRoomUserChatlogEvent, ModToolRequestRoomUserChatlogEvent.class);
        this.registerHandler(Incoming.ModToolRequestUserChatlogEvent, ModToolRequestUserChatlogEvent.class);
        this.registerHandler(Incoming.ModToolSanctionAlertEvent, ModToolSanctionAlertEvent.class);
        this.registerHandler(Incoming.ModToolSanctionMuteEvent, ModToolSanctionMuteEvent.class);
        this.registerHandler(Incoming.ModToolSanctionBanEvent, ModToolSanctionBanEvent.class);
        this.registerHandler(Incoming.ModToolSanctionTradeLockEvent, ModToolSanctionTradeLockEvent.class);
        this.registerHandler(Incoming.ModToolIssueChangeTopicEvent, ModToolIssueChangeTopicEvent.class);
        this.registerHandler(Incoming.ModToolDefaultSanctionEvent, ModToolDefaultSanctionEvent.class);
        this.registerHandler(Incoming.ModToolPreferencesEvent, ModToolPreferencesEvent.class);
        this.registerHandler(Incoming.ModToolIssueDefaultSanctionEvent, ModToolIssueDefaultSanctionEvent.class);

        this.registerHandler(Incoming.RequestReportRoomEvent, RequestReportRoomEvent.class);
        this.registerHandler(Incoming.DeletePendingCallsForHelpEvent, DeletePendingCallsForHelpEvent.class);
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
        this.registerHandler(Incoming.GuildUnblockMemberEvent, GuildUnblockMemberEvent.class);
        this.registerHandler(Incoming.GuildChangeSettingsEvent, GuildChangeSettingsEvent.class);
        this.registerHandler(Incoming.GuildAcceptMembershipEvent, GuildAcceptMembershipEvent.class);
        this.registerHandler(Incoming.GuildAcceptAllMembershipsEvent, GuildAcceptAllMembershipsEvent.class);
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
        this.registerHandler(Incoming.GetUserGuildBadgesMessageEvent, GetUserGuildBadgesMessageEvent.class);

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
        this.registerHandler(Incoming.PetSupplementEvent, PetSupplementEvent.class);
        // The official client (AIR 13) sends the same composer on 2868.
        this.registerHandler(Incoming.PetSupplementOfficialEvent, PetSupplementEvent.class);
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
        this.registerHandler(Incoming.WiredAddonSaveDataEvent, WiredAddonSaveDataEvent.class);
        this.registerHandler(Incoming.WiredSelectorSaveDataEvent, WiredSelectorSaveDataEvent.class);
        this.registerHandler(Incoming.WiredVariableSaveDataEvent, WiredVariableSaveDataEvent.class);
        this.registerHandler(Incoming.WiredConditionSaveDataEvent, WiredConditionSaveDataEvent.class);
        this.registerHandler(Incoming.WiredApplySetConditionsEvent, WiredApplySetConditionsEvent.class);
        this.registerHandler(Incoming.WiredMonitorRequestEvent, WiredMonitorRequestEvent.class);
        this.registerHandler(Incoming.WiredRoomSettingsRequestEvent, WiredRoomSettingsRequestEvent.class);
        this.registerHandler(Incoming.WiredRoomSettingsSaveEvent, WiredRoomSettingsSaveEvent.class);
        this.registerHandler(Incoming.WiredUserVariablesRequestEvent, WiredUserVariablesRequestEvent.class);
        this.registerHandler(Incoming.WiredUserVariableUpdateEvent, WiredUserVariableUpdateEvent.class);
        this.registerHandler(Incoming.WiredUserVariableManageEvent, WiredUserVariableManageEvent.class);
        this.registerHandler(Incoming.WiredGenerateWebApiKeyEvent, WiredGenerateWebApiKeyEvent.class);
        this.registerHandler(Incoming.WiredUserInspectMoveEvent, WiredUserInspectMoveEvent.class);
        this.registerHandler(Incoming.WiredFurniRuntimeStateRequestEvent, WiredFurniRuntimeStateRequestEvent.class);
        this.registerHandler(Incoming.WiredFeatureCapabilitiesEvent, WiredFeatureCapabilitiesEvent.class);
        this.registerHandler(Incoming.WiredArrayInspectionRequestEvent, WiredArrayInspectionRequestEvent.class);
        this.registerHandler(Incoming.WiredArrayInspectionUpdateEvent, WiredArrayInspectionUpdateEvent.class);
        this.registerHandler(Incoming.WiredSetupExportEvent, WiredSetupExportEvent.class);
        this.registerHandler(Incoming.WiredSetupImportEvent, WiredSetupImportEvent.class);
        this.registerHandler(Incoming.WiredOpenEvent, WiredOpenEvent.class);
        this.registerHandler(Incoming.GetWiredContractContentsEvent, GetWiredContractContentsEvent.class);
        this.registerHandler(Incoming.UpdateWiredContractEvent, UpdateWiredContractEvent.class);
        this.registerHandler(Incoming.WiredUserSelectedEvent, WiredUserSelectedEvent.class);
        this.registerHandler(Incoming.WiredMenuPermissionsSaveEvent, WiredMenuPermissionsSaveEvent.class);
        this.registerHandler(Incoming.WiredRoomStateActionEvent, WiredRoomStateActionEvent.class);
        this.registerHandler(Incoming.WiredRoomLogsPageEvent, WiredRoomLogsPageEvent.class);
        this.registerHandler(Incoming.WiredRoomStatsRequestEvent, WiredRoomStatsRequestEvent.class);
        this.registerHandler(Incoming.WiredErrorLogsRequestEvent, WiredErrorLogsRequestEvent.class);
        this.registerHandler(Incoming.WiredErrorLogsClearEvent, WiredErrorLogsClearEvent.class);
        this.registerHandler(Incoming.WiredVariableHoldersPageEvent, WiredVariableHoldersPageEvent.class);
        this.registerHandler(Incoming.WiredLeaderboardSubscribeEvent, WiredLeaderboardSubscribeEvent.class);
        this.registerHandler(Incoming.WiredLinkRequestConfigEvent, WiredLinkRequestConfigEvent.class);
        this.registerHandler(Incoming.WiredLinkSaveSelectionEvent, WiredLinkSaveSelectionEvent.class);
        this.registerHandler(Incoming.WiredLinkCreateNetworkEvent, WiredLinkCreateNetworkEvent.class);
        this.registerHandler(Incoming.WiredLinkRenameNetworkEvent, WiredLinkRenameNetworkEvent.class);
        this.registerHandler(Incoming.WiredLinkDeleteNetworkEvent, WiredLinkDeleteNetworkEvent.class);
        this.registerHandler(Incoming.WiredLinkGetConnectionsEvent, WiredLinkGetConnectionsEvent.class);
        this.registerHandler(Incoming.WiredAddonShareSynchronizeEvent, WiredAddonShareSynchronizeEvent.class);
        this.registerHandler(Incoming.WiredLabelSaveTextEvent, WiredLabelSaveTextEvent.class);
        this.registerHandler(Incoming.WiredLabelSaveSettingsEvent, WiredLabelSaveSettingsEvent.class);
        this.registerHandler(Incoming.WiredVariableHoldersRequestEvent, WiredVariableHoldersRequestEvent.class);
        this.registerHandler(Incoming.WiredVariableHashesEvent, WiredVariableHashesEvent.class);
        this.registerHandler(Incoming.WiredAllVariablesRequestEvent, WiredAllVariablesRequestEvent.class);
        this.registerHandler(Incoming.WiredGetVariablesForObjectEvent, WiredGetVariablesForObjectEvent.class);
    }

    void registerTranslation() throws Exception {
        this.registerHandler(Incoming.TranslationLanguagesRequestEvent, TranslationLanguagesRequestEvent.class);
        this.registerHandler(Incoming.TranslationTextRequestEvent, TranslationTextRequestEvent.class);
    }

    void registerUnknown() throws Exception {
        this.registerHandler(Incoming.RequestResolutionEvent, RequestResolutionEvent.class);
        this.registerHandler(Incoming.ResetResolutionAchievementEvent, ResetResolutionAchievementEvent.class);
        this.registerHandler(Incoming.RequestTalenTrackEvent, RequestTalentTrackEvent.class);
        this.registerHandler(Incoming.UnknownEvent1, UnknownEvent1.class);
        this.registerHandler(Incoming.MySanctionStatusEvent, MySanctionStatusEvent.class);
        this.registerHandler(Incoming.GetMyReportsStatusEvent, GetMyReportsStatusEvent.class);
        this.registerHandler(Incoming.AppealReportEvent, AppealReportEvent.class);
    }

    void registerFloorPlanEditor() throws Exception {
        this.registerHandler(Incoming.FloorPlanEditorSaveEvent, FloorPlanEditorSaveEvent.class);
        this.registerHandler(
                Incoming.FloorPlanEditorRequestBlockedTilesEvent, FloorPlanEditorRequestBlockedTilesEvent.class);
        this.registerHandler(
                Incoming.FloorPlanEditorRequestDoorSettingsEvent, FloorPlanEditorRequestDoorSettingsEvent.class);
    }

    void registerAchievements() throws Exception {
        this.registerHandler(Incoming.RequestAchievementsEvent, RequestAchievementsEvent.class);
        this.registerHandler(
                UnsupportedIncoming.RequestAchievementConfigurationEvent, RequestAchievementConfigurationEvent.class);
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
        this.registerHandler(Incoming.PhotoCompetitionEvent, PhotoCompetitionEvent.class);
    }

    void registerSnowWar() throws Exception {
        // The UNKNOWN_SNOWSTORM_* constant names are frozen by the plugin ABI
        // gate (PluginAbiCompatibilityTest) and duplicate ids are rejected by
        // PacketNamesContractTest, so the legacy names stay canonical. The
        // handler class names document what each header actually is.
        this.registerHandler(
                Incoming.UNKNOWN_SNOWSTORM_6000, // load stage ready
                polaris.messages.incoming.snowwar.SnowStormLoadStageReadyEvent.class);
        this.registerHandler(
                Incoming.UNKNOWN_SNOWSTORM_6001, // exit game
                polaris.messages.incoming.snowwar.SnowStormExitGameEvent.class);
        this.registerHandler(
                Incoming.UNKNOWN_SNOWSTORM_6010, // open arena editor (6002 is taken by ClickFurniEvent)
                polaris.messages.incoming.snowwar.SnowStormEditRoomEvent.class);
        this.registerHandler(
                Incoming.UNKNOWN_SNOWSTORM_6003, // walk
                polaris.messages.incoming.snowwar.SnowStormWalkEvent.class);
        this.registerHandler(
                Incoming.UNKNOWN_SNOWSTORM_6004, // throw at location
                polaris.messages.incoming.snowwar.SnowStormThrowAtLocationEvent.class);
        this.registerHandler(
                Incoming.UNKNOWN_SNOWSTORM_6005, // throw at player
                polaris.messages.incoming.snowwar.SnowStormThrowAtPlayerEvent.class);
        this.registerHandler(
                Incoming.UNKNOWN_SNOWSTORM_6006, // create snowball
                polaris.messages.incoming.snowwar.SnowStormCreateSnowballEvent.class);
        this.registerHandler(
                Incoming.UNKNOWN_SNOWSTORM_6007, // request full game status
                polaris.messages.incoming.snowwar.SnowStormRequestFullGameStatusEvent.class);
        this.registerHandler(
                Incoming.UNKNOWN_SNOWSTORM_6008, // play again
                polaris.messages.incoming.snowwar.SnowStormPlayAgainEvent.class);
        this.registerHandler(
                Incoming.UNKNOWN_SNOWSTORM_6009, // game chat
                polaris.messages.incoming.snowwar.SnowStormGameChatEvent.class);
        this.registerHandler(
                Incoming.SnowStormJoinQueueEvent, polaris.messages.incoming.snowwar.SnowStormJoinQueueEvent.class);
        this.registerHandler(
                Incoming.UNKNOWN_SNOWSTORM_6013, // leave queue
                polaris.messages.incoming.snowwar.SnowStormLeaveQueueEvent.class);
        this.registerHandler(
                Incoming.UNKNOWN_SNOWSTORM_6011, // save in-game arena editor layout
                polaris.messages.incoming.snowwar.SnowStormSaveEditorEvent.class);
        this.registerHandler(
                Incoming.UNKNOWN_SNOWSTORM_6014, // close arena editor (release matchmaking lock)
                polaris.messages.incoming.snowwar.SnowStormExitEditorEvent.class);
        this.registerHandler(
                Incoming.UNKNOWN_SNOWSTORM_6015, // lobby leader selects the next arena
                polaris.messages.incoming.snowwar.SnowStormSelectArenaEvent.class);
        this.registerHandler(
                Incoming.SnowStormGetAllTimeLeaderboardEvent,
                polaris.messages.incoming.snowwar.SnowStormGetAllTimeLeaderboardEvent.class);
        this.registerHandler(
                Incoming.SnowStormGetAllTimeFriendsLeaderboardEvent,
                polaris.messages.incoming.snowwar.SnowStormGetAllTimeFriendsLeaderboardEvent.class);
        this.registerHandler(
                Incoming.SnowStormGetWeeklyLeaderboardEvent,
                polaris.messages.incoming.snowwar.SnowStormGetWeeklyLeaderboardEvent.class);
        this.registerHandler(
                Incoming.SnowStormGetWeeklyFriendsLeaderboardEvent,
                polaris.messages.incoming.snowwar.SnowStormGetWeeklyFriendsLeaderboardEvent.class);
        this.registerHandler(
                Incoming.SnowStormGetTotalGroupLeaderboardEvent,
                polaris.messages.incoming.snowwar.SnowStormGetTotalGroupLeaderboardEvent.class);
        this.registerHandler(
                Incoming.SnowStormGetWeeklyGroupLeaderboardEvent,
                polaris.messages.incoming.snowwar.SnowStormGetWeeklyGroupLeaderboardEvent.class);
    }

    void registerGameCenter() throws Exception {
        this.registerHandler(Incoming.GameCenterRequestGamesEvent, GameCenterRequestGamesEvent.class);
        this.registerHandler(Incoming.GameCenterRequestAccountStatusEvent, GameCenterRequestAccountStatusEvent.class);
        this.registerHandler(Incoming.GetSnowWarGameTokensOfferEvent, GetSnowWarGameTokensOfferEvent.class);
        this.registerHandler(Incoming.PurchaseSnowWarGameTokensOfferEvent, PurchaseSnowWarGameTokensOfferEvent.class);
        this.registerHandler(Incoming.GameCenterJoinGameEvent, GameCenterJoinGameEvent.class);
        this.registerHandler(Incoming.GameCenterLoadGameEvent, GameCenterLoadGameEvent.class);
        this.registerHandler(Incoming.GameCenterLeaveGameEvent, GameCenterLeaveGameEvent.class);
        this.registerHandler(Incoming.GameCenterCheckDirectoryStatusEvent, GameCenterCheckDirectoryStatusEvent.class);
        this.registerHandler(Incoming.GameCenterEvent, GameCenterEvent.class);
        this.registerHandler(Incoming.GameCenterRequestGameStatusEvent, GameCenterRequestGameStatusEvent.class);

        // YouTube Room Broadcast
        this.registerHandler(
                Incoming.YouTubeRoomPlayEvent, polaris.messages.incoming.rooms.youtube.YouTubeRoomPlayEvent.class);
        this.registerHandler(
                Incoming.YouTubeRoomWatchingEvent,
                polaris.messages.incoming.rooms.youtube.YouTubeRoomWatchingEvent.class);
        this.registerHandler(
                Incoming.YouTubeRoomSettingsEvent,
                polaris.messages.incoming.rooms.youtube.YouTubeRoomSettingsEvent.class);

        // Housekeeping (in-client admin panel)
        this.registerHandler(
                Incoming.HousekeepingFindUserByNameEvent,
                polaris.messages.incoming.housekeeping.HousekeepingFindUserByNameEvent.class);
        this.registerHandler(
                Incoming.HousekeepingFindUserByIdEvent,
                polaris.messages.incoming.housekeeping.HousekeepingFindUserByIdEvent.class);
        this.registerHandler(
                Incoming.HousekeepingBanUserEvent,
                polaris.messages.incoming.housekeeping.HousekeepingBanUserEvent.class);
        this.registerHandler(
                Incoming.HousekeepingUnbanUserEvent,
                polaris.messages.incoming.housekeeping.HousekeepingUnbanUserEvent.class);
        this.registerHandler(
                Incoming.HousekeepingMuteUserEvent,
                polaris.messages.incoming.housekeeping.HousekeepingMuteUserEvent.class);
        this.registerHandler(
                Incoming.HousekeepingKickUserEvent,
                polaris.messages.incoming.housekeeping.HousekeepingKickUserEvent.class);
        this.registerHandler(
                Incoming.HousekeepingForceDisconnectUserEvent,
                polaris.messages.incoming.housekeeping.HousekeepingForceDisconnectUserEvent.class);
        this.registerHandler(
                Incoming.HousekeepingSetUserRankEvent,
                polaris.messages.incoming.housekeeping.HousekeepingSetUserRankEvent.class);
        this.registerHandler(
                Incoming.HousekeepingTradeLockUserEvent,
                polaris.messages.incoming.housekeeping.HousekeepingTradeLockUserEvent.class);
        this.registerHandler(
                Incoming.HousekeepingResetUserPasswordEvent,
                polaris.messages.incoming.housekeeping.HousekeepingResetUserPasswordEvent.class);
        this.registerHandler(
                Incoming.HousekeepingFindRoomByIdEvent,
                polaris.messages.incoming.housekeeping.HousekeepingFindRoomByIdEvent.class);
        this.registerHandler(
                Incoming.HousekeepingSearchRoomsEvent,
                polaris.messages.incoming.housekeeping.HousekeepingSearchRoomsEvent.class);
        this.registerHandler(
                Incoming.HousekeepingRoomStateEvent,
                polaris.messages.incoming.housekeeping.HousekeepingRoomStateEvent.class);
        this.registerHandler(
                Incoming.HousekeepingMuteRoomEvent,
                polaris.messages.incoming.housekeeping.HousekeepingMuteRoomEvent.class);
        this.registerHandler(
                Incoming.HousekeepingKickAllFromRoomEvent,
                polaris.messages.incoming.housekeeping.HousekeepingKickAllFromRoomEvent.class);
        this.registerHandler(
                Incoming.HousekeepingTransferRoomOwnershipEvent,
                polaris.messages.incoming.housekeeping.HousekeepingTransferRoomOwnershipEvent.class);
        this.registerHandler(
                Incoming.HousekeepingDeleteRoomEvent,
                polaris.messages.incoming.housekeeping.HousekeepingDeleteRoomEvent.class);
        this.registerHandler(
                Incoming.HousekeepingGiveCreditsEvent,
                polaris.messages.incoming.housekeeping.HousekeepingGiveCreditsEvent.class);
        this.registerHandler(
                Incoming.HousekeepingGiveCurrencyEvent,
                polaris.messages.incoming.housekeeping.HousekeepingGiveCurrencyEvent.class);
        this.registerHandler(
                Incoming.HousekeepingGrantItemEvent,
                polaris.messages.incoming.housekeeping.HousekeepingGrantItemEvent.class);
        this.registerHandler(
                Incoming.HousekeepingSetHcSubscriptionEvent,
                polaris.messages.incoming.housekeeping.HousekeepingSetHcSubscriptionEvent.class);
        this.registerHandler(
                Incoming.HousekeepingSendHotelAlertEvent,
                polaris.messages.incoming.housekeeping.HousekeepingSendHotelAlertEvent.class);
        this.registerHandler(
                Incoming.HousekeepingGetDashboardEvent,
                polaris.messages.incoming.housekeeping.HousekeepingGetDashboardEvent.class);
        this.registerHandler(
                Incoming.HousekeepingListActionLogEvent,
                polaris.messages.incoming.housekeeping.HousekeepingListActionLogEvent.class);
        this.registerHandler(
                Incoming.HousekeepingSaveRoomSettingsEvent,
                polaris.messages.incoming.housekeeping.HousekeepingSaveRoomSettingsEvent.class);
        this.registerHandler(
                Incoming.HousekeepingRequestListEvent,
                polaris.messages.incoming.housekeeping.HousekeepingRequestListEvent.class);
        this.registerHandler(
                Incoming.HousekeepingReloadEvent, polaris.messages.incoming.housekeeping.HousekeepingReloadEvent.class);
        this.registerHandler(
                Incoming.HousekeepingRevokeBanEvent,
                polaris.messages.incoming.housekeeping.HousekeepingRevokeBanEvent.class);
        this.registerHandler(
                Incoming.HousekeepingMaintenanceEvent,
                polaris.messages.incoming.housekeeping.HousekeepingMaintenanceEvent.class);
        this.registerHandler(
                Incoming.HousekeepingWordFilterEvent,
                polaris.messages.incoming.housekeeping.HousekeepingWordFilterEvent.class);
        this.registerHandler(
                Incoming.HousekeepingSetPermissionEvent,
                polaris.messages.incoming.housekeeping.HousekeepingSetPermissionEvent.class);
        this.registerHandler(
                Incoming.HousekeepingUserNoteEvent,
                polaris.messages.incoming.housekeeping.HousekeepingUserNoteEvent.class);
        this.registerHandler(
                Incoming.HousekeepingLockdownEvent,
                polaris.messages.incoming.housekeeping.HousekeepingLockdownEvent.class);

        this.registerHandler(
                Incoming.RequestRareValuesEvent, polaris.messages.incoming.rarevalues.RequestRareValuesEvent.class);
        this.registerHandler(Incoming.GetHotLooksEvent, polaris.messages.incoming.hotlooks.GetHotLooksEvent.class);

        this.registerHandler(Incoming.WheelOpenEvent, polaris.messages.incoming.wheel.WheelOpenEvent.class);
        this.registerHandler(Incoming.WheelSpinEvent, polaris.messages.incoming.wheel.WheelSpinEvent.class);
        this.registerHandler(Incoming.WheelBuySpinEvent, polaris.messages.incoming.wheel.WheelBuySpinEvent.class);
        this.registerHandler(
                Incoming.WheelAdminGetPrizesEvent, polaris.messages.incoming.wheel.WheelAdminGetPrizesEvent.class);
        this.registerHandler(
                Incoming.WheelAdminSavePrizesEvent, polaris.messages.incoming.wheel.WheelAdminSavePrizesEvent.class);

        this.registerHandler(
                Incoming.SoundboardPlayEvent, polaris.messages.incoming.soundboard.SoundboardPlayEvent.class);
        this.registerHandler(
                Incoming.SoundboardSetEnabledEvent,
                polaris.messages.incoming.soundboard.SoundboardSetEnabledEvent.class);
        this.registerHandler(
                Incoming.SoundboardSaveVolumeEvent,
                polaris.messages.incoming.soundboard.SoundboardSaveVolumeEvent.class);
        this.registerHandler(
                Incoming.SoundboardRequestSettingsEvent,
                polaris.messages.incoming.soundboard.SoundboardRequestSettingsEvent.class);
        this.registerHandler(
                Incoming.SoundboardCatalogRequestEvent,
                polaris.messages.incoming.soundboard.SoundboardCatalogRequestEvent.class);
        this.registerHandler(
                Incoming.SoundboardCatalogUpsertEvent,
                polaris.messages.incoming.soundboard.SoundboardCatalogUpsertEvent.class);
        this.registerHandler(
                Incoming.SoundboardCatalogReorderEvent,
                polaris.messages.incoming.soundboard.SoundboardCatalogReorderEvent.class);

        this.registerHandler(
                Incoming.TraxEditorRequestSongsEvent,
                polaris.messages.incoming.traxeditor.TraxEditorRequestSongsEvent.class);
        this.registerHandler(
                Incoming.TraxEditorBuySongEvent, polaris.messages.incoming.traxeditor.TraxEditorBuySongEvent.class);
        this.registerHandler(
                Incoming.TraxEditorSaveSongEvent, polaris.messages.incoming.traxeditor.TraxEditorSaveSongEvent.class);
        this.registerHandler(
                Incoming.TraxEditorDeleteSongEvent,
                polaris.messages.incoming.traxeditor.TraxEditorDeleteSongEvent.class);
    }

    void registerEarnings() throws Exception {
        this.registerHandler(Incoming.RequestEarningsCenterEvent, RequestEarningsCenterEvent.class);
        this.registerHandler(Incoming.ClaimEarningsRewardEvent, ClaimEarningsRewardEvent.class);
        this.registerHandler(Incoming.ClaimAllEarningsRewardsEvent, ClaimAllEarningsRewardsEvent.class);

        this.registerHandler(Incoming.GetQuestsEvent, GetQuestsEvent.class);
        this.registerHandler(Incoming.GetSeasonalQuestsOnlyEvent, GetSeasonalQuestsOnlyEvent.class);
        this.registerHandler(Incoming.GetSeasonalCalendarDailyOfferEvent, GetSeasonalCalendarDailyOfferEvent.class);
        this.registerHandler(Incoming.AcceptQuestEvent, AcceptQuestEvent.class);
        this.registerHandler(Incoming.ActivateQuestEvent, ActivateQuestEvent.class);
        this.registerHandler(Incoming.RejectQuestEvent, RejectQuestEvent.class);
        this.registerHandler(Incoming.CancelDailyQuestEvent, CancelDailyQuestEvent.class);
        this.registerHandler(Incoming.GetDailyQuestEvent, GetDailyQuestEvent.class);
        this.registerHandler(Incoming.OpenQuestTrackerEvent, OpenQuestTrackerEvent.class);
        this.registerHandler(Incoming.StartCampaignEvent, StartCampaignEvent.class);
        this.registerHandler(Incoming.GetDailyTasksEvent, GetDailyTasksEvent.class);
        this.registerHandler(Incoming.ClaimDailyTaskEvent, ClaimDailyTaskEvent.class);
        this.registerHandler(Incoming.GetRewardTracksEvent, GetRewardTracksEvent.class);
        this.registerHandler(Incoming.ClaimRewardTrackPrizeEvent, ClaimRewardTrackPrizeEvent.class);
        this.registerHandler(Incoming.PurchaseRewardTrackPremiumEvent, PurchaseRewardTrackPremiumEvent.class);
        this.registerHandler(Incoming.GetRewardTrackAdminDataEvent, GetRewardTrackAdminDataEvent.class);
        this.registerHandler(Incoming.SaveRewardTrackEvent, SaveRewardTrackEvent.class);
        this.registerHandler(Incoming.SaveRewardTrackTaskEvent, SaveRewardTrackTaskEvent.class);
        this.registerHandler(Incoming.SaveRewardTrackPrizeEvent, SaveRewardTrackPrizeEvent.class);
        this.registerHandler(Incoming.DeleteRewardTrackEntityEvent, DeleteRewardTrackEntityEvent.class);
        this.registerHandler(Incoming.SearchRewardTrackFurniEvent, SearchRewardTrackFurniEvent.class);
        this.registerHandler(Incoming.SaveRewardTrackTextsEvent, SaveRewardTrackTextsEvent.class);
    }
}
