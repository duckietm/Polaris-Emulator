package polaris.hotel.catalog;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.bots.Bot;
import polaris.hotel.catalog.layouts.BadgeDisplayLayout;
import polaris.hotel.catalog.layouts.BotsLayout;
import polaris.hotel.catalog.layouts.BuildersClubAddonsLayout;
import polaris.hotel.catalog.layouts.BuildersClubFrontPageLayout;
import polaris.hotel.catalog.layouts.BuildersClubLoyaltyLayout;
import polaris.hotel.catalog.layouts.CatalogRootLayout;
import polaris.hotel.catalog.layouts.ClubBuyLayout;
import polaris.hotel.catalog.layouts.ClubGiftsLayout;
import polaris.hotel.catalog.layouts.ColorGroupingLayout;
import polaris.hotel.catalog.layouts.Default_3x3Layout;
import polaris.hotel.catalog.layouts.FrontPageFeaturedLayout;
import polaris.hotel.catalog.layouts.FrontpageLayout;
import polaris.hotel.catalog.layouts.GuildForumLayout;
import polaris.hotel.catalog.layouts.GuildFrontpageLayout;
import polaris.hotel.catalog.layouts.GuildFurnitureLayout;
import polaris.hotel.catalog.layouts.InfoDucketsLayout;
import polaris.hotel.catalog.layouts.InfoLoyaltyLayout;
import polaris.hotel.catalog.layouts.InfoMonkeyLayout;
import polaris.hotel.catalog.layouts.InfoNikoLayout;
import polaris.hotel.catalog.layouts.InfoPetsLayout;
import polaris.hotel.catalog.layouts.InfoRentablesLayout;
import polaris.hotel.catalog.layouts.LoyaltyVipBuyLayout;
import polaris.hotel.catalog.layouts.MadMoneyLayout;
import polaris.hotel.catalog.layouts.MarketplaceLayout;
import polaris.hotel.catalog.layouts.MarketplaceOwnItems;
import polaris.hotel.catalog.layouts.PetCustomizationLayout;
import polaris.hotel.catalog.layouts.Pets2Layout;
import polaris.hotel.catalog.layouts.Pets3Layout;
import polaris.hotel.catalog.layouts.PetsLayout;
import polaris.hotel.catalog.layouts.ProductPage1Layout;
import polaris.hotel.catalog.layouts.RecentPurchasesLayout;
import polaris.hotel.catalog.layouts.RecolorableFurnitureLayout;
import polaris.hotel.catalog.layouts.RecyclerInfoLayout;
import polaris.hotel.catalog.layouts.RecyclerLayout;
import polaris.hotel.catalog.layouts.RecyclerPrizesLayout;
import polaris.hotel.catalog.layouts.RoomAdsLayout;
import polaris.hotel.catalog.layouts.RoomBundleLayout;
import polaris.hotel.catalog.layouts.SingleBundle;
import polaris.hotel.catalog.layouts.SoldLTDItemsLayout;
import polaris.hotel.catalog.layouts.SpacesLayout;
import polaris.hotel.catalog.layouts.TraxLayout;
import polaris.hotel.catalog.layouts.TrophiesLayout;
import polaris.hotel.catalog.layouts.VipBuyLayout;
import polaris.hotel.economy.EconomyOperationId;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.guilds.Guild;
import polaris.hotel.habbicons.HabbiconService;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.ItemManager;
import polaris.hotel.items.SoundTrack;
import polaris.hotel.items.interactions.InteractionBadgeDisplay;
import polaris.hotel.items.interactions.InteractionGuildFurni;
import polaris.hotel.items.interactions.InteractionGuildGate;
import polaris.hotel.items.interactions.InteractionHopper;
import polaris.hotel.items.interactions.InteractionMusicDisc;
import polaris.hotel.items.interactions.InteractionTeleport;
import polaris.hotel.items.interactions.InteractionTrophy;
import polaris.hotel.items.interactions.wired.extra.WiredWebApiOwnership;
import polaris.hotel.items.rentable.RentableFurniture;
import polaris.hotel.items.rentable.RentableFurnitureManager;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.permissions.Permission;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetManager;
import polaris.hotel.users.ChatStyleRepository;
import polaris.hotel.users.User;
import polaris.hotel.users.UserBadge;
import polaris.hotel.users.UserGender;
import polaris.hotel.users.UserInventory;
import polaris.hotel.users.UserItem;
import polaris.hotel.users.UserStats;
import polaris.hotel.users.inventory.BadgesComponent;
import polaris.hotel.users.inventory.EffectsComponent;
import polaris.messages.outgoing.catalog.AlertLimitedSoldOutComposer;
import polaris.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import polaris.messages.outgoing.catalog.AlertPurchaseUnavailableComposer;
import polaris.messages.outgoing.catalog.DiscountComposer;
import polaris.messages.outgoing.catalog.NotEnoughPointsTypeComposer;
import polaris.messages.outgoing.catalog.PetBoughtNotificationComposer;
import polaris.messages.outgoing.catalog.PurchaseOKComposer;
import polaris.messages.outgoing.catalog.RedeemVoucherErrorComposer;
import polaris.messages.outgoing.catalog.RedeemVoucherOKComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.inventory.AddBotComposer;
import polaris.messages.outgoing.inventory.AddPetComposer;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.modtool.ModToolIssueHandledComposer;
import polaris.messages.outgoing.users.AddUserBadgeComposer;
import polaris.messages.outgoing.users.UserCreditsComposer;
import polaris.messages.outgoing.users.UserPointsComposer;
import polaris.plugin.events.emulator.EmulatorLoadCatalogManagerEvent;
import polaris.plugin.events.users.UserCreditsEvent;
import polaris.plugin.events.users.UserPointsEvent;
import polaris.plugin.events.users.catalog.UserCatalogFurnitureBoughtEvent;
import polaris.plugin.events.users.catalog.UserCatalogItemPurchasedEvent;

