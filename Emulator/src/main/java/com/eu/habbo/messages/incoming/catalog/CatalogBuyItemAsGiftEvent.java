package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.catalog.*;
import com.eu.habbo.habbohotel.catalog.layouts.*;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.*;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.subscriptions.Subscription;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.*;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.HotelWillCloseInMinutesComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.users.UserClubComposer;
import com.eu.habbo.threading.runnables.ShutdownEmulator;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Calendar;

public class CatalogBuyItemAsGiftEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogBuyItemAsGiftEvent.class);

    private static final int USERNAME_MAX = 32;
    private static final int EXTRADATA_MAX = 256;

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (Emulator.getIntUnixTimestamp() - this.client.getHabbo().getHabboStats().lastGiftTimestamp >= CatalogManager.PURCHASE_COOLDOWN) {
            if (ShutdownEmulator.timestamp > 0) {
                LOGGER.debug("emulator closing");
                this.client.sendResponse(new HotelWillCloseInMinutesComposer((ShutdownEmulator.timestamp - Emulator.getIntUnixTimestamp()) / 60));
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                return;
            }

            synchronized (this.client.getHabbo().getHabboStats()) {
                if (this.client.getHabbo().getHabboStats().isPurchasingFurniture) {
                    LOGGER.debug("isPurchasingFurniture already true");
                    this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                    return;
                }
                this.client.getHabbo().getHabboStats().isPurchasingFurniture = true;
            }

            int paidCredits = 0;
            int paidPoints = 0;
            int paidPointsType = 0;

            try {
                int pageId = this.packet.readInt();
                int itemId = this.packet.readInt();
                String extraData = this.packet.readString();
                if (extraData.length() > EXTRADATA_MAX) extraData = extraData.substring(0, EXTRADATA_MAX);
                String username = this.packet.readString();
                if (username.length() > USERNAME_MAX) username = username.substring(0, USERNAME_MAX);
                int messageMax = Emulator.getConfig().getInt("hotel.gifts.length.max", 300);
                String rawMessage = this.packet.readString();
                if (rawMessage.length() > messageMax) rawMessage = rawMessage.substring(0, messageMax);
                String message = Emulator.getGameEnvironment().getWordFilter().filter(rawMessage, this.client.getHabbo());
                int spriteId = this.packet.readInt();
                int color = this.packet.readInt();
                int ribbonId = this.packet.readInt();
                boolean showName = this.packet.readBoolean();

                LOGGER.debug("Gift request: pageId={}, itemId={}, spriteId={}, color={}, ribbonId={}", pageId, itemId, spriteId, color, ribbonId);

                int userId = 0;

                CatalogPage clubGiftPage = Emulator.getGameEnvironment().getCatalogManager().catalogPages.get(pageId);
                if (this.isClubOfferPage(clubGiftPage)) {
                    this.handleClubOfferGift(clubGiftPage, itemId, username);
                    return;
                }

                if (!Emulator.getGameEnvironment().getCatalogManager().giftWrappers.containsKey(spriteId)
                        && !Emulator.getGameEnvironment().getCatalogManager().giftFurnis.containsKey(spriteId)) {
                    LOGGER.debug("invalid spriteId for gift wrapper/furni -> {}", spriteId);
                    this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                    return;
                }

                if (!GiftConfigurationComposer.BOX_TYPES.contains(color) || !GiftConfigurationComposer.RIBBON_TYPES.contains(ribbonId)) {
                    LOGGER.debug("invalid color/ribbon -> color={}, ribbonId={}", color, ribbonId);
                    this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                    return;
                }

                Integer iItemId = Emulator.getGameEnvironment().getCatalogManager().giftWrappers.get(spriteId);

                if (iItemId == null) {
                    iItemId = Emulator.getGameEnvironment().getCatalogManager().giftFurnis.get(spriteId);
                }

                if (iItemId == null) {
                    LOGGER.debug("iItemId null for spriteId={}", spriteId);
                    this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                    return;
                }

                Item giftItem = Emulator.getGameEnvironment().getItemManager().getItem(iItemId);

                if (giftItem == null) {
                    LOGGER.debug("direct giftItem null, trying random fallback. iItemId={}", iItemId);
                    giftItem = Emulator.getGameEnvironment().getItemManager().getItem(
                            (Integer) Emulator.getGameEnvironment().getCatalogManager().giftFurnis.values().toArray()[
                                    Emulator.getRandom().nextInt(Emulator.getGameEnvironment().getCatalogManager().giftFurnis.size())
                                    ]
                    );

                    if (giftItem == null) {
                        LOGGER.debug("fallback giftItem also null");
                        this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                        return;
                    }
                }

                try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                    Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(username);

                    if (habbo == null) {
                        LOGGER.debug("target user not online, checking DB -> {}", username);
                        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM users WHERE username = ?")) {
                            statement.setString(1, username);

                            try (ResultSet set = statement.executeQuery()) {
                                if (set.next()) {
                                    userId = set.getInt(1);
                                }
                            }
                        } catch (SQLException e) {
                            LOGGER.error("Caught SQL exception", e);
                        }
                    } else {
                        userId = habbo.getHabboInfo().getId();
                    }

                    if (userId == 0) {
                        LOGGER.debug("receiver not found -> {}", username);
                        this.client.sendResponse(new GiftReceiverNotFoundComposer());
                        return;
                    }

                    CatalogPage page = Emulator.getGameEnvironment().getCatalogManager().catalogPages.get(pageId);

                    if (page == null) {
                        LOGGER.debug("page null -> {}", pageId);
                        this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                        return;
                    }

                    if (page.getRank() > this.client.getHabbo().getHabboInfo().getRank().getId() || !page.isEnabled() || !page.isVisible()) {
                        LOGGER.debug("page access denied. pageRank={}, userRank={}, enabled={}, visible={}", page.getRank(), this.client.getHabbo().getHabboInfo().getRank().getId(), page.isEnabled(), page.isVisible());
                        this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
                        return;
                    }

                    CatalogItem item = page.getCatalogItem(itemId);

                    // Search-results gift sends the catalog offer_id as
                    // itemId, not catalog_items.id - see the same fix in
                    // CatalogBuyItemEvent. Fall back to scanning the
                    // page for the matching offer_id.
                    if (item == null) {
                        for (CatalogItem candidate : page.getCatalogItems().valueCollection()) {
                            if (candidate != null && candidate.getOfferId() == itemId) {
                                item = candidate;
                                break;
                            }
                        }
                    }

                    if (item == null) {
                        LOGGER.debug("catalog item null -> {}", itemId);
                        this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                        return;
                    }

                    if (item.isClubOnly() && !this.client.getHabbo().getHabboStats().hasActiveClub()) {
                        LOGGER.debug("item requires club -> itemId={}", itemId);
                        this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.REQUIRES_CLUB));
                        return;
                    }

                    for (Item baseItem : item.getBaseItems()) {
                        if (!baseItem.allowGift()) {
                            LOGGER.debug("base item not giftable -> baseItemId={}, name={}", baseItem.getId(), baseItem.getName());
                            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
                            return;
                        }
                    }

                    if (item.isLimited()) {
                        if (item.getLimitedStack() == item.getLimitedSells()) {
                            LOGGER.debug("LTD sold out -> itemId={}", itemId);
                            this.client.sendResponse(new AlertLimitedSoldOutComposer());
                            return;
                        }
                    }

                    int totalCredits = item.getCredits();
                    int totalPoints = item.getPoints();

                    // Paid wrapping (giftWrappers) costs hotel.gifts.special.price; default furni wrap is free.
                    boolean isPaidWrap = Emulator.getGameEnvironment().getCatalogManager().giftWrappers.containsKey(spriteId);
                    int wrapFee = isPaidWrap ? Emulator.getConfig().getInt("hotel.gifts.special.price", 0) : 0;
                    totalCredits += wrapFee;

                    if (totalCredits > this.client.getHabbo().getHabboInfo().getCredits()
                            || totalPoints > this.client.getHabbo().getHabboInfo().getCurrencyAmount(item.getPointsType())) {
                        LOGGER.debug("not enough currency. creditsNeeded={}, pointsNeeded={}, pointsType={}", totalCredits, totalPoints, item.getPointsType());
                        this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
                        return;
                    }

                    CatalogLimitedConfiguration limitedConfiguration = null;
                    int limitedStack = 0;
                    int limitedNumber = 0;

                    if (item.isLimited()) {
                        if (Emulator.getGameEnvironment().getCatalogManager().getLimitedConfig(item).available() == 0) {
                            LOGGER.debug("LTD available=0 -> itemId={}", itemId);
                            this.client.sendResponse(new AlertLimitedSoldOutComposer());
                            return;
                        }

                        if (Emulator.getConfig().getBoolean("hotel.catalog.ltd.limit.enabled")) {
                            int ltdLimit = Emulator.getConfig().getInt("hotel.purchase.ltd.limit.daily.total");
                            if (this.client.getHabbo().getHabboStats().totalLtds() >= ltdLimit) {
                                LOGGER.debug("sender reached daily total LTD limit");
                                this.client.getHabbo().alert(
                                        Emulator.getTexts().getValue("error.catalog.buy.limited.daily.total")
                                                .replace("%itemname%", item.getBaseItems().iterator().next().getDisplayName())
                                                .replace("%limit%", ltdLimit + "")
                                );
                                return;
                            }

                            ltdLimit = Emulator.getConfig().getInt("hotel.purchase.ltd.limit.daily.item");
                            if (this.client.getHabbo().getHabboStats().totalLtds(item.getId()) >= ltdLimit) {
                                LOGGER.debug("sender reached daily LTD item limit");
                                this.client.getHabbo().alert(
                                        Emulator.getTexts().getValue("error.catalog.buy.limited.daily.item")
                                                .replace("%itemname%", item.getBaseItems().iterator().next().getDisplayName())
                                                .replace("%limit%", ltdLimit + "")
                                );
                                return;
                            }
                        }

                        limitedConfiguration = Emulator.getGameEnvironment().getCatalogManager().getLimitedConfig(item);

                        if (limitedConfiguration == null) {
                            limitedConfiguration = Emulator.getGameEnvironment().getCatalogManager().createOrUpdateLimitedConfig(item);
                        }

                        limitedNumber = limitedConfiguration.getNumber();
                        limitedStack = limitedConfiguration.getTotalSet();
                        this.client.getHabbo().getHabboStats().addLtdLog(item.getId(), Emulator.getIntUnixTimestamp());
                    }

                    THashSet<HabboItem> itemsList = new THashSet<>();

                    boolean badgeFound = false;
                    for (Item baseItem : item.getBaseItems()) {
                        if (baseItem.getType() == FurnitureType.BADGE) {
                            if (habbo != null) {
                                if (habbo.getInventory().getBadgesComponent().hasBadge(baseItem.getName())) {
                                    badgeFound = true;
                                }
                            } else {
                                int c = 0;
                                try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) as c FROM users_badges WHERE user_id = ? AND badge_code LIKE ?")) {
                                    statement.setInt(1, userId);
                                    statement.setString(2, baseItem.getName());

                                    try (ResultSet rSet = statement.executeQuery()) {
                                        if (rSet.next()) {
                                            c = rSet.getInt("c");
                                        }
                                    }
                                }

                                if (c != 0) {
                                    badgeFound = true;
                                }
                            }
                        }
                    }

                    if (badgeFound) {
                        LOGGER.debug("receiver already has badge");
                        this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.ALREADY_HAVE_BADGE));
                        return;
                    }

                    if (item.getAmount() > 1 || item.getBaseItems().size() > 1) {
                        LOGGER.debug("unsupported multi amount/baseItems. amount={}, baseItems={}", item.getAmount(), item.getBaseItems().size());
                        this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                        return;
                    }

                    for (Item baseItem : item.getBaseItems()) {
                        if (item.getItemAmount(baseItem.getId()) > 1) {
                            LOGGER.debug("unsupported item amount > 1 for baseItemId={}", baseItem.getId());
                            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                            return;
                        }

                        for (int k = 0; k < item.getItemAmount(baseItem.getId()); k++) {
                            if (!baseItem.getName().contains("avatar_effect")) {
                                if (baseItem.getType() == FurnitureType.BADGE) {
                                    if (!badgeFound) {
                                        if (habbo != null) {
                                            HabboBadge badge = new HabboBadge(0, baseItem.getName(), 0, habbo);
                                            Emulator.getThreading().run(badge);
                                            habbo.getInventory().getBadgesComponent().addBadge(badge);
                                        } else {
                                            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO users_badges (user_id, badge_code) VALUES (?, ?)")) {
                                                statement.setInt(1, userId);
                                                statement.setString(2, baseItem.getName());
                                                statement.execute();
                                            }
                                        }

                                        badgeFound = true;
                                    }
                                } else if (item.getName().startsWith("rentable_bot_")) {
                                    LOGGER.debug("rentable bot gifts not supported");
                                    this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                                    return;
                                } else if (Item.isPet(baseItem)) {
                                    LOGGER.debug("pet gifts not supported");
                                    this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                                    return;
                                } else {
                                    if (baseItem.getInteractionType().getType() == InteractionTrophy.class
                                            || baseItem.getInteractionType().getType() == InteractionBadgeDisplay.class) {
                                        if (baseItem.getInteractionType().getType() == InteractionBadgeDisplay.class
                                                && habbo != null
                                                && !habbo.getClient().getHabbo().getInventory().getBadgesComponent().hasBadge(extraData)) {
                                            ScripterManager.scripterDetected(
                                                    habbo.getClient(),
                                                    Emulator.getTexts().getValue("scripter.warning.catalog.badge_display")
                                                            .replace("%username%", habbo.getClient().getHabbo().getHabboInfo().getUsername())
                                                            .replace("%badge%", extraData)
                                            );
                                            extraData = "UMAD";
                                        }

                                        extraData = this.client.getHabbo().getHabboInfo().getUsername()
                                                + (char) 9
                                                + Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                                                + "-"
                                                + (Calendar.getInstance().get(Calendar.MONTH) + 1)
                                                + "-"
                                                + Calendar.getInstance().get(Calendar.YEAR)
                                                + (char) 9
                                                + extraData;
                                    }

                                    if (baseItem.getInteractionType().getType() == InteractionTeleport.class
                                            || baseItem.getInteractionType().getType() == InteractionTeleportTile.class) {

                                        HabboItem teleportOne = Emulator.getGameEnvironment().getItemManager().createItem(userId, baseItem, limitedStack, limitedNumber, extraData);
                                        HabboItem teleportTwo = Emulator.getGameEnvironment().getItemManager().createItem(userId, baseItem, limitedStack, limitedNumber, extraData);

                                        if (teleportOne == null || teleportTwo == null) {
                                            LOGGER.debug("teleport creation failed. baseItemId={}, teleportOneNull={}, teleportTwoNull={}", baseItem.getId(), teleportOne == null, teleportTwo == null);
                                            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                                            return;
                                        }

                                        Emulator.getGameEnvironment().getItemManager().insertTeleportPair(teleportOne.getId(), teleportTwo.getId());
                                        itemsList.add(teleportOne);
                                        itemsList.add(teleportTwo);

                                    } else if (baseItem.getInteractionType().getType() == InteractionHopper.class) {
                                        HabboItem habboItem = Emulator.getGameEnvironment().getItemManager().createItem(userId, baseItem, limitedNumber, limitedNumber, extraData);

                                        if (habboItem == null) {
                                            LOGGER.debug("hopper creation failed. baseItemId={}", baseItem.getId());
                                            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                                            return;
                                        }

                                        Emulator.getGameEnvironment().getItemManager().insertHopper(habboItem);
                                        itemsList.add(habboItem);

                                    } else if (baseItem.getInteractionType().getType() == InteractionGuildFurni.class
                                            || baseItem.getInteractionType().getType() == InteractionGuildGate.class) {
                                        HabboItem createdItem = Emulator.getGameEnvironment().getItemManager().createItem(userId, baseItem, limitedStack, limitedNumber, extraData);

                                        if (createdItem == null) {
                                            LOGGER.debug("guild item creation failed. baseItemId={}", baseItem.getId());
                                            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                                            return;
                                        }

                                        if (!(createdItem instanceof InteractionGuildFurni)) {
                                            LOGGER.debug("created guild item has wrong class -> {}", createdItem.getClass().getName());
                                            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                                            return;
                                        }

                                        InteractionGuildFurni habboItem = (InteractionGuildFurni) createdItem;
                                        habboItem.setExtradata("");
                                        habboItem.needsUpdate(true);

                                        int guildId;
                                        try {
                                            guildId = Integer.parseInt(extraData);
                                        } catch (Exception e) {
                                            LOGGER.error("Caught exception", e);
                                            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                                            return;
                                        }

                                        Emulator.getThreading().run(habboItem);
                                        Emulator.getGameEnvironment().getGuildManager().setGuild(habboItem, guildId);
                                        itemsList.add(habboItem);
                                    } else {
                                        HabboItem habboItem = Emulator.getGameEnvironment().getItemManager().createItem(userId, baseItem, limitedStack, limitedNumber, extraData);

                                        if (habboItem == null) {
                                            LOGGER.debug("normal item creation failed. baseItemId={}, baseItemName={}", baseItem.getId(), baseItem.getName());
                                            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                                            return;
                                        }

                                        itemsList.add(habboItem);
                                    }
                                }
                            } else {
                                LOGGER.debug("avatar_effect not supported");
                                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                                this.client.sendResponse(new GenericAlertComposer(Emulator.getTexts().getValue("error.catalog.buy.not_yet")));
                                return;
                            }
                        }
                    }

                    if (itemsList.isEmpty()) {
                        LOGGER.debug("itemsList empty before giftData");
                        this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                        return;
                    }

                    StringBuilder giftData = new StringBuilder(itemsList.size() + "\t");

                    for (HabboItem i : itemsList) {
                        if (i == null) {
                            LOGGER.debug("null HabboItem detected inside itemsList");
                            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                            return;
                        }

                        giftData.append(i.getId()).append("\t");
                    }

                    giftData.append(color)
                            .append("\t")
                            .append(ribbonId)
                            .append("\t")
                            .append(showName ? "1" : "0")
                            .append("\t")
                            .append(message.replace("\t", ""))
                            .append("\t")
                            .append(this.client.getHabbo().getHabboInfo().getUsername())
                            .append("\t")
                            .append(this.client.getHabbo().getHabboInfo().getLook());

                    // Deduct currency before createGift so a failure here leaves the sender unpaid rather than gifted.
                    if (!this.client.getHabbo().hasPermission(Permission.ACC_INFINITE_CREDITS) && totalCredits > 0) {
                        this.client.getHabbo().giveCredits(-totalCredits);
                        paidCredits = totalCredits;
                    }

                    if (totalPoints > 0) {
                        if (item.getPointsType() == 0 && !this.client.getHabbo().hasPermission(Permission.ACC_INFINITE_PIXELS)) {
                            this.client.getHabbo().givePixels(-totalPoints);
                            paidPoints = totalPoints;
                            paidPointsType = 0;
                        } else if (!this.client.getHabbo().hasPermission(Permission.ACC_INFINITE_POINTS)) {
                            this.client.getHabbo().givePoints(item.getPointsType(), -totalPoints);
                            paidPoints = totalPoints;
                            paidPointsType = item.getPointsType();
                        }
                    }

                    HabboItem gift = Emulator.getGameEnvironment().getItemManager().createGift(username, giftItem, giftData.toString(), 0, 0);

                    if (gift == null) {
                        LOGGER.debug("createGift returned null");
                        if (paidCredits > 0) {
                            this.client.getHabbo().giveCredits(paidCredits);
                            paidCredits = 0;
                        }
                        if (paidPoints > 0) {
                            if (paidPointsType == 0) this.client.getHabbo().givePixels(paidPoints);
                            else this.client.getHabbo().givePoints(paidPointsType, paidPoints);
                            paidPoints = 0;
                        }
                        this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                        return;
                    }

                    if (limitedConfiguration != null) {
                        for (HabboItem itm : itemsList) {
                            if (itm == null) {
                                // Trip the catch path so the deduction is refunded.
                                throw new IllegalStateException("null item before limitedSold()");
                            }
                            limitedConfiguration.limitedSold(item.getId(), this.client.getHabbo(), itm);
                        }
                    }

                    if (this.client.getHabbo().getHabboInfo().getId() != userId) {
                        AchievementManager.progressAchievement(
                                this.client.getHabbo(),
                                Emulator.getGameEnvironment().getAchievementManager().getAchievement("GiftGiver")
                        );
                    }

                    if (habbo != null) {
                        habbo.getClient().sendResponse(new AddHabboItemComposer(gift));
                        habbo.getClient().getHabbo().getInventory().getItemsComponent().addItem(gift);
                        habbo.getClient().sendResponse(new InventoryRefreshComposer());

                        THashMap<String, String> keys = new THashMap<>();
                        keys.put("display", "BUBBLE");
                        keys.put("image", "${image.library.url}notifications/gift.gif");
                        keys.put("message", Emulator.getTexts().getValue("generic.gift.received.anonymous"));

                        if (showName) {
                            keys.put("message", Emulator.getTexts().getValue("generic.gift.received")
                                    .replace("%username%", this.client.getHabbo().getHabboInfo().getUsername()));
                        }

                        habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys));
                    }

                    if (this.client.getHabbo().getHabboInfo().getId() != userId) {
                        AchievementManager.progressAchievement(
                                userId,
                                Emulator.getGameEnvironment().getAchievementManager().getAchievement("GiftReceiver")
                        );
                    }

                    // Gift fully delivered; commit cooldown and clear refund tracking so the catch block can't double-refund.
                    this.client.getHabbo().getHabboStats().lastGiftTimestamp = Emulator.getIntUnixTimestamp();
                    paidCredits = 0;
                    paidPoints = 0;

                    this.client.sendResponse(new PurchaseOKComposer(item));
                }
            } catch (Exception e) {
                LOGGER.error("Exception caught", e);
                this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                if (paidCredits > 0) this.client.getHabbo().giveCredits(paidCredits);
                if (paidPoints > 0) {
                    if (paidPointsType == 0) this.client.getHabbo().givePixels(paidPoints);
                    else this.client.getHabbo().givePoints(paidPointsType, paidPoints);
                }
            } finally {
                this.client.getHabbo().getHabboStats().isPurchasingFurniture = false;
            }
        } else {
            LOGGER.debug("cooldown blocked purchase");
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
        }
    }

    private boolean isClubOfferPage(CatalogPage page) {
        return page instanceof ClubBuyLayout
                || page instanceof VipBuyLayout
                || page instanceof BuildersClubFrontPageLayout
                || page instanceof BuildersClubAddonsLayout
                || page instanceof BuildersClubLoyaltyLayout;
    }

    private int getClubOfferWindowId(CatalogPage page) {
        if (page instanceof BuildersClubAddonsLayout) {
            return ClubOffer.WINDOW_BUILDERS_CLUB_ADDONS;
        }

        if (page instanceof BuildersClubFrontPageLayout || page instanceof BuildersClubLoyaltyLayout) {
            return ClubOffer.WINDOW_BUILDERS_CLUB;
        }

        return ClubOffer.WINDOW_HABBO_CLUB;
    }

    private void handleClubOfferGift(CatalogPage page, int offerId, String username) {
        ClubOffer offer = Emulator.getGameEnvironment().getCatalogManager().clubOffers.get(offerId);

        if (offer == null || !offer.belongsToWindow(this.getClubOfferWindowId(page))) {
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        if (!offer.isGiftable()) {
            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return;
        }

        if (offer.isBuildersClubAddon()) {
            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return;
        }

        int totalCredits = offer.getCredits();
        int totalPoints = offer.getPoints();

        if (totalCredits > this.client.getHabbo().getHabboInfo().getCredits()
                || totalPoints > this.client.getHabbo().getHabboInfo().getCurrencyAmount(offer.getPointsType())) {
            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return;
        }

        Habbo recipient = Emulator.getGameEnvironment().getHabboManager().getHabbo(username);
        int recipientId = 0;

        if (recipient != null) {
            recipientId = recipient.getHabboInfo().getId();
        } else {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT id FROM users WHERE username = ?")) {
                statement.setString(1, username);
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) recipientId = set.getInt(1);
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception while resolving club gift recipient", e);
            }
        }

        if (recipientId == 0) {
            this.client.sendResponse(new GiftReceiverNotFoundComposer());
            return;
        }

        if (recipientId == this.client.getHabbo().getHabboInfo().getId()) {
            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return;
        }

        String subscriptionType = offer.isBuildersClubSubscription() ? Subscription.BUILDERS_CLUB : Subscription.HABBO_CLUB;
        int duration = offer.getDays() * 86400;

        boolean extended;
        if (recipient != null) {
            extended = (recipient.getHabboStats().createSubscription(subscriptionType, duration) != null);
        } else {
            extended = this.extendOfflineSubscription(recipientId, subscriptionType, duration);
        }

        if (!extended) {
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        if (totalCredits > 0 && !this.client.getHabbo().hasPermission(Permission.ACC_INFINITE_CREDITS)) {
            this.client.getHabbo().giveCredits(-totalCredits);
        }

        if (totalPoints > 0) {
            if (offer.getPointsType() == 0 && !this.client.getHabbo().hasPermission(Permission.ACC_INFINITE_PIXELS)) {
                this.client.getHabbo().givePixels(-totalPoints);
            } else if (!this.client.getHabbo().hasPermission(Permission.ACC_INFINITE_POINTS)) {
                this.client.getHabbo().givePoints(offer.getPointsType(), -totalPoints);
            }
        }

        if (recipient != null) {
            recipient.getClient().sendResponse(new UserClubComposer(recipient, subscriptionType, UserClubComposer.RESPONSE_TYPE_NORMAL));

            String prefix = Emulator.getTexts().getValue("prereg.reward.you.received", "You have received:");
            String daysWord = Emulator.getTexts().getValue("generic.days", "days");
            String clubLabel = offer.isBuildersClubSubscription() ? "Builders Club" : "HC";
            String giftDescription = clubLabel + " (" + offer.getDays() + " " + daysWord + ")";
            THashMap<String, String> keys = new THashMap<>();
            keys.put("display", "BUBBLE");
            keys.put("image", "${image.library.url}notifications/gift.gif");
            keys.put("message", prefix + " " + giftDescription);
            recipient.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_GIFT.key, keys));
        }

        if (this.client.getHabbo().getHabboInfo().getId() != recipientId) {
            AchievementManager.progressAchievement(
                    this.client.getHabbo(),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("GiftGiver")
            );
        }

        this.client.sendResponse(new PurchaseOKComposer(null));
    }

    private boolean extendOfflineSubscription(int userId, String subscriptionType, int duration) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT id, duration FROM users_subscriptions WHERE user_id = ? AND subscription_type = ? AND active = 1 ORDER BY id DESC LIMIT 1")) {
                select.setInt(1, userId);
                select.setString(2, subscriptionType);
                try (ResultSet set = select.executeQuery()) {
                    if (set.next()) {
                        int subId = set.getInt("id");
                        int existing = set.getInt("duration");
                        try (PreparedStatement update = connection.prepareStatement(
                                "UPDATE users_subscriptions SET duration = ? WHERE id = ?")) {
                            update.setInt(1, existing + duration);
                            update.setInt(2, subId);
                            update.executeUpdate();
                            return true;
                        }
                    }
                }
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO users_subscriptions (user_id, subscription_type, timestamp_start, duration, active) VALUES (?, ?, ?, ?, 1)",
                    Statement.RETURN_GENERATED_KEYS)) {
                insert.setInt(1, userId);
                insert.setString(2, subscriptionType);
                insert.setInt(3, Emulator.getIntUnixTimestamp());
                insert.setInt(4, duration);
                insert.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception while extending offline subscription", e);
            return false;
        }
    }
}