public class CatalogManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogManager.class);

    public record GiftWrappingSnapshot(Map<Integer, Integer> wrappers, Map<Integer, Integer> furniture) {}

    public static final Map<String, Class<? extends CatalogPage>> pageDefinitions =
            new ConcurrentHashMap<String, Class<? extends CatalogPage>>(CatalogPageLayouts.values().length) {
                {
                    for (CatalogPageLayouts layout : CatalogPageLayouts.values()) {
                        switch (layout) {
                            case frontpage:
                                this.put(layout.name().toLowerCase(), FrontpageLayout.class);
                                break;
                            case badge_display:
                                this.put(layout.name().toLowerCase(), BadgeDisplayLayout.class);
                                break;
                            case spaces:
                            case spaces_new:
                                this.put(layout.name().toLowerCase(), SpacesLayout.class);
                                break;
                            case trophies:
                                this.put(layout.name().toLowerCase(), TrophiesLayout.class);
                                break;
                            case bots:
                                this.put(layout.name().toLowerCase(), BotsLayout.class);
                                break;
                            case club_buy:
                                this.put(layout.name().toLowerCase(), ClubBuyLayout.class);
                                break;
                            case club_gift:
                                this.put(layout.name().toLowerCase(), ClubGiftsLayout.class);
                                break;
                            case sold_ltd_items:
                                this.put(layout.name().toLowerCase(), SoldLTDItemsLayout.class);
                                break;
                            case single_bundle:
                                this.put(layout.name().toLowerCase(), SingleBundle.class);
                                break;
                            case roomads:
                                this.put(layout.name().toLowerCase(), RoomAdsLayout.class);
                                break;
                            case recycler:
                            case recycler_info:
                            case recycler_prizes:
                            case marketplace:
                            case marketplace_own_items:
                                // Configuration is not available during class loading. Optional
                                // layouts are registered when the catalog is initialized.
                                break;
                            case info_duckets:
                                this.put(layout.name().toLowerCase(), InfoDucketsLayout.class);
                                break;
                            case info_pets:
                                this.put(layout.name().toLowerCase(), InfoPetsLayout.class);
                                break;
                            case info_rentables:
                                this.put(layout.name().toLowerCase(), InfoRentablesLayout.class);
                                break;
                            case info_loyalty:
                                this.put(layout.name().toLowerCase(), InfoLoyaltyLayout.class);
                                break;
                            case loyalty_vip_buy:
                                this.put(layout.name().toLowerCase(), LoyaltyVipBuyLayout.class);
                                break;
                            case guilds:
                                this.put(layout.name().toLowerCase(), GuildFrontpageLayout.class);
                                break;
                            case guild_furni:
                                this.put(layout.name().toLowerCase(), GuildFurnitureLayout.class);
                                break;
                            case guild_forum:
                                this.put(layout.name().toLowerCase(), GuildForumLayout.class);
                                break;
                            case pets:
                                this.put(layout.name().toLowerCase(), PetsLayout.class);
                                break;
                            case pets2:
                                this.put(layout.name().toLowerCase(), Pets2Layout.class);
                                break;
                            case pets3:
                                this.put(layout.name().toLowerCase(), Pets3Layout.class);
                                break;
                            case soundmachine:
                                this.put(layout.name().toLowerCase(), TraxLayout.class);
                                break;
                            case default_3x3_color_grouping:
                                this.put(layout.name().toLowerCase(), ColorGroupingLayout.class);
                                break;
                            case recolorable_furniture:
                                this.put(layout.name().toLowerCase(), RecolorableFurnitureLayout.class);
                                break;
                            case recent_purchases:
                                this.put(layout.name().toLowerCase(), RecentPurchasesLayout.class);
                                break;
                            case room_bundle:
                                this.put(layout.name().toLowerCase(), RoomBundleLayout.class);
                                break;
                            case petcustomization:
                                this.put(layout.name().toLowerCase(), PetCustomizationLayout.class);
                                break;
                            case vip_buy:
                                this.put(layout.name().toLowerCase(), VipBuyLayout.class);
                                break;
                            case frontpage_featured:
                                this.put(layout.name().toLowerCase(), FrontPageFeaturedLayout.class);
                                break;
                            case builders_club_addons:
                                this.put(layout.name().toLowerCase(), BuildersClubAddonsLayout.class);
                                break;
                            case builders_club_frontpage:
                                this.put(layout.name().toLowerCase(), BuildersClubFrontPageLayout.class);
                                break;
                            case builders_club_loyalty:
                                this.put(layout.name().toLowerCase(), BuildersClubLoyaltyLayout.class);
                                break;
                            case monkey:
                                this.put(layout.name().toLowerCase(), InfoMonkeyLayout.class);
                                break;
                            case niko:
                                this.put(layout.name().toLowerCase(), InfoNikoLayout.class);
                                break;
                            case mad_money:
                                this.put(layout.name().toLowerCase(), MadMoneyLayout.class);
                                break;
                            case custom_prefix:
                                // Retained in the public enum for plugin ABI compatibility only.
                                break;
                            case productpage1:
                                this.put(layout.name().toLowerCase(), ProductPage1Layout.class);
                                break;
                            case plasto:
                            case root:
                            case collectibles:
                                this.put(layout.name().toLowerCase(), Default_3x3Layout.class);
                                break;
                            case default_3x3:
                            default:
                                this.put("default_3x3", Default_3x3Layout.class);
                                break;
                        }
                    }
                }
            };

    private static void configureOptionalPageDefinitions() {
        boolean ecotronEnabled = Emulator.getConfig().getBoolean("hotel.ecotron.enabled");
        configurePageDefinition(CatalogPageLayouts.recycler, RecyclerLayout.class, ecotronEnabled);
        configurePageDefinition(CatalogPageLayouts.recycler_info, RecyclerInfoLayout.class, ecotronEnabled);
        configurePageDefinition(CatalogPageLayouts.recycler_prizes, RecyclerPrizesLayout.class, ecotronEnabled);

        boolean marketplaceEnabled = Emulator.getConfig().getBoolean("hotel.marketplace.enabled");
        configurePageDefinition(CatalogPageLayouts.marketplace, MarketplaceLayout.class, marketplaceEnabled);
        configurePageDefinition(
                CatalogPageLayouts.marketplace_own_items, MarketplaceOwnItems.class, marketplaceEnabled);
    }

    private static void configurePageDefinition(
            CatalogPageLayouts layout, Class<? extends CatalogPage> pageClass, boolean enabled) {
        if (enabled) {
            pageDefinitions.put(layout.name().toLowerCase(), pageClass);
        } else {
            pageDefinitions.remove(layout.name().toLowerCase());
        }
    }

    public static int catalogItemAmount;
    public static volatile int PURCHASE_COOLDOWN = 1;
    public static volatile boolean SORT_USING_ORDERNUM = false;
    public final Int2ObjectMap<CatalogPage> catalogPages;
    public final Int2ObjectMap<CatalogPage> buildersClubCatalogPages;
    public final Int2ObjectMap<CatalogFeaturedPage> catalogFeaturedPages;
    public final Map<Integer, Set<Item>> prizes;
    public final Map<Integer, Integer> giftWrappers;
    public final Map<Integer, Integer> giftFurnis;
    public final Set<CatalogItem> clubItems;
    public final Map<Integer, ClubOffer> clubOffers;
    public final Map<Integer, TargetOffer> targetOffers;
    public final Map<Integer, ClothItem> clothing;
    public final Int2IntMap offerDefs;
    public final Int2IntMap buildersClubOfferDefs;
    /** The base item every recycler box is made of. */
    public static final String RECYCLER_BOX = "ecotron_box";

    public final Item ecotronItem;
    public final Map<Integer, CatalogLimitedConfiguration> limitedNumbers;
    private final List<Voucher> vouchers;
    public final Int2ObjectMap<int[]> furnitureValues;
    private volatile byte[] rareValuesPayloadCache;
    private final AtomicLong catalogVersion = new AtomicLong();
    private volatile CatalogReadIndex readIndex;
    private final Object readIndexLock = new Object();

    public CatalogManager() {
        this(true);
    }

    CatalogManager(boolean initialize) {
        long millis = System.currentTimeMillis();
        this.catalogPages = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
        this.buildersClubCatalogPages = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
        this.catalogFeaturedPages = new Int2ObjectOpenHashMap<>();
        this.prizes = new HashMap<>();
        this.giftWrappers = new HashMap<>();
        this.giftFurnis = new HashMap<>();
        this.clubItems = new HashSet<>();
        this.clubOffers = new HashMap<>();
        this.targetOffers = new HashMap<>();
        this.clothing = new HashMap<>();
        this.offerDefs = new Int2IntOpenHashMap();
        this.buildersClubOfferDefs = new Int2IntOpenHashMap();
        this.vouchers = new ArrayList<>();
        this.limitedNumbers = new HashMap<>();
        this.furnitureValues = new Int2ObjectOpenHashMap<>();

        if (initialize) {
            this.initialize();
            this.ecotronItem = Emulator.getGameEnvironment().getItemManager().getItem(RECYCLER_BOX);
            LOGGER.info("Catalog Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
        } else {
            this.ecotronItem = null;
        }
    }

    public synchronized void initialize() {
        configureOptionalPageDefinitions();
        Emulator.getPluginManager().fireEvent(new EmulatorLoadCatalogManagerEvent());

        this.loadLimitedNumbers();
        // The pages are filled before they are published: they used to go live empty and get their items seconds
        // later, so a purchase or a gift during :update_catalogue found the page without its offer.
        Map<Integer, CatalogPage> pages = this.buildCatalogPages();
        this.loadBuildersClubCatalogPages();
        this.loadCatalogFeaturedPages();
        if (pages != null) {
            this.loadCatalogItems(pages);
            replacePageMap(this.catalogPages, pages);
            LOGGER.info("Loaded {} Catalog Pages!", this.catalogPages.size());
        }
        this.loadBuildersClubCatalogItems();
        this.loadClubOffers();
        this.loadTargetOffers();
        this.loadVouchers();
        this.loadClothing();
        this.loadRecycler();
        this.loadGiftWrappers();
        this.loadFurnitureValues();
        this.markCatalogChanged();
    }

    /** Monotonic counter of catalog mutations; the read index is rebuilt when it moves. */
    public long catalogVersion() {
        return this.catalogVersion.get();
    }

    /**
     * Signals that a catalog collection was mutated in place. Reloads and the admin studio call
     * this; a plugin that edits catalogPages or a page's items directly must call it too, or
     * lookups keep serving the previous snapshot.
     */
    public void markCatalogChanged() {
        this.catalogVersion.incrementAndGet();
    }

    CatalogReadIndex readIndex() {
        CatalogReadIndex current = this.readIndex;
        long version = this.catalogVersion.get();
        if (isCurrent(current, version)) return current;

        synchronized (this.readIndexLock) {
            current = this.readIndex;
            version = this.catalogVersion.get();
            if (isCurrent(current, version)) return current;

            // CatalogPage.getChildPages() is a concurrent map, so the admin edits in
            // CatalogAdminCacheSync.reparentPage/detachDeletedPage no longer break a build. A
            // plugin that edits another catalog collection in place can still throw
            // ConcurrentModificationException while a build walks it. Retry a few times against
            // the latest version instead of surfacing that transient race to whichever reader
            // happened to trigger the rebuild; if it keeps failing, something is genuinely wrong
            // and the last exception is rethrown.
            ConcurrentModificationException lastFailure = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                version = this.catalogVersion.get();
                try {
                    CatalogReadIndex built = CatalogReadIndex.build(this, version);
                    this.readIndex = built;
                    return built;
                } catch (ConcurrentModificationException e) {
                    lastFailure = e;
                }
            }
            throw lastFailure;
        }
    }

    private static boolean isCurrent(CatalogReadIndex index, long version) {
        return index != null && index.version() == version && index.sortUsingOrderNum() == SORT_USING_ORDERNUM;
    }

    /** Package-private accessor for {@link CatalogReadIndex#build} to iterate the live vouchers. */
    List<Voucher> vouchersView() {
        return this.vouchers;
    }

    private synchronized void loadFurnitureValues() {
        this.furnitureValues.clear();
        final int diamondType = Emulator.getConfig().getInt("seasonal.currency.diamond", 5);

        for (CatalogPage page : this.catalogPages.values()) {
            for (CatalogItem catalogItem : page.getCatalogItems().values()) {
                if (catalogItem.getAmount() != 1) continue;

                int credits = catalogItem.getCredits();
                int points = catalogItem.getPoints();
                int pointsType = catalogItem.getPointsType();

                if (points <= 0 || pointsType != diamondType) continue;

                Set<Item> baseItems = catalogItem.getBaseItems();

                if (baseItems.size() != 1) continue;

                for (Item item : baseItems) {
                    FurnitureType type = item.getType();

                    if (type != FurnitureType.FLOOR && type != FurnitureType.WALL) continue;

                    int spriteId = item.getSpriteId();

                    if (spriteId > 0 && !this.furnitureValues.containsKey(spriteId)) {
                        this.furnitureValues.put(spriteId, new int[] {credits, points, pointsType});
                    }
                }
            }
        }

        this.rebuildRareValuesPayloadCache();

        LOGGER.info("Furniture Values -> Loaded! ({} entries)", this.furnitureValues.size());
    }

    private void rebuildRareValuesPayloadCache() {
        try (java.io.ByteArrayOutputStream baos =
                        new java.io.ByteArrayOutputStream(this.furnitureValues.size() * 16 + 8);
                java.io.DataOutputStream out = new java.io.DataOutputStream(baos)) {
            out.writeInt(this.furnitureValues.size());
            for (Int2ObjectMap.Entry<int[]> entry : this.furnitureValues.int2ObjectEntrySet()) {
                int[] value = entry.getValue();
                out.writeInt(entry.getIntKey()); // spriteId
                out.writeInt(value[0]); // credits
                out.writeInt(value[1]); // points
                out.writeInt(value[2]); // pointsType
            }
            this.rareValuesPayloadCache = baos.toByteArray();
        } catch (java.io.IOException e) {
            LOGGER.error("Failed to build rare values payload cache", e);
            this.rareValuesPayloadCache = null;
        }
    }

    public Int2ObjectMap<int[]> getFurnitureValues() {
        return this.furnitureValues;
    }

    public byte[] getRareValuesPayloadSnapshot() {
        return this.rareValuesPayloadCache;
    }

    /**
     * Reads the limited numbers and applies them under the lock every reader takes. The map was cleared first and
     * filled after the query, without that lock: a limited purchase in between found no configuration, and a
     * configuration an in-flight purchase held was dropped with the numbers it would give back. A configuration that
     * exists keeps its object and takes the new numbers; a failed query changes nothing.
     */
    private synchronized void loadLimitedNumbers() {
        Map<Integer, LinkedList<Integer>> limiteds = new HashMap<>();
        Int2IntMap totals = new Int2IntOpenHashMap();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM catalog_items_limited")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    if (!limiteds.containsKey(set.getInt("catalog_item_id"))) {
                        limiteds.put(set.getInt("catalog_item_id"), new LinkedList<>());
                    }

                    int catalogItemId = set.getInt("catalog_item_id");
                    totals.put(catalogItemId, totals.get(catalogItemId) + 1);

                    if (set.getInt("user_id") == 0) {
                        limiteds.get(set.getInt("catalog_item_id")).push(set.getInt("number"));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            return;
        }

        synchronized (this.limitedNumbers) {
            this.limitedNumbers.keySet().retainAll(limiteds.keySet());
            for (Map.Entry<Integer, LinkedList<Integer>> set : limiteds.entrySet()) {
                int total = totals.get(set.getKey().intValue());
                CatalogLimitedConfiguration existing = this.limitedNumbers.get(set.getKey());
                if (existing != null) {
                    existing.replaceNumbers(set.getValue(), total);
                } else {
                    this.limitedNumbers.put(
                            set.getKey(), new CatalogLimitedConfiguration(set.getKey(), set.getValue(), total));
                }
            }
        }
    }

    /** The catalog pages as the database holds them, linked to their parents; null when it could not be read. */
    private synchronized Map<Integer, CatalogPage> buildCatalogPages() {
        final Map<Integer, CatalogPage> pages = new HashMap<>();
        pages.put(-1, new CatalogRootLayout());
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT * FROM catalog_pages ORDER BY parent_id, id")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Class<? extends CatalogPage> pageClazz = pageDefinitions.get(set.getString("page_layout"));

                    if (pageClazz == null) {
                        LOGGER.info("Unknown Page Layout: {}", set.getString("page_layout"));
                        continue;
                    }

                    try {
                        CatalogPage page =
                                pageClazz.getConstructor(ResultSet.class).newInstance(set);
                        pages.put(page.getId(), page);
                    } catch (Exception e) {
                        LOGGER.error("Failed to load layout: {}", set.getString("page_layout"));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            return null;
        }

        for (CatalogPage object : pages.values()) {
            CatalogPage page = pages.get(object.parentId);

            if (page != null) {
                if (page.id != object.id) {
                    page.addChildPage(object);
                }
            } else {
                if (object.parentId != -2) {
                    LOGGER.info(
                            "Parent Page not found for {} (ID: {}, parent_id: {})",
                            object.getPageName(),
                            object.id,
                            object.parentId);
                }
            }
        }

        return pages;
    }

    private synchronized void loadBuildersClubCatalogPages() {
        final Map<Integer, CatalogPage> pages = new HashMap<>();
        pages.put(-1, new CatalogRootLayout());

        String query =
                "SELECT id, parent_id, caption, caption AS caption_save, page_layout, icon_color, icon_image, 1 AS min_rank, order_num, visible, enabled, '0' AS club_only, 'BUILDERS_CLUB' AS catalog_mode, page_headline, page_teaser, page_special, page_text1, page_text2, page_text_details, page_text_teaser, '' AS includes FROM catalog_pages_bc ORDER BY parent_id, id";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(query)) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Class<? extends CatalogPage> pageClazz = pageDefinitions.get(set.getString("page_layout"));

                    if (pageClazz == null) {
                        LOGGER.info("Unknown Builders Club Page Layout: {}", set.getString("page_layout"));
                        continue;
                    }

                    try {
                        CatalogPage page =
                                pageClazz.getConstructor(ResultSet.class).newInstance(set);
                        pages.put(page.getId(), page);
                    } catch (Exception e) {
                        LOGGER.error("Failed to load Builders Club layout: {}", set.getString("page_layout"));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        for (CatalogPage object : pages.values()) {
            CatalogPage page = pages.get(object.parentId);

            if (page != null) {
                if (page.id != object.id) {
                    page.addChildPage(object);
                }
            } else {
                if (object.parentId != -2) {
                    LOGGER.info(
                            "Builders Club parent page not found for {} (ID: {}, parent_id: {})",
                            object.getPageName(),
                            object.id,
                            object.parentId);
                }
            }
        }

        replacePageMap(this.buildersClubCatalogPages, pages);

        LOGGER.info("Loaded {} Builders Club Catalog Pages!", this.buildersClubCatalogPages.size());
    }

    static void replacePageMap(Int2ObjectMap<CatalogPage> livePages, Map<Integer, CatalogPage> loadedPages) {
        synchronized (livePages) {
            livePages.clear();
            livePages.putAll(loadedPages);
        }
    }

    private synchronized void loadCatalogFeaturedPages() {
        Int2ObjectMap<CatalogFeaturedPage> loadedFeaturedPages = new Int2ObjectOpenHashMap<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT * FROM catalog_featured_pages ORDER BY slot_id ASC")) {
            while (set.next()) {
                loadedFeaturedPages.put(
                        set.getInt("slot_id"),
                        new CatalogFeaturedPage(
                                set.getInt("slot_id"),
                                set.getString("caption"),
                                set.getString("image"),
                                CatalogFeaturedPage.Type.valueOf(
                                        set.getString("type").toUpperCase()),
                                set.getInt("expire_timestamp"),
                                set.getString("page_name"),
                                set.getInt("page_id"),
                                set.getString("product_name")));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        this.replaceFeaturedPages(loadedFeaturedPages);
    }

    private synchronized void loadCatalogItems(Map<Integer, CatalogPage> pages) {
        this.offerDefs.clear();
        this.clubItems.clear();
        catalogItemAmount = 0;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT * FROM catalog_items")) {
            CatalogItem item;
            while (set.next()) {
                List<String> valueErrors = CatalogValueValidator.validate(CatalogValueValidator.Values.from(set));
                if (!valueErrors.isEmpty()) {
                    valueErrors.forEach(error -> LOGGER.error("Catalog value validation: {}", error));
                    continue;
                }
                if (set.getString("item_ids").equals("0") && set.getInt("habbicon_id") <= 0) continue;

                if (set.getString("catalog_name").contains("HABBO_CLUB_")) {
                    this.clubItems.add(new CatalogItem(set));
                    continue;
                }

                CatalogPage page = pages.get(set.getInt("page_id"));

                if (page == null) continue;

                item = page.getCatalogItem(set.getInt("id"));

                if (item == null) {
                    catalogItemAmount++;
                    item = new CatalogItem(set);
                    page.addItem(item);

                    int searchOfferId = item.getSearchOfferId();
                    if (searchOfferId != -1) {
                        page.addOfferId(searchOfferId);

                        this.offerDefs.put(searchOfferId, item.getId());
                    }
                } else item.update(set);

                if (item.isLimited()) {
                    this.createOrUpdateLimitedConfig(item);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        for (CatalogPage page : pages.values()) {
            for (Integer id : page.getIncluded()) {
                CatalogPage p = pages.get(id.intValue());

                if (p != null) {
                    page.getCatalogItems().putAll(p.getCatalogItems());
                }
            }
        }
    }

    private synchronized void loadBuildersClubCatalogItems() {
        this.buildersClubOfferDefs.clear();

        String query =
                "SELECT id, item_ids, page_id, catalog_name, 0 AS cost_credits, 0 AS cost_points, 0 AS points_type, 1 AS amount, 0 AS limited_stack, 0 AS limited_sells, extradata, '0' AS club_only, '1' AS have_offer, id AS offer_id, order_number FROM catalog_items_bc";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery(query)) {
            CatalogItem item;

            while (set.next()) {
                if (set.getString("item_ids").equals("0")) {
                    continue;
                }

                CatalogPage page = this.buildersClubCatalogPages.get(set.getInt("page_id"));

                if (page == null) {
                    continue;
                }

                item = page.getCatalogItem(set.getInt("id"));

                if (item == null) {
                    item = new CatalogItem(set);
                    page.addItem(item);
                    page.addOfferId(item.getOfferId());
                    this.buildersClubOfferDefs.put(item.getOfferId(), item.getId());
                } else {
                    item.update(set);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        for (CatalogPage page : this.buildersClubCatalogPages.values()) {
            for (Integer id : page.getIncluded()) {
                CatalogPage includedPage = this.buildersClubCatalogPages.get(id.intValue());

                if (includedPage != null) {
                    page.getCatalogItems().putAll(includedPage.getCatalogItems());
                }
            }
        }
    }

    private void loadClubOffers() {
        this.clubOffers.clear();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT * FROM catalog_club_offers WHERE enabled = ?")) {
            statement.setString(1, "1");
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    this.clubOffers.put(set.getInt("id"), new ClubOffer(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    private void loadTargetOffers() {
        Map<Integer, TargetOffer> loadedTargetOffers = new HashMap<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT * FROM catalog_target_offers WHERE end_timestamp > ?")) {
            statement.setInt(1, Emulator.getIntUnixTimestamp());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    loadedTargetOffers.put(set.getInt("id"), new TargetOffer(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        replaceContents(this.targetOffers, loadedTargetOffers);
    }

    private void loadVouchers() {
        synchronized (this.vouchers) {
            this.vouchers.clear();

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet set = statement.executeQuery("SELECT * FROM vouchers")) {
                while (set.next()) {
                    this.vouchers.add(new Voucher(set));
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    public void loadRecycler() {
        Map<Integer, Set<Item>> loadedPrizes = new HashMap<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT * FROM recycler_prizes")) {
            while (set.next()) {
                Item item = Emulator.getGameEnvironment().getItemManager().getItem(set.getInt("item_id"));

                if (item != null) {
                    loadedPrizes
                            .computeIfAbsent(set.getInt("rarity"), ignored -> new HashSet<>())
                            .add(item);
                } else {
                    LOGGER.error("Cannot load item with ID: {} as recycler reward!", set.getInt("item_id"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        replaceContents(this.prizes, loadedPrizes);
    }

    public void loadGiftWrappers() {
        Map<Integer, Integer> loadedGiftWrappers = new HashMap<>();
        Map<Integer, Integer> loadedGiftFurnis = new HashMap<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT * FROM gift_wrappers ORDER BY sprite_id DESC")) {
            while (set.next()) {
                switch (set.getString("type")) {
                    case "wrapper":
                        loadedGiftWrappers.put(set.getInt("sprite_id"), set.getInt("item_id"));
                        break;

                    case "gift":
                        loadedGiftFurnis.put(set.getInt("sprite_id"), set.getInt("item_id"));
                        break;
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        this.replaceGiftWrapping(loadedGiftWrappers, loadedGiftFurnis);
    }

    void replaceGiftWrapping(Map<Integer, Integer> loadedGiftWrappers, Map<Integer, Integer> loadedGiftFurnis) {
        synchronized (this.giftWrappers) {
            synchronized (this.giftFurnis) {
                this.giftWrappers.clear();
                this.giftFurnis.clear();
                this.giftWrappers.putAll(loadedGiftWrappers);
                this.giftFurnis.putAll(loadedGiftFurnis);
            }
        }
    }

    private void loadClothing() {
        Map<Integer, ClothItem> loadedClothing = new HashMap<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT * FROM catalog_clothing")) {
            while (set.next()) {
                loadedClothing.put(set.getInt("id"), new ClothItem(set));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        replaceContents(this.clothing, loadedClothing);
    }

    public ClothItem getClothing(String name) {
        return this.readIndex().clothing(name);
    }

    public Voucher getVoucher(String code) {
        return this.readIndex().voucher(code);
    }

    public void redeemVoucher(GameClient client, String voucherCode) {
        User user = client.getUser();
        if (user == null) return;

        Voucher voucher = Emulator.getGameEnvironment().getCatalogManager().getVoucher(voucherCode);
        if (voucher == null) {
            client.sendResponse(new RedeemVoucherErrorComposer(RedeemVoucherErrorComposer.INVALID_CODE));
            return;
        }

        // Resolve what the voucher hands out before using it up. A voucher pointing at a
        // catalogue offer that no longer exists was claimed first and then delivered
        // nothing, silently: the code was spent and the user got nothing for it.
        CatalogItem voucherItem = null;
        if (voucher.catalogItemId > 0) {
            voucherItem = this.getCatalogItem(voucher.catalogItemId);
            if (voucherItem == null) {
                LOGGER.warn(
                        "Voucher {} points at catalog item {}, which does not exist; refusing it unspent",
                        voucher.code,
                        voucher.catalogItemId);
                client.sendResponse(new RedeemVoucherErrorComposer(RedeemVoucherErrorComposer.TECHNICAL_ERROR));
                return;
            }
        }

        // A wallet that cannot take the voucher's value would refuse the grant after the code was spent.
        if (!fitsWallet(user.getUserInfo().getCredits(), voucher.credits)
                || (voucher.points > 0
                        && !fitsWallet(user.getUserInfo().getCurrencyAmount(voucher.pointsType), voucher.points))) {
            client.sendResponse(new RedeemVoucherErrorComposer(RedeemVoucherErrorComposer.TECHNICAL_ERROR));
            return;
        }

        Voucher.ClaimResult claimResult =
                voucher.claimForUser(user.getUserInfo().getId());
        switch (claimResult) {
            case CLAIMED:
                break;
            case EXHAUSTED:
                client.sendResponse(new RedeemVoucherErrorComposer(
                        Emulator.getGameEnvironment().getCatalogManager().deleteVoucher(voucher)
                                ? RedeemVoucherErrorComposer.INVALID_CODE
                                : RedeemVoucherErrorComposer.TECHNICAL_ERROR));
                return;
            case USER_LIMIT:
                client.sendResponse(
                        new ModToolIssueHandledComposer("You have exceeded the limit for redeeming this voucher."));
                return;
            case FAILED:
            default:
                client.sendResponse(new RedeemVoucherErrorComposer(RedeemVoucherErrorComposer.TECHNICAL_ERROR));
                return;
        }

        // The item is checked before the currency is given: a refused item (club only, sold out, a purchase already
        // running) used to leave the code spent, nothing delivered and an OK on screen.
        if (voucherItem != null && !this.purchaseItemDelivered(null, voucherItem, client.getUser(), 1, "", true)) {
            voucher.releaseClaim(user.getUserInfo().getId());
            return;
        }

        if (voucher.points > 0) {
            client.getUser().givePoints(voucher.pointsType, voucher.points);
        }

        if (voucher.credits > 0) {
            client.getUser().giveCredits(voucher.credits);
        }

        client.sendResponse(new RedeemVoucherOKComposer());
    }

    /** Whether a balance can take {@code amount} more without overflowing. */
    static boolean fitsWallet(int balance, int amount) {
        return amount <= 0 || (long) balance + amount <= Integer.MAX_VALUE;
    }

    public boolean deleteVoucher(Voucher voucher) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("DELETE FROM vouchers WHERE code = ?")) {
            statement.setString(1, voucher.code);

            this.forgetVoucher(voucher);

            return statement.executeUpdate() >= 1;
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return false;
    }

    /**
     * Removes {@code voucher} from the live list and bumps the catalog version, without touching
     * the database. Split out of {@link #deleteVoucher} so the in-memory half - the part that was
     * missing the {@link #markCatalogChanged()} call and left the read index serving a deleted
     * voucher - can be exercised without a live connection.
     */
    void forgetVoucher(Voucher voucher) {
        synchronized (this.vouchers) {
            this.vouchers.remove(voucher);
        }
        this.markCatalogChanged();
    }

    public CatalogPage getCatalogPage(int pageId) {
        return this.catalogPages.get(pageId);
    }

    public CatalogPage getCatalogPage(int pageId, CatalogPageType pageType) {
        return this.getCatalogPagesMap(pageType).get(pageId);
    }

    public CatalogPage getCatalogPage(String captionSafe) {
        return this.readIndex().pageByCaption(captionSafe);
    }

    public CatalogPage getCatalogPageByLayout(String layoutName) {
        return this.readIndex().pageByLayout(layoutName);
    }

    public CatalogItem getCatalogItem(int id) {
        return this.getCatalogItem(id, CatalogPageType.NORMAL);
    }

    public CatalogItem getCatalogItem(int id, CatalogPageType pageType) {
        return this.readIndex()
                .item(pageType == CatalogPageType.BUILDER ? CatalogPageType.BUILDER : CatalogPageType.NORMAL, id);
    }

    public void loadRecentPurchases(User user) {
        if (user == null) return;

        UserStats stats = user.getUserStats();
        if (stats == null || stats.isRecentPurchasesInitialized()) return;

        List<Integer> ids = stats.getRecentPurchaseIds();
        List<CatalogItem> history = new ArrayList<>();

        if (ids != null) {
            for (int id : ids) {
                CatalogItem item = this.getCatalogItem(id);
                if (item == null) continue;
                if (isPetOrBotItem(item)) continue;
                history.add(item);
            }
        }

        stats.initRecentPurchases(history);
    }

    private static boolean isPetOrBotItem(CatalogItem item) {
        if (item == null) return false;
        for (Item baseItem : item.getBaseItems()) {
            if (Item.isPet(baseItem) || Item.isBot(baseItem)) return true;
        }
        return false;
    }

    public List<CatalogPage> getCatalogPages(int parentId, final User user) {
        return this.getCatalogPages(parentId, user, CatalogPageType.NORMAL);
    }

    public List<CatalogPage> getCatalogPages(int parentId, final User user, final CatalogPageType pageType) {
        final List<CatalogPage> pages = new ArrayList<>();
        if (user == null) return pages;

        CatalogPageType mapType =
                pageType == CatalogPageType.BUILDER ? CatalogPageType.BUILDER : CatalogPageType.NORMAL;
        int userRank = user.getUserInfo().getRank().getId();
        boolean hasActiveClub = user.getUserInfo().getUserStats().hasActiveClub();

        for (CatalogPage object : this.readIndex().children(mapType, parentId)) {
            boolean isVisiblePage = object.visible;
            boolean hasRightRank = object.getRank() <= userRank;
            boolean clubRightsOkay = !object.isClubOnly() || hasActiveClub;
            boolean pageTypeMatches = (pageType == CatalogPageType.BUILDER)
                    || object.getCatalogPageType().matches(pageType);

            if (isVisiblePage && hasRightRank && clubRightsOkay && pageTypeMatches) {
                pages.add(object);
            }
        }

        return pages;
    }

    public Int2ObjectMap<CatalogFeaturedPage> getCatalogFeaturedPages() {
        return this.catalogFeaturedPages;
    }

    /** The front page slots still running, in the order of their slot id, as the client shows them (the first is the tall one). */
    public List<CatalogFeaturedPage> getCatalogFeaturedPagesSnapshot() {
        synchronized (this.catalogFeaturedPages) {
            return CatalogFeaturedPage.shown(this.catalogFeaturedPages.values(), Emulator.getIntUnixTimestamp());
        }
    }

    public GiftWrappingSnapshot getGiftWrappingSnapshot() {
        synchronized (this.giftWrappers) {
            synchronized (this.giftFurnis) {
                return new GiftWrappingSnapshot(new HashMap<>(this.giftWrappers), new HashMap<>(this.giftFurnis));
            }
        }
    }

    public Map<Integer, Set<Item>> getRecyclerPrizesSnapshot() {
        synchronized (this.prizes) {
            Map<Integer, Set<Item>> snapshot = new HashMap<>();
            for (Map.Entry<Integer, Set<Item>> entry : this.prizes.entrySet()) {
                snapshot.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }
            return snapshot;
        }
    }

    public Map<Integer, ClothItem> getClothingSnapshot() {
        synchronized (this.clothing) {
            return new HashMap<>(this.clothing);
        }
    }

    public Map<Integer, TargetOffer> getTargetOffersSnapshot() {
        synchronized (this.targetOffers) {
            return new HashMap<>(this.targetOffers);
        }
    }

    public CatalogItem getClubItem(int itemId) {
        return this.readIndex().clubItem(itemId);
    }

    public boolean moveCatalogItem(CatalogItem item, int pageId) {
        CatalogPage page = this.getCatalogPage(item.getPageId());

        if (page == null) return false;

        page.getCatalogItems().remove(item.getId());

        page = this.getCatalogPage(pageId);

        if (page == null) return false;

        page.getCatalogItems().put(item.getId(), item);

        item.setPageId(pageId);
        item.setNeedsUpdate(true);

        item.run();
        this.markCatalogChanged();
        return true;
    }

    /** The highest recycler rarity level: 6 = mythic, 5 = legendary, ..., 1 = common. */
    public static final int RECYCLER_MAX_RARITY = 6;

    /**
     * The rarity of one recycle: levels {@link #RECYCLER_MAX_RARITY} down to 2 are tried in turn, each with a 1 in N
     * chance ({@code hotel.ecotron.rarity.chance.N}); a level without a chance (0 or unset) is skipped, and when none
     * hits the prize is level 1.
     */
    static int drawRecyclerRarity(IntUnaryOperator chanceForLevel, IntUnaryOperator nextInt) {
        for (int level = RECYCLER_MAX_RARITY; level >= 2; level--) {
            int chance = chanceForLevel.applyAsInt(level);

            if (chance > 0 && nextInt.applyAsInt(chance) + 1 == chance) return level;
        }

        return 1;
    }

    public Item getRandomRecyclerPrize() {
        ConfigurationManager config = Emulator.getConfig();
        Random random = Emulator.getRandom();
        int level = drawRecyclerRarity(
                rarity -> config.getInt("hotel.ecotron.rarity.chance." + rarity, 0), random::nextInt);

        synchronized (this.prizes) {
            Set<Item> levelPrizes = this.prizes.get(level);
            if (levelPrizes != null && !levelPrizes.isEmpty()) {
                return (Item) levelPrizes.toArray()[Emulator.getRandom().nextInt(levelPrizes.size())];
            }
        }

        LOGGER.error("No rewards specified for rarity level {}", level);
        return null;
    }

    public CatalogPage createCatalogPage(
            String caption,
            String captionSave,
            int roomId,
            int icon,
            CatalogPageLayouts layout,
            int minRank,
            int parentId,
            CatalogPageType pageType,
            CatalogPageType catalogMode) {
        return createCatalogPage(
                caption, captionSave, roomId, icon, layout, minRank, parentId, pageType, catalogMode, true, true, 1);
    }

    public CatalogPage createCatalogPage(
            String caption,
            String captionSave,
            int roomId,
            int icon,
            CatalogPageLayouts layout,
            int minRank,
            int parentId,
            CatalogPageType pageType,
            CatalogPageType catalogMode,
            boolean visible,
            boolean enabled,
            int orderNum) {
        return createCatalogPage(
                caption,
                captionSave,
                roomId,
                icon,
                layout,
                minRank,
                parentId,
                pageType,
                catalogMode,
                visible,
                enabled,
                orderNum,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                1,
                false,
                false,
                "");
    }

    public CatalogPage createCatalogPage(
            String caption,
            String captionSave,
            int roomId,
            int icon,
            CatalogPageLayouts layout,
            int minRank,
            int parentId,
            CatalogPageType pageType,
            CatalogPageType catalogMode,
            boolean visible,
            boolean enabled,
            int orderNum,
            String headline,
            String teaser,
            String special,
            String textOne,
            String textTwo,
            String textDetails,
            String textTeaser,
            int iconColor,
            boolean clubOnly,
            boolean vipOnly,
            String includes) {
        CatalogPage catalogPage = null;
        boolean buildersClubPage = (pageType == CatalogPageType.BUILDER);
        String insertQuery = buildersClubPage
                ? "INSERT INTO catalog_pages_bc (parent_id, caption, page_layout, icon_color, icon_image, order_num, visible, enabled, page_headline, page_teaser, page_special, page_text1, page_text2, page_text_details, page_text_teaser) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                : "INSERT INTO catalog_pages (parent_id, caption, caption_save, icon_image, icon_color, visible, enabled, min_rank, page_layout, room_id, catalog_mode, order_num, club_only, vip_only, page_headline, page_teaser, page_special, page_text1, page_text2, page_text_details, page_text_teaser, includes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String selectQuery = buildersClubPage
                ? "SELECT id, parent_id, caption, caption AS caption_save, page_layout, icon_color, icon_image, 1 AS min_rank, order_num, visible, enabled, '0' AS club_only, 'BUILDERS_CLUB' AS catalog_mode, page_headline, page_teaser, page_special, page_text1, page_text2, page_text_details, page_text_teaser, '' AS includes FROM catalog_pages_bc WHERE id = ?"
                : "SELECT * FROM catalog_pages WHERE id = ?";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, parentId);
            statement.setString(2, caption);

            if (buildersClubPage) {
                statement.setString(3, layout.name());
                statement.setInt(4, iconColor);
                statement.setInt(5, icon);
                statement.setInt(6, orderNum < 0 ? 0 : orderNum);
                statement.setString(7, visible ? "1" : "0");
                statement.setString(8, enabled ? "1" : "0");
                statement.setString(9, headline);
                statement.setString(10, teaser);
                statement.setString(11, special);
                statement.setString(12, textOne);
                statement.setString(13, textTwo);
                statement.setString(14, textDetails);
                statement.setString(15, textTeaser);
            } else {
                statement.setString(3, captionSave);
                statement.setInt(4, icon);
                statement.setInt(5, iconColor);
                statement.setString(6, visible ? "1" : "0");
                statement.setString(7, enabled ? "1" : "0");
                statement.setInt(8, minRank);
                statement.setString(9, layout.name());
                statement.setInt(10, roomId);
                statement.setString(11, catalogMode.name());
                statement.setInt(12, orderNum < 0 ? 0 : orderNum);
                statement.setString(13, clubOnly ? "1" : "0");
                statement.setString(14, vipOnly ? "1" : "0");
                statement.setString(15, headline);
                statement.setString(16, teaser);
                statement.setString(17, special);
                statement.setString(18, textOne);
                statement.setString(19, textTwo);
                statement.setString(20, textDetails);
                statement.setString(21, textTeaser);
                statement.setString(22, includes);
            }
            statement.execute();
            try (ResultSet set = statement.getGeneratedKeys()) {
                if (set.next()) {
                    try (PreparedStatement stmt = connection.prepareStatement(selectQuery)) {
                        stmt.setInt(1, set.getInt(1));
                        try (ResultSet page = stmt.executeQuery()) {
                            if (page.next()) {
                                Class<? extends CatalogPage> pageClazz =
                                        pageDefinitions.get(page.getString("page_layout"));

                                if (pageClazz != null) {
                                    try {
                                        catalogPage = pageClazz
                                                .getConstructor(ResultSet.class)
                                                .newInstance(page);
                                    } catch (Exception e) {
                                        LOGGER.error("Caught exception", e);
                                    }
                                } else {
                                    LOGGER.error("Unknown page layout: {}", page.getString("page_layout"));
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        if (catalogPage != null) {
            this.getCatalogPagesMap(pageType).put(catalogPage.getId(), catalogPage);
            CatalogAdminCacheSync.attachCreatedPage(catalogPage, parentId, orderNum < 0 ? 0 : orderNum, pageType);
        }

        return catalogPage;
    }

    public CatalogLimitedConfiguration getLimitedConfig(CatalogItem item) {
        synchronized (this.limitedNumbers) {
            return this.limitedNumbers.get(item.getId());
        }
    }

    /** True when the item is a limited edition whose whole stock has been claimed. */
    public boolean isLimitedSoldOut(CatalogItem item) {
        if (item == null || !item.isLimited()) {
            return false;
        }

        CatalogLimitedConfiguration configuration = this.getLimitedConfig(item);

        return configuration != null && configuration.available() == 0;
    }

    public List<CatalogItem> getEffectivePageItems(CatalogPage page) {
        if (page.refreshesItemsOnRead()) {
            // RoomBundleLayout: recomputes the bundle from the live room as a side effect on
            // CatalogItem, mutating it in place. The result is discarded here - the read index's
            // cached, sorted list below stays valid because the mutation happened on the same
            // CatalogItem instance it holds a reference to.
            page.getCatalogItems();
        }

        // Which map actually owns this page - not page.getCatalogPageType(), which is a data
        // field on the page and can disagree with where the page is actually stored (e.g. a
        // BOTH-mode page reachable only from the NORMAL map). The identity check just below
        // depends on asking the right map in the first place.
        CatalogPageType mapType = this.buildersClubCatalogPages.get(page.getId()) == page
                ? CatalogPageType.BUILDER
                : CatalogPageType.NORMAL;
        List<CatalogItem> sorted = this.readIndex().sortedItems(mapType, page.getId());
        List<CatalogItem> items =
                sorted != null && this.getCatalogPagesMap(mapType).get(page.getId()) == page
                        ? new ArrayList<>(sorted)
                        : new ArrayList<>(page.getCatalogItems().values());

        int soldOutPageId = Emulator.getConfig().getInt("catalog.ltd.page.soldout");
        if (soldOutPageId <= 0) {
            return items;
        }

        if (page.getId() == soldOutPageId) {
            synchronized (this.limitedNumbers) {
                for (Map.Entry<Integer, CatalogLimitedConfiguration> entry : this.limitedNumbers.entrySet()) {
                    if (entry.getValue().available() != 0) {
                        continue;
                    }

                    CatalogItem soldOut = this.getCatalogItem(entry.getKey());
                    if (soldOut != null && soldOut.getPageId() != soldOutPageId && !items.contains(soldOut)) {
                        items.add(soldOut);
                    }
                }
            }
        } else {
            items.removeIf(this::isLimitedSoldOut);
        }

        return items;
    }

    public CatalogLimitedConfiguration createOrUpdateLimitedConfig(CatalogItem item) {
        if (!item.isLimited()) return null;

        synchronized (this.limitedNumbers) {
            CatalogLimitedConfiguration limitedConfiguration = this.limitedNumbers.get(item.getId());
            if (limitedConfiguration == null) {
                limitedConfiguration = new CatalogLimitedConfiguration(item.getId(), new LinkedList<>(), 0);
                limitedConfiguration.generateNumbers(1, item.limitedStack);
                this.limitedNumbers.put(item.getId(), limitedConfiguration);
            } else {
                if (limitedConfiguration.getTotalSet() != item.limitedStack) {
                    if (limitedConfiguration.getTotalSet() == 0) {
                        limitedConfiguration.setTotalSet(item.limitedStack);
                    } else if (item.limitedStack > limitedConfiguration.getTotalSet()) {
                        // The new numbers follow the last one made: they started after the new stack instead, so a
                        // stack raised from 100 to 150 got 151-200 and numbers 101-150 never existed.
                        limitedConfiguration.generateNumbers(
                                limitedConfiguration.getTotalSet() + 1,
                                item.limitedStack - limitedConfiguration.getTotalSet());
                    } else {
                        item.limitedStack = limitedConfiguration.getTotalSet();
                    }
                }
            }

            return limitedConfiguration;
        }
    }

    public void dispose() {
        for (CatalogPage page : this.catalogPages.values()) {
            for (CatalogItem item : page.getCatalogItems().values()) {
                item.run();
                if (item.isLimited()) {
                    this.limitedNumbers.get(item.getId()).run();
                }
            }
        }

        LOGGER.info("Catalog Manager -> Disposed!");
    }

    public void purchaseItem(
            CatalogPage page, CatalogItem item, User user, int amount, String extradata, boolean free) {
        this.purchaseItemDelivered(page, item, user, amount, extradata, free);
    }

    /**
     * {@link #purchaseItem}, saying whether the order was delivered. A refusal (club only, sold out, a purchase
     * already running, ...) answers the client itself and returns false, so a caller that already spent something
     * for it, such as a voucher, can give that back.
     */
    public boolean purchaseItemDelivered(
            CatalogPage page, CatalogItem item, User user, int amount, String extradata, boolean free) {
        LegacyPurchase purchase = new LegacyPurchase();
        this.purchaseItem(page, item, user, amount, extradata, free, purchase);
        return purchase.delivered;
    }

    private void purchaseItem(
            CatalogPage page,
            CatalogItem item,
            User user,
            int amount,
            String extradata,
            boolean free,
            LegacyPurchase purchase) {
        if (item == null) {
            user.getClient()
                    .sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
            return;
        }

        synchronized (user.getUserStats()) {
            if (user.getUserStats().isPurchasingFurniture) {
                user.getClient()
                        .sendResponse(
                                new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                return;
            }
            user.getUserStats().isPurchasingFurniture = true;
        }

        try {
            if (this.refusedBeforePricing(item, user, amount)) {
                return;
            }

            try {
                if (item.isLimited()) {
                    amount = 1;
                    if (this.limitedPurchaseRefused(item, user)) {
                        return;
                    }
                }

                amount = this.checkedAmount(page, item, user, amount);
                if (amount < 0) {
                    return;
                }

                int limitedStack = 0;
                if (item.isLimited()) {
                    purchase.limitedConfiguration = this.getLimitedConfig(item);

                    if (purchase.limitedConfiguration == null) {
                        purchase.limitedConfiguration = this.createOrUpdateLimitedConfig(item);
                    }

                    limitedStack = purchase.limitedConfiguration.getTotalSet();
                }

                int totalCredits = free ? 0 : this.calculateDiscountedPrice(item.getCredits(), amount, item);
                int totalPoints = free ? 0 : this.calculateDiscountedPrice(item.getPoints(), amount, item);

                boolean notEnoughCredits = totalCredits > user.getUserInfo().getCredits();
                boolean notEnoughPoints = totalPoints > user.getUserInfo().getCurrencyAmount(item.getPointsType());
                if (notEnoughCredits || notEnoughPoints) {
                    // The client checks the balance before it buys; this answers a balance that changed meanwhile
                    // the way Habbo does, instead of leaving the purchase waiting for a reply.
                    user.getClient()
                            .sendResponse(new NotEnoughPointsTypeComposer(
                                    notEnoughCredits, notEnoughPoints, item.getPointsType()));
                    return;
                }

                if (purchase.limitedConfiguration != null) {
                    OptionalInt limitedNumberReservation = purchase.limitedConfiguration.pollNumber();
                    if (limitedNumberReservation.isEmpty()) {
                        user.getClient().sendResponse(new AlertLimitedSoldOutComposer());
                        return;
                    }
                    purchase.limitedNumber = limitedNumberReservation.getAsInt();
                }

                if (item.getHabbiconId() > 0) {
                    if (amount != 1 || item.isLimited()) {
                        user.getClient()
                                .sendResponse(
                                        new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
                        return;
                    }
                    this.purchaseHabbiconAtomically(item, user, free, totalCredits, totalPoints);
                    purchase.delivered = true;
                    return;
                }

                if (this.isAtomicEntitlementPurchase(item)) {
                    this.purchaseEntitlementsAtomically(item, user, amount, free, totalCredits, totalPoints);
                    purchase.delivered = true;
                    return;
                }

                if (this.isAtomicBotOrPetPurchase(item)) {
                    this.purchaseBotsAndPetsAtomically(item, user, amount, extradata, free, totalCredits, totalPoints);
                    purchase.delivered = true;
                    return;
                }

                if (this.isAtomicFurniturePurchase(item)) {
                    // The atomic path owns the reserved limited number from here and gives it back itself when its
                    // transaction fails. Restoring it again below could reset a number another buyer has taken since.
                    int reservedNumber = purchase.limitedNumber;
                    purchase.limitedNumber = 0;
                    this.purchaseFurnitureAtomically(
                            item,
                            user,
                            amount,
                            extradata,
                            free,
                            purchase.limitedConfiguration,
                            limitedStack,
                            reservedNumber,
                            totalCredits,
                            totalPoints);
                    purchase.delivered = true;
                    return;
                }

                if (!this.createLegacyContents(item, user, amount, extradata, limitedStack, purchase)) {
                    return;
                }

                if (this.legacyContentsRefused(item, user, purchase)) {
                    return;
                }

                UserCatalogItemPurchasedEvent purchasedEvent =
                        this.chargeLegacyPurchase(item, user, free, totalCredits, totalPoints, purchase);
                if (purchasedEvent == null) {
                    return;
                }

                this.deliverLegacyPurchase(item, user, amount, free, purchasedEvent, purchase);
            } catch (Exception e) {
                LOGGER.error("Exception caught", e);
                user.getClient()
                        .sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            }
        } finally {
            try {
                if (!purchase.delivered) {
                    this.undoLegacyPurchase(item, user, purchase);
                }
            } finally {
                synchronized (user.getUserStats()) {
                    user.getUserStats().isPurchasingFurniture = false;
                }
            }
        }
    }

    /**
     * What an offer being bought collects before it is paid for and handed over; on a failure it says what to undo.
     * Used by the purchase path that no atomic transaction covers (mixed bundles and offers without base items).
     */
    private static final class LegacyPurchase {
        final Set<UserItem> createdItems = new HashSet<>();
        final List<Bot> createdBots = new ArrayList<>();
        final List<Pet> createdPets = new ArrayList<>();
        // Effect id and duration (seconds, 0 permanent) per bought effect.
        final List<int[]> pendingEffects = new ArrayList<>();
        final List<Guild> pendingGuildForums = new ArrayList<>();
        final Set<UserItem> itemsList = new HashSet<>();
        final List<String> badges = new ArrayList<>();
        final Map<AddUserItemComposer.AddUserItemCategory, List<Integer>> unseenItems = new HashMap<>();
        boolean badgeFound = false;
        boolean paymentTaken = false;
        boolean delivered = false;
        int paidCredits = 0;
        int paidPoints = 0;
        int paidPointsType = 0;
        CatalogLimitedConfiguration limitedConfiguration = null;
        int limitedNumber = 0;
    }

    /**
     * Refuses an offer the buyer may not have at all: club only without club, no amount, an offer a wired web api
     * keeps, or a chat bubble style the buyer already owns. Tells the buyer why.
     */
    private boolean refusedBeforePricing(CatalogItem item, User user, int amount) {
        if (item.isClubOnly() && !user.getClient().getUser().getUserStats().hasActiveClub()) {
            user.getClient()
                    .sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.REQUIRES_CLUB));
            return true;
        }

        if (amount <= 0) {
            user.getClient()
                    .sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return true;
        }

        // The catalog checks the inventory size before it gets here, but vouchers, targeted offers and the limited
        // raffle deliver through this method directly and went past it.
        if (inventoryOverCap(user)) {
            user.getClient()
                    .sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
            return true;
        }

        String ownershipRefusal = WiredWebApiOwnership.purchaseRefusal(
                item, amount, user.getUserInfo().getId());
        if (ownershipRefusal != null) {
            user.getClient().sendResponse(new BubbleAlertComposer(ownershipRefusal));
            user.getClient()
                    .sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return true;
        }

        // An offer that only sells a chat bubble style cannot be bought again by its owner.
        int soldChatStyle = ChatStyleRepository.styleForCatalogItem(item.getId());
        if (soldChatStyle > 0
                && item.getBaseItems().isEmpty()
                && ChatStyleRepository.owns(user.getUserInfo().getId(), soldChatStyle)) {
            user.getClient()
                    .sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return true;
        }

        return false;
    }

    /** Whether the buyer's inventory is over its size, or cannot be read to tell. */
    static boolean inventoryOverCap(User user) {
        try {
            return user.getInventory().getItemsComponent().itemCount() > UserInventory.MAXIMUM_ITEMS;
        } catch (RuntimeException e) {
            LOGGER.error(
                    "Purchase refused for user {}: their inventory could not be read",
                    user.getUserInfo().getId(),
                    e);
            return true;
        }
    }

    /** Refuses a limited edition that is sold out or over the buyer's daily limits. */
    private boolean limitedPurchaseRefused(CatalogItem item, User user) {
        if (this.getLimitedConfig(item).available() == 0) {
            user.getClient().sendResponse(new AlertLimitedSoldOutComposer());
            return true;
        }

        if (Emulator.getConfig().getBoolean("hotel.catalog.ltd.limit.enabled")) {
            int ltdLimit = Emulator.getConfig().getInt("hotel.purchase.ltd.limit.daily.total");
            if (user.getUserStats().totalLtds() >= ltdLimit) {
                user.alert(Emulator.getTexts()
                        .getValue("error.catalog.buy.limited.daily.total")
                        .replace(
                                "%itemname%",
                                item.getBaseItems().iterator().next().getDisplayName())
                        .replace("%limit%", ltdLimit + ""));
                this.refuseAfterLimitAlert(user);
                return true;
            }

            ltdLimit = Emulator.getConfig().getInt("hotel.purchase.ltd.limit.daily.item");
            if (user.getUserStats().totalLtds(item.id) >= ltdLimit) {
                user.alert(Emulator.getTexts()
                        .getValue("error.catalog.buy.limited.daily.item")
                        .replace(
                                "%itemname%",
                                item.getBaseItems().iterator().next().getDisplayName())
                        .replace("%limit%", ltdLimit + ""));
                this.refuseAfterLimitAlert(user);
                return true;
            }
        }

        return false;
    }

    /**
     * Ends the purchase the client is waiting for after a daily limit alert: without a reply its purchase button
     * stays busy until the client gives up on its own.
     */
    private void refuseAfterLimitAlert(User user) {
        user.getClient().sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
    }

    /**
     * The number of times the offer is bought: asking for exactly the pack size buys the pack once. Returns -1, after
     * telling the buyer, for more than 100 items at once or several of an offer without a bulk discount.
     */
    private int checkedAmount(CatalogPage page, CatalogItem item, User user, int amount) {
        if (amount > 1) {
            if (amount == item.getAmount()) {
                amount = 1;
            } else {
                long requestedItems = (long) amount * item.getAmount();
                if (requestedItems > 100) {
                    user.alert(
                            "Whoops! You tried to buy this " + requestedItems + " times. This must've been a mistake.");
                    user.getClient()
                            .sendResponse(
                                    new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
                    return -1;
                }
            }
        }

        if (amount > 1 && !CatalogItem.haveOffer(item)) {
            String message = Emulator.getTexts()
                    .getValue("scripter.warning.catalog.amount")
                    .replace("%username%", user.getUserInfo().getUsername())
                    .replace("%itemname%", item.getName())
                    // A purchase without a page (targeted offers, vouchers, rewards) has no caption to name.
                    .replace("%pagename%", page == null ? "" : page.getCaption());
            ScripterManager.scripterDetected(user.getClient(), message);
            LOGGER.info(message);
            user.getClient()
                    .sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return -1;
        }

        return amount;
    }

    /**
     * Creates what a legacy offer holds, amount times: bots, pets and furniture are created right away, effects and
     * badges are only noted. Returns false, after telling the buyer, when one part cannot be made.
     */
    private boolean createLegacyContents(
            CatalogItem item, User user, int amount, String extradata, int limitedStack, LegacyPurchase purchase)
            throws Exception {
        for (int i = 0; i < amount; i++) {
            for (Item baseItem : item.getBaseItems()) {
                // Engraved once per base item, from what the buyer sent, as the atomic furniture path does.
                String furnitureExtradata = null;
                for (int k = 0; k < item.getItemAmount(baseItem.getId()); k++) {
                    if (baseItem.getName().startsWith("rentable_bot_")
                            || baseItem.getName().startsWith("bot_")) {
                        if (!this.createLegacyBot(item, user, baseItem, purchase)) {
                            return false;
                        }
                    } else if (baseItem.getType() == FurnitureType.EFFECT) {
                        int effectId = baseItem.getEffectM();

                        if (user.getUserInfo().getGender().equals(UserGender.F)) {
                            effectId = baseItem.getEffectF();
                        }

                        if (effectId > 0) {
                            purchase.pendingEffects.add(new int[] {effectId, item.getEffectDuration()});
                        }
                    } else if (Item.isPet(baseItem)) {
                        if (!this.createLegacyPet(user, baseItem, extradata, purchase)) {
                            return false;
                        }
                    } else if (baseItem.getType() == FurnitureType.BADGE) {
                        if (!user.getInventory().getBadgesComponent().hasBadge(baseItem.getName())) {
                            if (!purchase.badges.contains(baseItem.getName())) {
                                purchase.badges.add(baseItem.getName());
                            }
                        } else {
                            purchase.badgeFound = true;
                        }
                    } else {
                        if (furnitureExtradata == null) {
                            furnitureExtradata = this.prepareFurnitureExtraData(user, baseItem, extradata);
                        }

                        if (!this.createLegacyFurniture(
                                item, user, baseItem, furnitureExtradata, limitedStack, purchase)) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    /** Creates one bot of the offer into the buyer's inventory; Frank needs his permission. */
    private boolean createLegacyBot(CatalogItem item, User user, Item baseItem, LegacyPurchase purchase)
            throws Exception {
        String baseName = baseItem.getName();
        String type = item.getName().replace("rentable_bot_", "");
        type = type.replace("bot_", "");
        type = type.replace("visitor_logger", "visitor_log");

        if (("bot_" + polaris.hotel.bots.FrankBot.BOT_TYPE).equals(baseName)
                || ("rentable_bot_" + polaris.hotel.bots.FrankBot.BOT_TYPE).equals(baseName)) {
            if (!user.getClient().getUser().hasPermission(polaris.hotel.bots.FrankBot.PERMISSION_USE)) {
                user.getClient()
                        .sendResponse(
                                new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                return false;
            }
        }

        Map<String, String> data = new HashMap<>();

        for (String s : item.getExtradata().split(";")) {
            if (s.contains(":")) {
                data.put(s.split(":")[0], s.split(":")[1]);
            }
        }

        Bot bot = Emulator.getGameEnvironment()
                .getBotManager()
                .createBot(data, type, user.getUserInfo().getId());

        if (bot == null) {
            throw new Exception("Failed to create bot of type: " + type);
        }

        purchase.createdBots.add(bot);
        bot.setOwnerId(user.getClient().getUser().getUserInfo().getId());
        bot.setOwnerName(user.getClient().getUser().getUserInfo().getUsername());
        bot.needsUpdate(true);
        Emulator.getThreading().run(bot);
        purchase.unseenItems
                .computeIfAbsent(AddUserItemComposer.AddUserItemCategory.BOT, ignored -> new ArrayList<>())
                .add(bot.getId());
        return true;
    }

    /** Creates one pet of the offer from the name, race and colour the buyer chose. */
    private boolean createLegacyPet(User user, Item baseItem, String extradata, LegacyPurchase purchase) {
        String[] data = extradata.split("\n");

        if (data.length < 3) {
            user.getClient().sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return false;
        }

        Pet pet = null;
        try {
            pet = Emulator.getGameEnvironment()
                    .getPetManager()
                    .createPet(baseItem, data[0], data[1], data[2], user.getClient());
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        if (pet == null) {
            user.getClient().sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return false;
        }

        purchase.createdPets.add(pet);
        purchase.unseenItems
                .computeIfAbsent(AddUserItemComposer.AddUserItemCategory.PET, ignored -> new ArrayList<>())
                .add(pet.getId());
        return true;
    }

    /**
     * Creates one piece of furniture of the offer: a linked teleport pair, a hopper, guild furniture tied to its
     * guild, a music disc with its track, or a plain item. Returns false, after telling the buyer, when the guild id
     * or the track is wrong, or a guild's forum terminal may not be bought.
     */
    private boolean createLegacyFurniture(
            CatalogItem item, User user, Item baseItem, String extradata, int limitedStack, LegacyPurchase purchase) {
        ItemManager itemManager = Emulator.getGameEnvironment().getItemManager();
        int ownerId = user.getClient().getUser().getUserInfo().getId();
        int limitedNumber = purchase.limitedNumber;

        if (InteractionTeleport.class.isAssignableFrom(
                baseItem.getInteractionType().getType())) {
            UserItem teleportOne = itemManager.createItem(ownerId, baseItem, limitedStack, limitedNumber, extradata);
            UserItem teleportTwo = itemManager.createItem(ownerId, baseItem, limitedStack, limitedNumber, extradata);
            if (teleportOne == null || teleportTwo == null) {
                if (teleportOne != null) purchase.createdItems.add(teleportOne);
                if (teleportTwo != null) purchase.createdItems.add(teleportTwo);
                throw new IllegalStateException("failed to create catalog teleport pair");
            }
            purchase.createdItems.add(teleportOne);
            purchase.createdItems.add(teleportTwo);
            itemManager.insertTeleportPair(teleportOne.getId(), teleportTwo.getId());
            purchase.itemsList.add(teleportOne);
            purchase.itemsList.add(teleportTwo);
        } else if (baseItem.getInteractionType().getType() == InteractionHopper.class) {
            UserItem hopper = itemManager.createItem(ownerId, baseItem, limitedStack, limitedNumber, extradata);

            if (hopper == null) throw new IllegalStateException("failed to create catalog hopper");
            purchase.createdItems.add(hopper);

            itemManager.insertHopper(hopper);

            purchase.itemsList.add(hopper);
        } else if (baseItem.getInteractionType().getType() == InteractionGuildFurni.class
                || baseItem.getInteractionType().getType() == InteractionGuildGate.class) {
            return this.createLegacyGuildFurniture(user, baseItem, extradata, limitedStack, purchase);
        } else if (baseItem.getInteractionType().getType() == InteractionMusicDisc.class) {
            SoundTrack track = itemManager.getSoundTrack(item.getExtradata());

            if (track == null) {
                user.getClient()
                        .sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                return false;
            }

            UserItem createdItem = itemManager.createItem(
                    ownerId, baseItem, limitedStack, limitedNumber, this.createMusicDiscExtraData(user, track));
            if (!(createdItem instanceof InteractionMusicDisc)) {
                if (createdItem != null) purchase.createdItems.add(createdItem);
                throw new IllegalStateException("failed to create catalog music disc");
            }
            InteractionMusicDisc userItem = (InteractionMusicDisc) createdItem;
            purchase.createdItems.add(userItem);
            userItem.needsUpdate(true);

            Emulator.getThreading().run(userItem);
            purchase.itemsList.add(userItem);

            AchievementManager.progressAchievement(
                    user, Emulator.getGameEnvironment().getAchievementManager().getAchievement("MusicCollector"));
        } else {
            UserItem userItem = itemManager.createItem(ownerId, baseItem, limitedStack, limitedNumber, extradata);
            if (userItem == null) throw new IllegalStateException("failed to create catalog item");
            purchase.createdItems.add(userItem);
            purchase.itemsList.add(userItem);
        }

        return true;
    }

    /**
     * Creates guild furniture for the guild id the buyer sent, if they are a member; a forum terminal follows the
     * guild's forum rules and opens the forum once paid.
     */
    private boolean createLegacyGuildFurniture(
            User user, Item baseItem, String extradata, int limitedStack, LegacyPurchase purchase) {
        int guildId;
        try {
            guildId = Integer.parseInt(extradata);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
            user.getClient().sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return false;
        }

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

        if (guild == null || Emulator.getGameEnvironment().getGuildManager().getGuildMember(guild, user) == null) {
            // Guild furniture is sold to members of its guild only, as on the atomic furniture path.
            user.getClient().sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return false;
        }

        if (GuildForumPurchasePolicy.isForumTerminal(baseItem.getName())
                && !GuildForumPurchasePolicy.mayBuy(
                        guild.hasForum(),
                        guild.getOwnerId(),
                        user.getUserInfo().getId(),
                        user.getUserInfo().getRank().getLevel())) {
            user.getClient().sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return false;
        }

        UserItem createdItem = Emulator.getGameEnvironment()
                .getItemManager()
                .createItem(
                        user.getClient().getUser().getUserInfo().getId(),
                        baseItem,
                        limitedStack,
                        purchase.limitedNumber,
                        extradata);
        if (!(createdItem instanceof InteractionGuildFurni)) {
            if (createdItem != null) purchase.createdItems.add(createdItem);
            throw new IllegalStateException("failed to create catalog guild item");
        }
        InteractionGuildFurni userItem = (InteractionGuildFurni) createdItem;
        purchase.createdItems.add(userItem);
        userItem.setExtradata("");
        userItem.needsUpdate(true);

        Emulator.getThreading().run(userItem);
        Emulator.getGameEnvironment().getGuildManager().setGuild(userItem, guildId);
        purchase.itemsList.add(userItem);

        if (baseItem.getName().equals("guild_forum")) {
            purchase.pendingGuildForums.add(guild);
        }

        return true;
    }

    /** Refuses a legacy offer whose effect the buyer owns for good, or whose only part is a badge they have. */
    private boolean legacyContentsRefused(CatalogItem item, User user, LegacyPurchase purchase) {
        // A permanent effect cannot be bought again.
        for (int[] effect : purchase.pendingEffects) {
            if (user.getInventory().getEffectsComponent().ownsPermanently(effect[0])) {
                user.getClient()
                        .sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
                return true;
            }
        }

        if (purchase.badgeFound && item.getBaseItems().size() == 1) {
            user.getClient()
                    .sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.ALREADY_HAVE_BADGE));
            return true;
        }

        return false;
    }

    /**
     * Lets plugins see and reprice the purchase, then takes the payment. Returns the event the delivery follows, or
     * null, after telling the buyer, when the new price is over their balance or the payment is refused.
     */
    private UserCatalogItemPurchasedEvent chargeLegacyPurchase(
            CatalogItem item, User user, boolean free, int totalCredits, int totalPoints, LegacyPurchase purchase) {
        UserCatalogItemPurchasedEvent purchasedEvent = new UserCatalogItemPurchasedEvent(
                user, item, purchase.itemsList, totalCredits, totalPoints, purchase.badges);
        this.fireCatalogPurchaseEvent(purchasedEvent);

        CatalogPurchaseMath.requireNonNegative(purchasedEvent.totalCredits, "plugin-adjusted credit price");
        CatalogPurchaseMath.requireNonNegative(purchasedEvent.totalPoints, "plugin-adjusted points price");

        if (purchasedEvent.totalCredits > user.getUserInfo().getCredits()
                || purchasedEvent.totalPoints > user.getUserInfo().getCurrencyAmount(item.getPointsType())) {
            user.getClient()
                    .sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return null;
        }

        purchase.paidCredits =
                (!free && !user.hasPermission(Permission.ACC_INFINITE_CREDITS)) ? purchasedEvent.totalCredits : 0;
        purchase.paidPoints =
                (!free && !user.hasPermission(Permission.ACC_INFINITE_POINTS)) ? purchasedEvent.totalPoints : 0;
        purchase.paidPointsType = item.getPointsType();

        if (!CatalogPaymentService.tryTake(user, purchase.paidCredits, purchase.paidPointsType, purchase.paidPoints)) {
            user.getClient()
                    .sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return null;
        }
        purchase.paymentTaken = true;
        return purchasedEvent;
    }

    /**
     * Hands a paid legacy purchase over: bots and pets, effects, guild forums, furniture (rented when the offer rents), badges,
     * then the buyer's packets, quests and the purchase log. Furniture a plugin took out of the purchase is deleted.
     */
    private void deliverLegacyPurchase(
            CatalogItem item,
            User user,
            int amount,
            boolean free,
            UserCatalogItemPurchasedEvent purchasedEvent,
            LegacyPurchase purchase) {
        this.recordLegacyLimitedSale(item, user, purchasedEvent, purchase);

        // Bots and pets reach the buyer's inventory only now that the purchase is paid: on a failure before this
        // the buyer never sees them, as with the atomic bot and pet path.
        for (Bot bot : purchase.createdBots) {
            user.getClient().getUser().getInventory().getBotsComponent().addBot(bot);
            user.getClient().sendResponse(new AddBotComposer(bot));
        }
        for (Pet pet : purchase.createdPets) {
            user.getClient().getUser().getInventory().getPetsComponent().addPet(pet);
            user.getClient().sendResponse(new AddPetComposer(pet));
            user.getClient().sendResponse(new PetBoughtNotificationComposer(pet, false));

            AchievementManager.progressAchievement(
                    user.getClient().getUser(),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("PetLover"));
        }
        for (int[] effect : purchase.pendingEffects) {
            user.getInventory().getEffectsComponent().createEffect(effect[0], effect[1]);
        }
        for (Guild guild : purchase.pendingGuildForums) {
            guild.setForum(true);
            guild.needsUpdate = true;
            guild.run();
        }

        Map<AddUserItemComposer.AddUserItemCategory, List<Integer>> unseenItems = purchase.unseenItems;
        if (purchasedEvent.itemsList != null && !purchasedEvent.itemsList.isEmpty()) {
            if (item.isRentOffer()) {
                int expires = RentableFurniture.expiresAfter(Emulator.getIntUnixTimestamp(), item.getRentDays());
                for (UserItem rented : purchasedEvent.itemsList) {
                    rented.setExpiresTimestamp(expires);
                    rented.needsUpdate(true);
                    Emulator.getThreading().run(rented);
                    RentableFurnitureManager.track(rented);
                }
            }
            user.getClient().getUser().getInventory().getItemsComponent().addItems(purchasedEvent.itemsList);
            unseenItems.put(
                    AddUserItemComposer.AddUserItemCategory.OWNED_FURNI,
                    purchasedEvent.itemsList.stream().map(UserItem::getId).collect(Collectors.toList()));

            Emulator.getPluginManager()
                    .fireEvent(new UserCatalogFurnitureBoughtEvent(user, item, purchasedEvent.itemsList));
        }

        if (!purchasedEvent.badges.isEmpty()
                && !unseenItems.containsKey(AddUserItemComposer.AddUserItemCategory.BADGE)) {
            unseenItems.put(AddUserItemComposer.AddUserItemCategory.BADGE, new ArrayList<>());
        }

        for (String b : purchasedEvent.badges) {
            UserBadge badge = BadgesComponent.createBadge(b, user);
            if (badge == null) continue;
            user.getClient().sendResponse(new AddUserBadgeComposer(badge));
            Map<String, String> keys = new HashMap<>();
            keys.put("display", "BUBBLE");
            keys.put("image", "${image.library.url}album1584/" + badge.getCode() + ".gif");
            keys.put("message", Emulator.getTexts().getValue("commands.generic.cmd_badge.received"));
            user.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys));
            unseenItems.get(AddUserItemComposer.AddUserItemCategory.BADGE).add(badge.getId());
        }

        // Durable items and wallet debit now agree. Nonessential response,
        // achievement, and logging failures must not undo a delivered order.
        purchase.delivered = true;

        if (item.isLimited()) {
            for (int i = 0; i < amount; i++) {
                user.getUserStats().addLtdLog(item.getId(), Emulator.getIntUnixTimestamp());
            }
        }

        for (UserItem createdItem : purchase.createdItems) {
            if (purchasedEvent.itemsList == null || !purchasedEvent.itemsList.contains(createdItem)) {
                Emulator.getGameEnvironment().getItemManager().deleteItem(createdItem);
            }
        }
        if (!isPetOrBotItem(purchasedEvent.catalogItem)) {
            user.getClient().getUser().getUserStats().addPurchase(purchasedEvent.catalogItem);
        }

        user.getClient().sendResponse(new AddUserItemComposer(unseenItems));

        user.getClient().sendResponse(new PurchaseOKComposer(purchasedEvent.catalogItem));
        progressFurniturePurchase(user, purchasedEvent.catalogItem);
        ChatStyleRepository.grantForPurchase(user, purchasedEvent.catalogItem);
        user.getClient().sendResponse(new InventoryRefreshComposer());

        Set<String> itemIds = new HashSet<>();

        for (UserItem ix : purchasedEvent.itemsList) {
            itemIds.add(ix.getId() + "");
        }

        if (!free) {
            Emulator.getThreading()
                    .run(new CatalogPurchaseLogEntry(
                            Emulator.getIntUnixTimestamp(),
                            purchasedEvent.user.getUserInfo().getId(),
                            purchasedEvent.catalogItem != null ? purchasedEvent.catalogItem.getId() : 0,
                            String.join(";", itemIds),
                            purchasedEvent.catalogItem != null ? purchasedEvent.catalogItem.getName() : "",
                            purchasedEvent.totalCredits,
                            purchasedEvent.totalPoints,
                            item.getPointsType(),
                            amount));
        }
    }

    /**
     * Undoes a purchase that was not delivered: deletes what was created, takes bots and pets back out of the
     * inventory, returns a reserved limited number to the pool and refunds a payment already taken.
     */
    /**
     * One number, one sale, written before anything reaches the buyer, as the atomic path does: the sale used to be
     * recorded after delivery for every item, with its SQL errors swallowed, so a failed write left the number free in
     * the database (sold again after a reload) and the second half of a teleporter pair always failed. A failure
     * now undoes the purchase. Once written, the number is the buyer's and is not given back.
     */
    private void recordLegacyLimitedSale(
            CatalogItem item, User user, UserCatalogItemPurchasedEvent purchasedEvent, LegacyPurchase purchase) {
        if (purchase.limitedConfiguration == null || purchasedEvent.itemsList == null) return;

        UserItem limitedItem = null;
        for (UserItem candidate : purchasedEvent.itemsList) {
            if (limitedItem == null || candidate.getId() < limitedItem.getId()) limitedItem = candidate;
        }
        if (limitedItem == null) return;

        try {
            purchase.limitedConfiguration.limitedSoldOrThrow(item.getId(), user, limitedItem);
        } catch (SQLException e) {
            throw new IllegalStateException("Limited sale of offer " + item.getId() + " could not be recorded", e);
        }
        purchase.limitedNumber = 0;
    }

    private void undoLegacyPurchase(CatalogItem item, User user, LegacyPurchase purchase) {
        for (UserItem createdItem : purchase.createdItems) {
            if (createdItem == null) continue;
            // The rows go and so do the items: a failure after delivery left items in the inventory whose rows
            // had been deleted.
            user.getInventory().getItemsComponent().removeUserItem(createdItem.getId());
            Emulator.getGameEnvironment().getItemManager().deleteItem(createdItem);
        }
        for (Bot bot : purchase.createdBots) {
            user.getInventory().getBotsComponent().removeBot(bot);
            Emulator.getGameEnvironment().getBotManager().deleteBot(bot);
        }
        for (Pet pet : purchase.createdPets) {
            user.getInventory().getPetsComponent().removePet(pet);
            Emulator.getGameEnvironment().getPetManager().deletePet(pet);
        }
        CatalogLimitedConfiguration limitedConfiguration = purchase.limitedConfiguration;
        int limitedNumber = purchase.limitedNumber;
        if (limitedConfiguration != null && limitedNumber > 0) {
            // Return the reserved limited number to the pool so a failed
            // purchase does not permanently shrink the stock or leave the
            // item stranded on the sold-out page.
            limitedConfiguration.restoreNumber(item.getId(), limitedNumber);
        }
        if (purchase.paymentTaken) {
            CatalogPaymentService.refund(user, purchase.paidCredits, purchase.paidPointsType, purchase.paidPoints);
        }
    }

    private boolean isAtomicFurniturePurchase(CatalogItem item) {
        if (item == null || item.getBaseItems().isEmpty()) return false;
        // A rent offer takes the delivery that sets the expiry and tracks it; the atomic path does neither.
        if (item.isRentOffer()) return false;
        for (Item baseItem : item.getBaseItems()) {
            if (baseItem == null
                    || Item.isBot(baseItem)
                    || Item.isPet(baseItem)
                    || baseItem.getType() == FurnitureType.BADGE
                    || baseItem.getType() == FurnitureType.EFFECT) {
                return false;
            }
        }
        return true;
    }

    private boolean isAtomicEntitlementPurchase(CatalogItem item) {
        if (item == null || item.getBaseItems().isEmpty()) return false;
        for (Item baseItem : item.getBaseItems()) {
            if (baseItem == null
                    || (baseItem.getType() != FurnitureType.BADGE && baseItem.getType() != FurnitureType.EFFECT))
                return false;
        }
        return true;
    }

    private boolean isAtomicBotOrPetPurchase(CatalogItem item) {
        if (item == null || item.getBaseItems().isEmpty()) return false;
        for (Item baseItem : item.getBaseItems()) {
            if (baseItem == null || (!Item.isBot(baseItem) && !Item.isPet(baseItem))) return false;
            if (Item.isPet(baseItem)) {
                try {
                    int type = Integer.parseInt(baseItem.getName().toLowerCase().replace("a0 pet", ""));
                    if (type == 16) return false;
                } catch (NumberFormatException exception) {
                    return false;
                }
            }
        }
        return true;
    }

    private void purchaseBotsAndPetsAtomically(
            CatalogItem item, User user, int amount, String extraData, boolean free, int totalCredits, int totalPoints)
            throws SQLException {
        String[] petData = extraData.split("\n");
        String operationId =
                EconomyOperationId.create("catalog:" + user.getUserInfo().getId() + ":" + item.getId());
        SpecialCompanionPurchase purchase = CatalogPurchaseTransaction.execute(user, operationId, connection -> {
            List<Bot> bots = new ArrayList<>();
            List<Pet> pets = new ArrayList<>();
            for (int index = 0; index < amount; index++) {
                for (Item baseItem : item.getBaseItems()) {
                    for (int count = 0; count < item.getItemAmount(baseItem.getId()); count++) {
                        if (Item.isBot(baseItem)) {
                            String baseName = baseItem.getName();
                            String type = item.getName()
                                    .replace("rentable_bot_", "")
                                    .replace("bot_", "")
                                    .replace("visitor_logger", "visitor_log");
                            if (("bot_" + polaris.hotel.bots.FrankBot.BOT_TYPE).equals(baseName)
                                    || ("rentable_bot_" + polaris.hotel.bots.FrankBot.BOT_TYPE).equals(baseName)) {
                                if (!user.hasPermission(polaris.hotel.bots.FrankBot.PERMISSION_USE)) {
                                    throw new SQLException("Missing permission for Frank bot");
                                }
                            }
                            Map<String, String> data = new HashMap<>();
                            for (String value : item.getExtradata().split(";")) {
                                String[] pair = value.split(":", 2);
                                if (pair.length == 2) data.put(pair[0], pair[1]);
                            }
                            Bot bot = Emulator.getGameEnvironment()
                                    .getBotManager()
                                    .createBot(
                                            connection,
                                            data,
                                            type,
                                            user.getUserInfo().getId());
                            if (bot == null) throw new SQLException("Unable to create catalog bot");
                            bots.add(bot);
                        } else {
                            if (petData.length < 3) throw new SQLException("Invalid catalog pet data");

                            PetManager petManager =
                                    Emulator.getGameEnvironment().getPetManager();

                            try {
                                int petType = Integer.parseInt(
                                        baseItem.getName().toLowerCase().replace("a0 pet", ""));
                                int breedColor = Integer.parseInt(petData[1]);
                                if (petManager.isClubOnlyBreed(petType, breedColor)
                                        && !user.getUserStats().hasActiveClub()) {
                                    throw new SQLException("HC required for club-only pet breed");
                                }
                            } catch (NumberFormatException ignored) {
                            }

                            Pet pet = petManager.createPet(
                                    connection, baseItem, petData[0], petData[1], petData[2], user.getClient());
                            if (pet == null) throw new SQLException("Unable to create catalog pet");
                            pets.add(pet);
                        }
                    }
                }
            }

            UserCatalogItemPurchasedEvent event = new UserCatalogItemPurchasedEvent(
                    user, item, new HashSet<>(), totalCredits, totalPoints, new ArrayList<>());
            this.fireCatalogPurchaseEvent(event);
            ResolvedCatalogCharges charges = this.resolveCatalogCharges(user, item, free, event);
            if (!free) this.writePurchaseLog(connection, event, charges, amount);
            SpecialCompanionPurchase result = new SpecialCompanionPurchase(
                    event, bots, pets, charges.credits(), charges.points(), charges.pointsType());
            return new CatalogPurchaseTransaction.PreparedPurchase<>(
                    result, charges.credits(), charges.points(), charges.pointsType());
        });

        this.publishCommittedCharges(user, purchase.credits(), purchase.points(), purchase.pointsType());
        Map<AddUserItemComposer.AddUserItemCategory, List<Integer>> unseenItems = new HashMap<>();
        for (Bot bot : purchase.bots()) {
            user.getInventory().getBotsComponent().addBot(bot);
            user.getClient().sendResponse(new AddBotComposer(bot));
            unseenItems
                    .computeIfAbsent(AddUserItemComposer.AddUserItemCategory.BOT, ignored -> new ArrayList<>())
                    .add(bot.getId());
        }
        for (Pet pet : purchase.pets()) {
            user.getInventory().getPetsComponent().addPet(pet);
            user.getClient().sendResponse(new AddPetComposer(pet));
            user.getClient().sendResponse(new PetBoughtNotificationComposer(pet, false));
            AchievementManager.progressAchievement(
                    user, Emulator.getGameEnvironment().getAchievementManager().getAchievement("PetLover"));
            unseenItems
                    .computeIfAbsent(AddUserItemComposer.AddUserItemCategory.PET, ignored -> new ArrayList<>())
                    .add(pet.getId());
        }
        if (!isPetOrBotItem(purchase.event().catalogItem)) {
            user.getUserStats().addPurchase(purchase.event().catalogItem);
        }
        user.getClient().sendResponse(new AddUserItemComposer(unseenItems));
        user.getClient().sendResponse(new PurchaseOKComposer(purchase.event().catalogItem));
        ChatStyleRepository.grantForPurchase(user, purchase.event().catalogItem);
        user.getClient().sendResponse(new InventoryRefreshComposer());
    }

    private void purchaseHabbiconAtomically(
            CatalogItem item, User user, boolean free, int totalCredits, int totalPoints) throws SQLException {
        String operationId =
                EconomyOperationId.create("catalog:" + user.getUserInfo().getId() + ":" + item.getId());
        HabbiconService.Change change = CatalogPurchaseTransaction.execute(user, operationId, connection -> {
            UserCatalogItemPurchasedEvent event = new UserCatalogItemPurchasedEvent(
                    user, item, new HashSet<>(), totalCredits, totalPoints, new ArrayList<>());
            this.fireCatalogPurchaseEvent(event);
            ResolvedCatalogCharges charges = this.resolveCatalogCharges(user, item, free, event);
            try (PreparedStatement statement =
                    connection.prepareStatement("SELECT id FROM users WHERE id = ? FOR UPDATE")) {
                statement.setInt(1, user.getUserInfo().getId());
                try (ResultSet ignored = statement.executeQuery()) {
                    if (!ignored.next()) {
                        throw new SQLException("Unknown Habbicon purchaser");
                    }
                }
            }
            HabbiconService.Change granted = user.getHabbiconService()
                    .grantCatalog(connection, user.getUserInfo().getId(), item.getHabbiconId());
            if (!free) {
                this.writePurchaseLog(connection, event, charges, 1);
            }
            return new CatalogPurchaseTransaction.PreparedPurchase<>(
                    granted, charges.credits(), charges.points(), charges.pointsType());
        });
        HabbiconService.publish(user, change);
        user.getClient().sendResponse(new UserCreditsComposer(user));
        user.getClient().sendResponse(new polaris.messages.outgoing.users.UserCurrencyComposer(user));
        user.getUserStats().addPurchase(item);
        user.getClient().sendResponse(new PurchaseOKComposer(item));
    }

    private void purchaseEntitlementsAtomically(
            CatalogItem item, User user, int amount, boolean free, int totalCredits, int totalPoints)
            throws SQLException {
        List<String> requestedBadges = new ArrayList<>();
        Map<Integer, Integer> requestedEffects = new HashMap<>();
        for (int index = 0; index < amount; index++) {
            for (Item baseItem : item.getBaseItems()) {
                int count = item.getItemAmount(baseItem.getId());
                if (baseItem.getType() == FurnitureType.BADGE) {
                    if (user.getInventory().getBadgesComponent().hasBadge(baseItem.getName())) {
                        if (item.getBaseItems().size() == 1) {
                            user.getClient()
                                    .sendResponse(new AlertPurchaseFailedComposer(
                                            AlertPurchaseFailedComposer.ALREADY_HAVE_BADGE));
                            return;
                        }
                    } else if (!requestedBadges.contains(baseItem.getName())) {
                        requestedBadges.add(baseItem.getName());
                    }
                } else {
                    int effectId = user.getUserInfo().getGender() == UserGender.F
                            ? baseItem.getEffectF()
                            : baseItem.getEffectM();
                    if (effectId > 0) requestedEffects.merge(effectId, count, Integer::sum);
                }
            }
        }

        String operationId =
                EconomyOperationId.create("catalog:" + user.getUserInfo().getId() + ":" + item.getId());
        EntitlementPurchase purchase = CatalogPurchaseTransaction.execute(user, operationId, connection -> {
            UserCatalogItemPurchasedEvent event = new UserCatalogItemPurchasedEvent(
                    user, item, new HashSet<>(), totalCredits, totalPoints, new ArrayList<>(requestedBadges));
            this.fireCatalogPurchaseEvent(event);
            ResolvedCatalogCharges charges = this.resolveCatalogCharges(user, item, free, event);

            List<UserBadge> badges = new ArrayList<>();
            for (String code : event.badges) {
                if (user.getInventory().getBadgesComponent().hasBadge(code)) continue;
                UserBadge badge = new UserBadge(0, code, 0, user);
                badge.insert(connection);
                badges.add(badge);
            }

            Map<Integer, EffectsComponent.UserEffect> effects = new HashMap<>();
            // The offer says how long its effect lasts (catalog_items.extradata, 0 for
            // permanent); the legacy path at purchaseEffect already honours it.
            int effectDuration = item.getEffectDuration();
            for (Map.Entry<Integer, Integer> requested : requestedEffects.entrySet()) {
                for (int count = 0; count < requested.getValue(); count++) {
                    EffectsComponent.UserEffect effect = EffectsComponent.persistEffect(
                            connection, user.getUserInfo().getId(), requested.getKey(), effectDuration);
                    effects.put(requested.getKey(), effect);
                }
            }

            if (!free) this.writePurchaseLog(connection, event, charges, amount);
            EntitlementPurchase result = new EntitlementPurchase(
                    event, badges, effects, charges.credits(), charges.points(), charges.pointsType());
            return new CatalogPurchaseTransaction.PreparedPurchase<>(
                    result, charges.credits(), charges.points(), charges.pointsType());
        });

        this.publishCommittedCharges(user, purchase.credits(), purchase.points(), purchase.pointsType());
        Map<AddUserItemComposer.AddUserItemCategory, List<Integer>> unseenItems = new HashMap<>();
        if (!purchase.badges().isEmpty()) {
            unseenItems.put(AddUserItemComposer.AddUserItemCategory.BADGE, new ArrayList<>());
        }
        for (UserBadge badge : purchase.badges()) {
            user.getInventory().getBadgesComponent().addBadge(badge);
            user.getClient().sendResponse(new AddUserBadgeComposer(badge));
            unseenItems.get(AddUserItemComposer.AddUserItemCategory.BADGE).add(badge.getId());
            Map<String, String> keys = new HashMap<>();
            keys.put("display", "BUBBLE");
            keys.put("image", "${image.library.url}album1584/" + badge.getCode() + ".gif");
            keys.put("message", Emulator.getTexts().getValue("commands.generic.cmd_badge.received"));
            user.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys));
        }
        for (EffectsComponent.UserEffect effect : purchase.effects().values()) {
            user.getInventory().getEffectsComponent().publishEffect(effect);
        }
        if (!isPetOrBotItem(purchase.event().catalogItem)) {
            user.getUserStats().addPurchase(purchase.event().catalogItem);
        }
        user.getClient().sendResponse(new AddUserItemComposer(unseenItems));
        user.getClient().sendResponse(new PurchaseOKComposer(purchase.event().catalogItem));
        ChatStyleRepository.grantForPurchase(user, purchase.event().catalogItem);
        user.getClient().sendResponse(new InventoryRefreshComposer());
    }

    private void purchaseFurnitureAtomically(
            CatalogItem item,
            User user,
            int amount,
            String extraData,
            boolean free,
            CatalogLimitedConfiguration limitedConfiguration,
            int limitedStack,
            int limitedNumber,
            int totalCredits,
            int totalPoints)
            throws SQLException {
        int userId = user.getUserInfo().getId();
        AtomicFurniturePurchase purchase;
        try {
            String operationId = EconomyOperationId.create("catalog:" + userId + ":" + item.getId());
            purchase = CatalogPurchaseTransaction.execute(user, operationId, connection -> {
                Set<UserItem> createdItems = new HashSet<>();
                Map<InteractionGuildFurni, Guild> guildFurniture = new HashMap<>();
                boolean includesMusicDisc = false;
                for (int index = 0; index < amount; index++) {
                    for (Item baseItem : item.getBaseItems()) {
                        String itemExtraData = this.prepareFurnitureExtraData(user, baseItem, extraData);
                        for (int count = 0; count < item.getItemAmount(baseItem.getId()); count++) {
                            if (baseItem.getInteractionType().getType() == InteractionGuildFurni.class
                                    || baseItem.getInteractionType().getType() == InteractionGuildGate.class) {
                                int guildId;
                                try {
                                    guildId = Integer.parseInt(extraData);
                                } catch (NumberFormatException exception) {
                                    throw new SQLException("Invalid guild furniture id", exception);
                                }
                                Guild guild = Emulator.getGameEnvironment()
                                        .getGuildManager()
                                        .getGuild(guildId);
                                if (guild == null
                                        || Emulator.getGameEnvironment()
                                                        .getGuildManager()
                                                        .getGuildMember(guild, user)
                                                == null) {
                                    throw new SQLException("User cannot purchase furniture for guild " + guildId);
                                }
                                if (GuildForumPurchasePolicy.isForumTerminal(baseItem.getName())
                                        && !GuildForumPurchasePolicy.mayBuy(
                                                guild.hasForum(),
                                                guild.getOwnerId(),
                                                user.getUserInfo().getId(),
                                                user.getUserInfo().getRank().getLevel())) {
                                    throw new SQLException(
                                            "User cannot purchase a forum terminal for guild " + guildId);
                                }
                                InteractionGuildFurni created = (InteractionGuildFurni) Emulator.getGameEnvironment()
                                        .getItemManager()
                                        .createItem(connection, userId, baseItem, limitedStack, limitedNumber, "");
                                if (created == null) throw new SQLException("Unable to create guild furniture");
                                Emulator.getGameEnvironment()
                                        .getGuildManager()
                                        .persistGuild(connection, created.getId(), guildId);
                                created.setGuildId(guildId);
                                guildFurniture.put(created, guild);
                                createdItems.add(created);
                            } else if (baseItem.getInteractionType().getType() == InteractionMusicDisc.class) {
                                SoundTrack track = Emulator.getGameEnvironment()
                                        .getItemManager()
                                        .getSoundTrack(item.getExtradata());
                                if (track == null) throw new SQLException("Unknown catalog music track");
                                itemExtraData = this.createMusicDiscExtraData(user, track);
                                UserItem created = Emulator.getGameEnvironment()
                                        .getItemManager()
                                        .createItem(
                                                connection,
                                                userId,
                                                baseItem,
                                                limitedStack,
                                                limitedNumber,
                                                itemExtraData);
                                if (created == null) throw new SQLException("Unable to create music disc");
                                createdItems.add(created);
                                includesMusicDisc = true;
                            } else if (InteractionTeleport.class.isAssignableFrom(
                                    baseItem.getInteractionType().getType())) {
                                UserItem first = Emulator.getGameEnvironment()
                                        .getItemManager()
                                        .createItem(
                                                connection,
                                                userId,
                                                baseItem,
                                                limitedStack,
                                                limitedNumber,
                                                itemExtraData);
                                UserItem second = Emulator.getGameEnvironment()
                                        .getItemManager()
                                        .createItem(
                                                connection,
                                                userId,
                                                baseItem,
                                                limitedStack,
                                                limitedNumber,
                                                itemExtraData);
                                if (first == null || second == null)
                                    throw new SQLException("Unable to create teleport pair");
                                Emulator.getGameEnvironment()
                                        .getItemManager()
                                        .insertTeleportPair(connection, first.getId(), second.getId());
                                createdItems.add(first);
                                createdItems.add(second);
                            } else {
                                UserItem created = Emulator.getGameEnvironment()
                                        .getItemManager()
                                        .createItem(
                                                connection,
                                                userId,
                                                baseItem,
                                                limitedStack,
                                                limitedNumber,
                                                itemExtraData);
                                if (created == null) throw new SQLException("Unable to create catalog furniture");
                                if (baseItem.getInteractionType().getType() == InteractionHopper.class) {
                                    Emulator.getGameEnvironment()
                                            .getItemManager()
                                            .insertHopper(connection, created);
                                }
                                createdItems.add(created);
                            }
                        }
                    }
                }

                Set<Integer> createdIds =
                        createdItems.stream().map(UserItem::getId).collect(Collectors.toSet());
                UserCatalogItemPurchasedEvent purchasedEvent = new UserCatalogItemPurchasedEvent(
                        user, item, createdItems, totalCredits, totalPoints, new ArrayList<>());
                this.fireCatalogPurchaseEvent(purchasedEvent);
                Set<Integer> eventIds =
                        purchasedEvent.itemsList.stream().map(UserItem::getId).collect(Collectors.toSet());
                if (!createdIds.equals(eventIds)) {
                    throw new SQLException("Catalog plugin changed the atomic furniture set");
                }

                ResolvedCatalogCharges charges = this.resolveCatalogCharges(user, item, free, purchasedEvent);
                if (limitedConfiguration != null) {
                    UserItem limitedItem = createdItems.stream()
                            .findFirst()
                            .orElseThrow(() -> new SQLException("Limited purchase created no furniture"));
                    limitedConfiguration.limitedSold(connection, item.getId(), user, limitedItem);
                }
                if (!free) {
                    this.writePurchaseLog(connection, purchasedEvent, charges, amount);
                }
                AtomicFurniturePurchase result = new AtomicFurniturePurchase(
                        purchasedEvent,
                        guildFurniture,
                        includesMusicDisc,
                        charges.credits(),
                        charges.points(),
                        charges.pointsType());
                return new CatalogPurchaseTransaction.PreparedPurchase<>(
                        result, charges.credits(), charges.points(), charges.pointsType());
            });
        } catch (SQLException | RuntimeException exception) {
            if (limitedConfiguration != null && limitedNumber > 0) limitedConfiguration.restoreNumber(limitedNumber);
            throw exception;
        }

        // Committed: the number is sold in the database. A failure while handing the purchase over (the buyer gone
        // offline, say) used to reach the catch above and put the number back in the pool, where the next buyer
        // drew it and failed on the sold row.
        this.publishAtomicFurniturePurchase(user, purchase);
        if (limitedConfiguration != null) {
            user.getUserStats().addLtdLog(item.getId(), Emulator.getIntUnixTimestamp());
        }
    }

    /**
     * Builds the extra data a trophy or a badge display carries: the owner, the date and the filtered
     * text, in the layout the client reads. Engraving a mystery trophy in a room goes through here too,
     * so a trophy bought in the catalog and one engraved in a room are written the same way.
     */
    public String prepareFurnitureExtraData(User user, Item baseItem, String extraData) {
        if (baseItem.getInteractionType().getType() != InteractionTrophy.class
                && baseItem.getInteractionType().getType() != InteractionBadgeDisplay.class) return extraData;

        String value = extraData;
        if (baseItem.getInteractionType().getType() == InteractionBadgeDisplay.class
                && !user.getInventory().getBadgesComponent().hasBadge(value)) {
            ScripterManager.scripterDetected(
                    user.getClient(),
                    Emulator.getTexts()
                            .getValue("scripter.warning.catalog.badge_display")
                            .replace("%username%", user.getUserInfo().getUsername())
                            .replace("%badge%", value));
            value = "UMAD";
        }
        int maximum = Emulator.getConfig().getInt("hotel.trophies.length.max", 300);
        if (value.length() > maximum) value = value.substring(0, maximum);
        return user.getUserInfo().getUsername() + (char) 9
                + Calendar.getInstance().get(Calendar.DAY_OF_MONTH) + "-"
                + (Calendar.getInstance().get(Calendar.MONTH) + 1) + "-"
                + Calendar.getInstance().get(Calendar.YEAR) + (char) 9
                + Emulator.getGameEnvironment().getWordFilter().filter(value.replace(((char) 9) + "", ""), user);
    }

    private String createMusicDiscExtraData(User user, SoundTrack track) {
        Calendar calendar = Calendar.getInstance();
        return user.getUserInfo().getUsername() + "\n"
                + calendar.get(Calendar.DAY_OF_MONTH) + "\n"
                + (calendar.get(Calendar.MONTH) + 1) + "\n"
                + calendar.get(Calendar.YEAR) + "\n"
                + track.getLength() + "\n" + track.getName() + "\n" + track.getId();
    }

    private void fireCatalogPurchaseEvent(UserCatalogItemPurchasedEvent event) {
        Emulator.getPluginManager().fireEvent(event);
    }

    private ResolvedCatalogCharges resolveCatalogCharges(
            User user, CatalogItem item, boolean free, UserCatalogItemPurchasedEvent purchasedEvent) {
        int credits = 0;
        int points = 0;
        int pointsType = item.getPointsType();
        if (!free && !user.hasPermission(Permission.ACC_INFINITE_CREDITS) && purchasedEvent.totalCredits > 0) {
            UserCreditsEvent event = new UserCreditsEvent(user, -purchasedEvent.totalCredits);
            if (!Emulator.getPluginManager().fireEvent(event).isCancelled()) credits = Math.max(0, -event.credits);
        }
        if (!free && !user.hasPermission(Permission.ACC_INFINITE_POINTS) && purchasedEvent.totalPoints > 0) {
            UserPointsEvent event = new UserPointsEvent(user, -purchasedEvent.totalPoints, pointsType);
            if (!Emulator.getPluginManager().fireEvent(event).isCancelled()) {
                points = Math.max(0, -event.points);
                pointsType = event.type;
            }
        }
        return new ResolvedCatalogCharges(credits, points, pointsType);
    }

    private void writePurchaseLog(
            Connection connection, UserCatalogItemPurchasedEvent event, ResolvedCatalogCharges charges, int amount)
            throws SQLException {
        Set<String> itemIds = event.itemsList.stream()
                .map(item -> Integer.toString(item.getId()))
                .collect(Collectors.toSet());
        CatalogPurchaseLogEntry entry = new CatalogPurchaseLogEntry(
                Emulator.getIntUnixTimestamp(),
                event.user.getUserInfo().getId(),
                event.catalogItem.getId(),
                String.join(";", itemIds),
                event.catalogItem.getName(),
                charges.credits(),
                charges.points(),
                charges.pointsType(),
                amount);
        try (PreparedStatement statement = connection.prepareStatement(entry.getQuery())) {
            entry.log(statement);
            statement.executeBatch();
        }
    }

    /** Reward-track "buy from catalogue": the purchase delivered at least one floor or wall furni. */
    private static void progressFurniturePurchase(User user, CatalogItem item) {
        if (item != null
                && item.getBaseItems().stream()
                        .anyMatch(base ->
                                base.getType() == FurnitureType.FLOOR || base.getType() == FurnitureType.WALL)) {
            polaris.hotel.quests.QuestProgressEvents.progress(
                    user, polaris.hotel.quests.QuestGoalType.BUY_CATALOG_FURNI, 1);
        }
    }

    private void publishAtomicFurniturePurchase(User user, AtomicFurniturePurchase purchase) {
        this.publishCommittedCharges(user, purchase.credits(), purchase.points(), purchase.pointsType());

        Set<UserItem> items = purchase.event().itemsList;
        user.getInventory().getItemsComponent().addItems(items);
        Map<AddUserItemComposer.AddUserItemCategory, List<Integer>> unseenItems = new HashMap<>();
        unseenItems.put(
                AddUserItemComposer.AddUserItemCategory.OWNED_FURNI,
                items.stream().map(UserItem::getId).collect(Collectors.toList()));
        Emulator.getPluginManager()
                .fireEvent(new UserCatalogFurnitureBoughtEvent(user, purchase.event().catalogItem, items));
        for (Map.Entry<InteractionGuildFurni, Guild> guildEntry :
                purchase.guildFurniture().entrySet()) {
            if (guildEntry.getKey().getBaseItem().getName().equals("guild_forum")) {
                guildEntry.getValue().setForum(true);
                guildEntry.getValue().needsUpdate = true;
                Emulator.getThreading().run(guildEntry.getValue());
            }
        }
        if (purchase.includesMusicDisc()) {
            AchievementManager.progressAchievement(
                    user, Emulator.getGameEnvironment().getAchievementManager().getAchievement("MusicCollector"));
        }
        if (!isPetOrBotItem(purchase.event().catalogItem)) {
            user.getUserStats().addPurchase(purchase.event().catalogItem);
        }
        user.getClient().sendResponse(new AddUserItemComposer(unseenItems));
        user.getClient().sendResponse(new PurchaseOKComposer(purchase.event().catalogItem));
        progressFurniturePurchase(user, purchase.event().catalogItem);
        ChatStyleRepository.grantForPurchase(user, purchase.event().catalogItem);
        user.getClient().sendResponse(new InventoryRefreshComposer());
    }

    private void publishCommittedCharges(User user, int credits, int points, int pointsType) {
        if (credits > 0) {
            user.getClient().sendResponse(new UserCreditsComposer(user));
        }
        if (points > 0) {
            user.getClient()
                    .sendResponse(new UserPointsComposer(
                            user.getUserInfo().getCurrencyAmount(pointsType), -points, pointsType));
        }
    }

    private record ResolvedCatalogCharges(int credits, int points, int pointsType) {}

    private record AtomicFurniturePurchase(
            UserCatalogItemPurchasedEvent event,
            Map<InteractionGuildFurni, Guild> guildFurniture,
            boolean includesMusicDisc,
            int credits,
            int points,
            int pointsType) {}

    private record EntitlementPurchase(
            UserCatalogItemPurchasedEvent event,
            List<UserBadge> badges,
            Map<Integer, EffectsComponent.UserEffect> effects,
            int credits,
            int points,
            int pointsType) {}

    private record SpecialCompanionPurchase(
            UserCatalogItemPurchasedEvent event,
            List<Bot> bots,
            List<Pet> pets,
            int credits,
            int points,
            int pointsType) {}

    public List<ClubOffer> getClubOffers() {
        return this.getClubOffers(ClubOffer.WINDOW_CLUB);
    }

    public Int2ObjectMap<CatalogPage> getCatalogPagesMap(CatalogPageType pageType) {
        return (pageType == CatalogPageType.BUILDER) ? this.buildersClubCatalogPages : this.catalogPages;
    }

    public List<ClubOffer> getClubOffers(int windowId) {
        List<ClubOffer> offers = new ArrayList<>();

        for (Map.Entry<Integer, ClubOffer> entry : this.clubOffers.entrySet()) {
            if (!entry.getValue().isDeal() && entry.getValue().belongsToWindow(windowId)) {
                offers.add(entry.getValue());
            }
        }

        offers.sort(Comparator.comparingInt(ClubOffer::getId));
        return offers;
    }

    public TargetOffer getTargetOffer(int offerId) {
        synchronized (this.targetOffers) {
            return this.targetOffers.get(offerId);
        }
    }

    void replaceFeaturedPages(Int2ObjectMap<CatalogFeaturedPage> loadedFeaturedPages) {
        synchronized (this.catalogFeaturedPages) {
            this.catalogFeaturedPages.clear();
            this.catalogFeaturedPages.putAll(loadedFeaturedPages);
        }
    }

    static <K, V> void replaceContents(Map<K, V> target, Map<K, V> loaded) {
        synchronized (target) {
            target.clear();
            target.putAll(loaded);
        }
    }

    private int calculateDiscountedPrice(int originalPrice, int amount, CatalogItem item) {
        if (!CatalogItem.haveOffer(item)) return CatalogPurchaseMath.checkedPrice(originalPrice, amount);

        int basicDiscount = amount / DiscountComposer.DISCOUNT_BATCH_SIZE;

        int bonusDiscount = 0;
        if (basicDiscount >= DiscountComposer.MINIMUM_DISCOUNTS_FOR_BONUS) {
            if (amount % DiscountComposer.DISCOUNT_BATCH_SIZE == DiscountComposer.DISCOUNT_BATCH_SIZE - 1) {
                bonusDiscount = 1;
            }

            bonusDiscount += basicDiscount - DiscountComposer.MINIMUM_DISCOUNTS_FOR_BONUS;
        }

        int additionalDiscounts = 0;
        for (int threshold : DiscountComposer.ADDITIONAL_DISCOUNT_THRESHOLDS) {
            if (amount >= threshold) additionalDiscounts++;
        }

        int totalDiscountedItems =
                (basicDiscount * DiscountComposer.DISCOUNT_AMOUNT_PER_BATCH) + bonusDiscount + additionalDiscounts;

        int payableItems = Math.max(0, amount - totalDiscountedItems);
        return CatalogPurchaseMath.checkedPrice(originalPrice, payableItems);
    }
}
