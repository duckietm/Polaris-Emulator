package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.catalog.layouts.*;
import com.eu.habbo.habbohotel.economy.EconomyOperationId;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.SoundTrack;
import com.eu.habbo.habbohotel.items.interactions.*;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.inventory.EffectsComponent;
import com.eu.habbo.messages.outgoing.catalog.*;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.inventory.AddBotComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.AddPetComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.modtool.ModToolIssueHandledComposer;
import com.eu.habbo.messages.outgoing.users.AddUserBadgeComposer;
import com.eu.habbo.messages.outgoing.users.UserCreditsComposer;
import com.eu.habbo.messages.outgoing.users.UserPointsComposer;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadCatalogManagerEvent;
import com.eu.habbo.plugin.events.users.UserCreditsEvent;
import com.eu.habbo.plugin.events.users.UserPointsEvent;
import com.eu.habbo.plugin.events.users.catalog.UserCatalogFurnitureBoughtEvent;
import com.eu.habbo.plugin.events.users.catalog.UserCatalogItemPurchasedEvent;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class CatalogManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogManager.class);

    public static final Map<String, Class<? extends CatalogPage>> pageDefinitions = new HashMap<String, Class<? extends CatalogPage>>(CatalogPageLayouts.values().length) {
        {
            for (CatalogPageLayouts layout : CatalogPageLayouts.values()) {
                switch (layout) {
                    case frontpage:
                        this.put(layout.name().toLowerCase(), FrontpageLayout.class);
                        break;
                    case badge_display:
                        this.put(layout.name().toLowerCase(), BadgeDisplayLayout.class);
                        break;
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
                        if (Emulator.getConfig().getBoolean("hotel.ecotron.enabled"))
                            this.put(layout.name().toLowerCase(), RecyclerLayout.class);
                        break;
                    case recycler_info:
                        if (Emulator.getConfig().getBoolean("hotel.ecotron.enabled"))
                            this.put(layout.name().toLowerCase(), RecyclerInfoLayout.class);
                    case recycler_prizes:
                        if (Emulator.getConfig().getBoolean("hotel.ecotron.enabled"))
                            this.put(layout.name().toLowerCase(), RecyclerPrizesLayout.class);
                        break;
                    case marketplace:
                        if (Emulator.getConfig().getBoolean("hotel.marketplace.enabled"))
                            this.put(layout.name().toLowerCase(), MarketplaceLayout.class);
                        break;
                    case marketplace_own_items:
                        if (Emulator.getConfig().getBoolean("hotel.marketplace.enabled"))
                            this.put(layout.name().toLowerCase(), MarketplaceOwnItems.class);
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
                        this.put(layout.name().toLowerCase(), CustomPrefixLayout.class);
                        break;
                    case default_3x3:
                    default:
                        this.put("default_3x3", Default_3x3Layout.class);
                        break;
                }
            }
        }
    };
    public static int catalogItemAmount;
    public static int PURCHASE_COOLDOWN = 1;
    public static boolean SORT_USING_ORDERNUM = false;
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
    public final Item ecotronItem;
    public final Map<Integer, CatalogLimitedConfiguration> limitedNumbers;
    private final List<Voucher> vouchers;
    public final Int2ObjectMap<int[]> furnitureValues;
    private volatile byte[] rareValuesPayloadCache;

    public CatalogManager() {
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

        this.initialize();

        this.ecotronItem = Emulator.getGameEnvironment().getItemManager().getItem("ecotron_box");

        LOGGER.info("Catalog Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
    }


    public synchronized void initialize() {
        Emulator.getPluginManager().fireEvent(new EmulatorLoadCatalogManagerEvent());

        this.loadLimitedNumbers();
        this.loadCatalogPages();
        this.loadBuildersClubCatalogPages();
        this.loadCatalogFeaturedPages();
        this.loadCatalogItems();
        this.loadBuildersClubCatalogItems();
        this.loadClubOffers();
        this.loadTargetOffers();
        this.loadVouchers();
        this.loadClothing();
        this.loadRecycler();
        this.loadGiftWrappers();
        this.loadFurnitureValues();
    }

    private synchronized void loadFurnitureValues() {
        this.furnitureValues.clear();
        final int diamondType = Emulator.getConfig().getInt("seasonal.currency.diamond", 5);

        for (CatalogPage page : this.catalogPages.values()) {
            for (CatalogItem catalogItem : page.getCatalogItems().values()) {
                if (catalogItem.getAmount() != 1)
                    continue;

                int credits = catalogItem.getCredits();
                int points = catalogItem.getPoints();
                int pointsType = catalogItem.getPointsType();

                if (points <= 0 || pointsType != diamondType)
                    continue;

                Set<Item> baseItems = catalogItem.getBaseItems();

                if (baseItems.size() != 1)
                    continue;

                for (Item item : baseItems) {
                    FurnitureType type = item.getType();

                    if (type != FurnitureType.FLOOR && type != FurnitureType.WALL)
                        continue;

                    int spriteId = item.getSpriteId();

                    if (spriteId > 0 && !this.furnitureValues.containsKey(spriteId)) {
                        this.furnitureValues.put(spriteId, new int[]{credits, points, pointsType});
                    }
                }
            }
        }

        this.rebuildRareValuesPayloadCache();

        LOGGER.info("Furniture Values -> Loaded! ({} entries)", this.furnitureValues.size());
    }

    private void rebuildRareValuesPayloadCache() {
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(this.furnitureValues.size() * 16 + 8);
             java.io.DataOutputStream out = new java.io.DataOutputStream(baos)) {
            out.writeInt(this.furnitureValues.size());
            for (Int2ObjectMap.Entry<int[]> entry : this.furnitureValues.int2ObjectEntrySet()) {
                int[] value = entry.getValue();
                out.writeInt(entry.getIntKey()); // spriteId
                out.writeInt(value[0]);        // credits
                out.writeInt(value[1]);        // points
                out.writeInt(value[2]);        // pointsType
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

    private synchronized void loadLimitedNumbers() {
        this.limitedNumbers.clear();

        Map<Integer, LinkedList<Integer>> limiteds = new HashMap<>();
        Int2IntMap totals = new Int2IntOpenHashMap();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM catalog_items_limited")) {
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
        }

        for (Map.Entry<Integer, LinkedList<Integer>> set : limiteds.entrySet()) {
            this.limitedNumbers.put(set.getKey(), new CatalogLimitedConfiguration(set.getKey(), set.getValue(), totals.get(set.getKey())));
        }
    }


    private synchronized void loadCatalogPages() {
        this.catalogPages.clear();

        final Map<Integer, CatalogPage> pages = new HashMap<>();
        pages.put(-1, new CatalogRootLayout());
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM catalog_pages ORDER BY parent_id, id")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Class<? extends CatalogPage> pageClazz = pageDefinitions.get(set.getString("page_layout"));

                    if (pageClazz == null) {
                        LOGGER.info("Unknown Page Layout: {}", set.getString("page_layout"));
                        continue;
                    }

                    try {
                        CatalogPage page = pageClazz.getConstructor(ResultSet.class).newInstance(set);
                        pages.put(page.getId(), page);
                    } catch (Exception e) {
                        LOGGER.error("Failed to load layout: {}", set.getString("page_layout"));
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
                    LOGGER.info("Parent Page not found for {} (ID: {}, parent_id: {})", object.getPageName(), object.id, object.parentId);
                }
            }
        }

        this.catalogPages.putAll(pages);

        LOGGER.info("Loaded {} Catalog Pages!", this.catalogPages.size());
    }

    private synchronized void loadBuildersClubCatalogPages() {
        this.buildersClubCatalogPages.clear();

        final Map<Integer, CatalogPage> pages = new HashMap<>();
        pages.put(-1, new CatalogRootLayout());

        String query = "SELECT id, parent_id, caption, caption AS caption_save, page_layout, icon_color, icon_image, 1 AS min_rank, order_num, visible, enabled, '0' AS club_only, 'BUILDERS_CLUB' AS catalog_mode, page_headline, page_teaser, page_special, page_text1, page_text2, page_text_details, page_text_teaser, '' AS includes FROM catalog_pages_bc ORDER BY parent_id, id";

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
                        CatalogPage page = pageClazz.getConstructor(ResultSet.class).newInstance(set);
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
                    LOGGER.info("Builders Club parent page not found for {} (ID: {}, parent_id: {})", object.getPageName(), object.id, object.parentId);
                }
            }
        }

        this.buildersClubCatalogPages.putAll(pages);

        LOGGER.info("Loaded {} Builders Club Catalog Pages!", this.buildersClubCatalogPages.size());
    }


    private synchronized void loadCatalogFeaturedPages() {
        this.catalogFeaturedPages.clear();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); Statement statement = connection.createStatement(); ResultSet set = statement.executeQuery("SELECT * FROM catalog_featured_pages ORDER BY slot_id ASC")) {
            while (set.next()) {
                this.catalogFeaturedPages.put(set.getInt("slot_id"), new CatalogFeaturedPage(
                        set.getInt("slot_id"),
                        set.getString("caption"),
                        set.getString("image"),
                        CatalogFeaturedPage.Type.valueOf(set.getString("type").toUpperCase()),
                        set.getInt("expire_timestamp"),
                        set.getString("page_name"),
                        set.getInt("page_id"),
                        set.getString("product_name")
                ));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    private synchronized void loadCatalogItems() {
        this.offerDefs.clear();
        this.clubItems.clear();
        catalogItemAmount = 0;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); Statement statement = connection.createStatement(); ResultSet set = statement.executeQuery("SELECT * FROM catalog_items")) {
            CatalogItem item;
            while (set.next()) {
                List<String> valueErrors = CatalogValueValidator.validate(CatalogValueValidator.Values.from(set));
                if (!valueErrors.isEmpty()) {
                    valueErrors.forEach(error -> LOGGER.error("Catalog value validation: {}", error));
                    continue;
                }
                if (set.getString("item_ids").equals("0"))
                    continue;

                if (set.getString("catalog_name").contains("HABBO_CLUB_")) {
                    this.clubItems.add(new CatalogItem(set));
                    continue;
                }

                CatalogPage page = this.catalogPages.get(set.getInt("page_id"));

                if (page == null)
                    continue;

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
                } else
                    item.update(set);

                if (item.isLimited()) {
                    this.createOrUpdateLimitedConfig(item);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        for (CatalogPage page : this.catalogPages.values()) {
            for (Integer id : page.getIncluded()) {
                CatalogPage p = this.catalogPages.get(id);

                if (p != null) {
                    page.getCatalogItems().putAll(p.getCatalogItems());
                }
            }
        }
    }

    private synchronized void loadBuildersClubCatalogItems() {
        this.buildersClubOfferDefs.clear();

        String query = "SELECT id, item_ids, page_id, catalog_name, 0 AS cost_credits, 0 AS cost_points, 0 AS points_type, 1 AS amount, 0 AS limited_stack, 0 AS limited_sells, extradata, '0' AS club_only, '1' AS have_offer, id AS offer_id, order_number FROM catalog_items_bc";

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
                CatalogPage includedPage = this.buildersClubCatalogPages.get(id);

                if (includedPage != null) {
                    page.getCatalogItems().putAll(includedPage.getCatalogItems());
                }
            }
        }
    }

    private void loadClubOffers() {
        this.clubOffers.clear();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM catalog_club_offers WHERE enabled = ?")) {
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
        synchronized (this.targetOffers) {
            this.targetOffers.clear();

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM catalog_target_offers WHERE end_timestamp > ?")) {
                statement.setInt(1, Emulator.getIntUnixTimestamp());
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        this.targetOffers.put(set.getInt("id"), new TargetOffer(set));
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }


    private void loadVouchers() {
        synchronized (this.vouchers) {
            this.vouchers.clear();

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); Statement statement = connection.createStatement(); ResultSet set = statement.executeQuery("SELECT * FROM vouchers")) {
                while (set.next()) {
                    this.vouchers.add(new Voucher(set));
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }


    public void loadRecycler() {
        synchronized (this.prizes) {
            this.prizes.clear();
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); Statement statement = connection.createStatement(); ResultSet set = statement.executeQuery("SELECT * FROM recycler_prizes")) {
                while (set.next()) {
                    Item item = Emulator.getGameEnvironment().getItemManager().getItem(set.getInt("item_id"));

                    if (item != null) {
                        if (this.prizes.get(set.getInt("rarity")) == null) {
                            this.prizes.put(set.getInt("rarity"), new HashSet<>());
                        }

                        this.prizes.get(set.getInt("rarity")).add(item);
                    } else {
                        LOGGER.error("Cannot load item with ID: {} as recycler reward!", set.getInt("item_id"));
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }


    public void loadGiftWrappers() {
        synchronized (this.giftWrappers) {
            synchronized (this.giftFurnis) {
                this.giftWrappers.clear();
                this.giftFurnis.clear();

                try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); Statement statement = connection.createStatement(); ResultSet set = statement.executeQuery("SELECT * FROM gift_wrappers ORDER BY sprite_id DESC")) {
                    while (set.next()) {
                        switch (set.getString("type")) {
                            case "wrapper":
                                this.giftWrappers.put(set.getInt("sprite_id"), set.getInt("item_id"));
                                break;

                            case "gift":
                                this.giftFurnis.put(set.getInt("sprite_id"), set.getInt("item_id"));
                                break;
                        }
                    }
                } catch (SQLException e) {
                    LOGGER.error("Caught SQL exception", e);
                }
            }
        }
    }

    private void loadClothing() {
        synchronized (this.clothing) {
            this.clothing.clear();

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); Statement statement = connection.createStatement(); ResultSet set = statement.executeQuery("SELECT * FROM catalog_clothing")) {
                while (set.next()) {
                    this.clothing.put(set.getInt("id"), new ClothItem(set));
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    public ClothItem getClothing(String name) {
        for (ClothItem item : this.clothing.values()) {
            if (item.name.equalsIgnoreCase(name)) {
                return item;
            }
        }

        return null;
    }

    public Voucher getVoucher(String code) {
        synchronized (this.vouchers) {
            for (Voucher voucher : this.vouchers) {
                if (voucher.code.equals(code)) {
                    return voucher;
                }
            }
        }
        return null;
    }

    public void redeemVoucher(GameClient client, String voucherCode) {
        Habbo habbo = client.getHabbo();
        if (habbo == null)
            return;

        Voucher voucher = Emulator.getGameEnvironment().getCatalogManager().getVoucher(voucherCode);
        if (voucher == null) {
            client.sendResponse(new RedeemVoucherErrorComposer(RedeemVoucherErrorComposer.INVALID_CODE));
            return;
        }

        Voucher.ClaimResult claimResult = voucher.claimForUser(habbo.getHabboInfo().getId());
        switch (claimResult) {
            case CLAIMED:
                break;
            case EXHAUSTED:
                client.sendResponse(new RedeemVoucherErrorComposer(Emulator.getGameEnvironment().getCatalogManager().deleteVoucher(voucher) ? RedeemVoucherErrorComposer.INVALID_CODE : RedeemVoucherErrorComposer.TECHNICAL_ERROR));
                return;
            case USER_LIMIT:
                client.sendResponse(new ModToolIssueHandledComposer("You have exceeded the limit for redeeming this voucher."));
                return;
            case FAILED:
            default:
                client.sendResponse(new RedeemVoucherErrorComposer(RedeemVoucherErrorComposer.TECHNICAL_ERROR));
                return;
        }

        if (voucher.points > 0) {
            client.getHabbo().givePoints(voucher.pointsType, voucher.points);
        }

        if (voucher.credits > 0) {
            client.getHabbo().giveCredits(voucher.credits);
        }

        if (voucher.catalogItemId > 0) {
            CatalogItem item = this.getCatalogItem(voucher.catalogItemId);
            if (item != null) {
                this.purchaseItem(null, item, client.getHabbo(), 1, "", true);
            }
        }

        client.sendResponse(new RedeemVoucherOKComposer());
    }

    public boolean deleteVoucher(Voucher voucher) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM vouchers WHERE code = ?")) {
            statement.setString(1, voucher.code);

            synchronized (this.vouchers) {
                this.vouchers.remove(voucher);
            }

            return statement.executeUpdate() >= 1;
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return false;
    }


    public CatalogPage getCatalogPage(int pageId) {
        return this.catalogPages.get(pageId);
    }

    public CatalogPage getCatalogPage(int pageId, CatalogPageType pageType) {
        return this.getCatalogPagesMap(pageType).get(pageId);
    }

    public CatalogPage getCatalogPage(String captionSafe) {
        return this.catalogPages.values().stream()
                .filter(p -> p != null && p.getPageName() != null && p.getPageName().equalsIgnoreCase(captionSafe))
                .findAny().orElse(null);
    }

    public CatalogPage getCatalogPageByLayout(String layoutName) {
        return this.catalogPages.values().stream()
                .filter(p -> p != null &&
                        p.isVisible() &&
                        p.isEnabled() &&
                        p.getRank() < 2 &&
                        p.getLayout() != null && p.getLayout().equalsIgnoreCase(layoutName)
                )
                .findAny().orElse(null);
    }

    public CatalogItem getCatalogItem(int id) {
        return this.getCatalogItem(id, CatalogPageType.NORMAL);
    }

    public CatalogItem getCatalogItem(int id, CatalogPageType pageType) {
        final CatalogItem[] item = {null};
        final Int2ObjectMap<CatalogPage> pagesMap = this.getCatalogPagesMap(pageType);

        synchronized (pagesMap) {
            for (CatalogPage object : pagesMap.values()) {
                item[0] = object.getCatalogItem(id);
                if (item[0] != null) {
                    break;
                }
            }
        }

        return item[0];
    }


    public List<CatalogPage> getCatalogPages(int parentId, final Habbo habbo) {
        return this.getCatalogPages(parentId, habbo, CatalogPageType.NORMAL);
    }

    public List<CatalogPage> getCatalogPages(int parentId, final Habbo habbo, final CatalogPageType pageType) {
        final List<CatalogPage> pages = new ArrayList<>();
        final Int2ObjectMap<CatalogPage> pagesMap = this.getCatalogPagesMap(pageType);
        CatalogPage parentPage = pagesMap.get(parentId);

        if (parentPage == null) {
            return pages;
        }

        for (CatalogPage object : parentPage.childPages.values()) {
            boolean isVisiblePage = object.visible;
            boolean hasRightRank = object.getRank() <= habbo.getHabboInfo().getRank().getId();
            boolean clubRightsOkay = !object.isClubOnly() || habbo.getHabboInfo().getHabboStats().hasActiveClub();
            boolean pageTypeMatches = (pageType == CatalogPageType.BUILDER) || object.getCatalogPageType().matches(pageType);

            if (isVisiblePage && hasRightRank && clubRightsOkay && pageTypeMatches) {
                pages.add(object);
            }
        }
        Collections.sort(pages);

        return pages;
    }

    public Int2ObjectMap<CatalogFeaturedPage> getCatalogFeaturedPages() {
        return this.catalogFeaturedPages;
    }


    public CatalogItem getClubItem(int itemId) {
        synchronized (this.clubItems) {
            for (CatalogItem item : this.clubItems) {
                if (item.getId() == itemId)
                    return item;
            }
        }

        return null;
    }


    public boolean moveCatalogItem(CatalogItem item, int pageId) {
        CatalogPage page = this.getCatalogPage(item.getPageId());

        if (page == null)
            return false;

        page.getCatalogItems().remove(item.getId());

        page = this.getCatalogPage(pageId);

        if (page == null)
            return false;

        page.getCatalogItems().put(item.getId(), item);

        item.setPageId(pageId);
        item.setNeedsUpdate(true);

        item.run();
        return true;
    }


    public Item getRandomRecyclerPrize() {
        int level = 1;

        if (Emulator.getRandom().nextInt(Emulator.getConfig().getInt("hotel.ecotron.rarity.chance.5")) + 1 == Emulator.getConfig().getInt("hotel.ecotron.rarity.chance.5")) {
            level = 5;
        } else if (Emulator.getRandom().nextInt(Emulator.getConfig().getInt("hotel.ecotron.rarity.chance.4")) + 1 == Emulator.getConfig().getInt("hotel.ecotron.rarity.chance.4")) {
            level = 4;
        } else if (Emulator.getRandom().nextInt(Emulator.getConfig().getInt("hotel.ecotron.rarity.chance.3")) + 1 == Emulator.getConfig().getInt("hotel.ecotron.rarity.chance.3")) {
            level = 3;
        } else if (Emulator.getRandom().nextInt(Emulator.getConfig().getInt("hotel.ecotron.rarity.chance.2")) + 1 == Emulator.getConfig().getInt("hotel.ecotron.rarity.chance.2")) {
            level = 2;
        }

        if (this.prizes.containsKey(level) && !this.prizes.get(level).isEmpty()) {
            return (Item) this.prizes.get(level).toArray()[Emulator.getRandom().nextInt(this.prizes.get(level).size())];
        } else {
            LOGGER.error("No rewards specified for rarity level {}", level);
        }

        return null;
    }


    public CatalogPage createCatalogPage(String caption, String captionSave, int roomId, int icon, CatalogPageLayouts layout, int minRank, int parentId, CatalogPageType pageType, CatalogPageType catalogMode) {
        return createCatalogPage(caption, captionSave, roomId, icon, layout, minRank, parentId, pageType, catalogMode, true, true, 1);
    }

    public CatalogPage createCatalogPage(String caption, String captionSave, int roomId, int icon, CatalogPageLayouts layout, int minRank, int parentId, CatalogPageType pageType, CatalogPageType catalogMode, boolean visible, boolean enabled, int orderNum) {
        CatalogPage catalogPage = null;
        boolean buildersClubPage = (pageType == CatalogPageType.BUILDER);
        String insertQuery = buildersClubPage
                ? "INSERT INTO catalog_pages_bc (parent_id, caption, page_layout, icon_color, icon_image, order_num, visible, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                : "INSERT INTO catalog_pages (parent_id, caption, caption_save, icon_image, visible, enabled, min_rank, page_layout, room_id, catalog_mode, order_num) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String selectQuery = buildersClubPage
                ? "SELECT id, parent_id, caption, caption AS caption_save, page_layout, icon_color, icon_image, 1 AS min_rank, order_num, visible, enabled, '0' AS club_only, 'BUILDERS_CLUB' AS catalog_mode, page_headline, page_teaser, page_special, page_text1, page_text2, page_text_details, page_text_teaser, '' AS includes FROM catalog_pages_bc WHERE id = ?"
                : "SELECT * FROM catalog_pages WHERE id = ?";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, parentId);
            statement.setString(2, caption);

            if (buildersClubPage) {
                statement.setString(3, layout.name());
                statement.setInt(4, 1);
                statement.setInt(5, icon);
                statement.setInt(6, orderNum < 0 ? 0 : orderNum);
                statement.setString(7, visible ? "1" : "0");
                statement.setString(8, enabled ? "1" : "0");
            } else {
                statement.setString(3, captionSave);
                statement.setInt(4, icon);
                statement.setString(5, visible ? "1" : "0");
                statement.setString(6, enabled ? "1" : "0");
                statement.setInt(7, minRank);
                statement.setString(8, layout.name());
                statement.setInt(9, roomId);
                statement.setString(10, catalogMode.name());
                statement.setInt(11, orderNum < 0 ? 0 : orderNum);
            }
            statement.execute();
            try (ResultSet set = statement.getGeneratedKeys()) {
                if (set.next()) {
                    try (PreparedStatement stmt = connection.prepareStatement(selectQuery)) {
                        stmt.setInt(1, set.getInt(1));
                        try (ResultSet page = stmt.executeQuery()) {
                            if (page.next()) {
                                Class<? extends CatalogPage> pageClazz = pageDefinitions.get(page.getString("page_layout"));

                                if (pageClazz != null) {
                                    try {
                                        catalogPage = pageClazz.getConstructor(ResultSet.class).newInstance(page);
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


    public CatalogLimitedConfiguration createOrUpdateLimitedConfig(CatalogItem item) {
        if (item.isLimited()) {
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
                        limitedConfiguration.generateNumbers(item.limitedStack + 1, item.limitedStack - limitedConfiguration.getTotalSet());
                    } else {
                        item.limitedStack = limitedConfiguration.getTotalSet();
                    }
                }
            }

            return limitedConfiguration;
        }

        return null;
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


    public void purchaseItem(CatalogPage page, CatalogItem item, Habbo habbo, int amount, String extradata, boolean free) {
        if (item == null) {
            habbo.getClient().sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
            return;
        }

        synchronized (habbo.getHabboStats()) {
            if (habbo.getHabboStats().isPurchasingFurniture) {
                habbo.getClient().sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                return;
            }
            habbo.getHabboStats().isPurchasingFurniture = true;
        }

        Set<HabboItem> createdItems = new HashSet<>();
        List<Bot> createdBots = new ArrayList<>();
        List<Pet> createdPets = new ArrayList<>();
        List<Integer> pendingEffects = new ArrayList<>();
        List<Guild> pendingGuildForums = new ArrayList<>();
        boolean paymentTaken = false;
        boolean purchaseDelivered = false;
        int paidCredits = 0;
        int paidPoints = 0;
        int paidPointsType = 0;
        CatalogLimitedConfiguration limitedConfiguration = null;
        int limitedNumber = 0;

        try {
            if (item.isClubOnly() && !habbo.getClient().getHabbo().getHabboStats().hasActiveClub()) {
                habbo.getClient().sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.REQUIRES_CLUB));
                return;
            }

            if (amount <= 0) {
                habbo.getClient().sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
                return;
            }

            try {
                int limitedStack = 0;
                if (item.isLimited()) {
                    amount = 1;
                    if (this.getLimitedConfig(item).available() == 0) {
                        habbo.getClient().sendResponse(new AlertLimitedSoldOutComposer());
                        return;
                    }

                    if (Emulator.getConfig().getBoolean("hotel.catalog.ltd.limit.enabled")) {
                        int ltdLimit = Emulator.getConfig().getInt("hotel.purchase.ltd.limit.daily.total");
                        if (habbo.getHabboStats().totalLtds() >= ltdLimit) {
                            habbo.alert(Emulator.getTexts().getValue("error.catalog.buy.limited.daily.total").replace("%itemname%", item.getBaseItems().iterator().next().getDisplayName()).replace("%limit%", ltdLimit + ""));
                            return;
                        }

                        ltdLimit = Emulator.getConfig().getInt("hotel.purchase.ltd.limit.daily.item");
                        if (habbo.getHabboStats().totalLtds(item.id) >= ltdLimit) {
                            habbo.alert(Emulator.getTexts().getValue("error.catalog.buy.limited.daily.item").replace("%itemname%", item.getBaseItems().iterator().next().getDisplayName()).replace("%limit%", ltdLimit + ""));
                            return;
                        }
                    }
                }

                if (amount > 1) {
                    if (amount == item.getAmount()) {
                        amount = 1;
                    } else {
                        long requestedItems = (long) amount * item.getAmount();
                        if (requestedItems > 100) {
                            habbo.alert("Whoops! You tried to buy this " + requestedItems + " times. This must've been a mistake.");
                            habbo.getClient().sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
                            return;
                        }
                    }
                }

                Set<HabboItem> itemsList = new HashSet<>();


                if (amount > 1 && !CatalogItem.haveOffer(item)) {
                    String message = Emulator.getTexts().getValue("scripter.warning.catalog.amount").replace("%username%", habbo.getHabboInfo().getUsername()).replace("%itemname%", item.getName()).replace("%pagename%", page.getCaption());
                    ScripterManager.scripterDetected(habbo.getClient(), message);
                    LOGGER.info(message);
                    habbo.getClient().sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
                    return;
                }

                if (item.isLimited()) {
                    limitedConfiguration = this.getLimitedConfig(item);

                    if (limitedConfiguration == null) {
                        limitedConfiguration = this.createOrUpdateLimitedConfig(item);
                    }

                    limitedStack = limitedConfiguration.getTotalSet();
                }

                int totalCredits = free ? 0 : this.calculateDiscountedPrice(item.getCredits(), amount, item);
                int totalPoints = free ? 0 : this.calculateDiscountedPrice(item.getPoints(), amount, item);

                if (totalCredits > habbo.getHabboInfo().getCredits()) return;
                if (totalPoints > habbo.getHabboInfo().getCurrencyAmount(item.getPointsType()))
                    return;

                if (limitedConfiguration != null) limitedNumber = limitedConfiguration.getNumber();

                if (this.isAtomicEntitlementPurchase(item)) {
                    this.purchaseEntitlementsAtomically(item, habbo, amount, free, totalCredits, totalPoints);
                    return;
                }

                if (this.isAtomicBotOrPetPurchase(item)) {
                    this.purchaseBotsAndPetsAtomically(item, habbo, amount, extradata, free, totalCredits, totalPoints);
                    return;
                }

                if (this.isAtomicFurniturePurchase(item)) {
                    this.purchaseFurnitureAtomically(item, habbo, amount, extradata, free, limitedConfiguration,
                            limitedStack, limitedNumber, totalCredits, totalPoints);
                    return;
                }

                List<String> badges = new ArrayList<>();
                Map<AddHabboItemComposer.AddHabboItemCategory, List<Integer>> unseenItems = new HashMap<>();
                boolean badgeFound = false;

                for (int i = 0; i < amount; i++) {
                    for (Item baseItem : item.getBaseItems()) {
                        for (int k = 0; k < item.getItemAmount(baseItem.getId()); k++) {
                            if (baseItem.getName().startsWith("rentable_bot_") || baseItem.getName().startsWith("bot_")) {
                                String baseName = baseItem.getName();
                                String type = item.getName().replace("rentable_bot_", "");
                                type = type.replace("bot_", "");
                                type = type.replace("visitor_logger", "visitor_log");

                                if (("bot_" + com.eu.habbo.habbohotel.bots.FrankBot.BOT_TYPE).equals(baseName)
                                        || ("rentable_bot_" + com.eu.habbo.habbohotel.bots.FrankBot.BOT_TYPE).equals(baseName)) {
                                    if (!habbo.getClient().getHabbo().hasPermission(com.eu.habbo.habbohotel.bots.FrankBot.PERMISSION_USE)) {
                                        habbo.getClient().sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                                        return;
                                    }
                                }

                                Map<String, String> data = new HashMap<>();

                                for (String s : item.getExtradata().split(";")) {
                                    if (s.contains(":")) {
                                        data.put(s.split(":")[0], s.split(":")[1]);
                                    }
                                }

                                Bot bot = Emulator.getGameEnvironment().getBotManager().createBot(data, type, habbo.getHabboInfo().getId());

                                if (bot != null) {
                                    createdBots.add(bot);
                                    bot.setOwnerId(habbo.getClient().getHabbo().getHabboInfo().getId());
                                    bot.setOwnerName(habbo.getClient().getHabbo().getHabboInfo().getUsername());
                                    bot.needsUpdate(true);
                                    Emulator.getThreading().run(bot);
                                    habbo.getClient().getHabbo().getInventory().getBotsComponent().addBot(bot);
                                    habbo.getClient().sendResponse(new AddBotComposer(bot));

                                    if (!unseenItems.containsKey(AddHabboItemComposer.AddHabboItemCategory.BOT)) {
                                        unseenItems.put(AddHabboItemComposer.AddHabboItemCategory.BOT, new ArrayList<>());
                                    }

                                    unseenItems.get(AddHabboItemComposer.AddHabboItemCategory.BOT).add(bot.getId());
                                } else {
                                    throw new Exception("Failed to create bot of type: " + type);
                                }
                            } else if (baseItem.getType() == FurnitureType.EFFECT) {
                                int effectId = baseItem.getEffectM();

                                if (habbo.getHabboInfo().getGender().equals(HabboGender.F)) {
                                    effectId = baseItem.getEffectF();
                                }

                                if (effectId > 0) {
                                    pendingEffects.add(effectId);
                                }
                            } else if (Item.isPet(baseItem)) {
                                String[] data = extradata.split("\n");

                                if (data.length < 3) {
                                    habbo.getClient().sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                                    return;
                                }

                                Pet pet = null;
                                try {
                                    pet = Emulator.getGameEnvironment().getPetManager().createPet(baseItem, data[0], data[1], data[2], habbo.getClient());
                                } catch (Exception e) {
                                    LOGGER.error("Caught exception", e);
                                    habbo.getClient().sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                                }

                                if (pet == null) {
                                    habbo.getClient().sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                                    return;
                                }

                                createdPets.add(pet);
                                habbo.getClient().getHabbo().getInventory().getPetsComponent().addPet(pet);
                                habbo.getClient().sendResponse(new AddPetComposer(pet));
                                habbo.getClient().sendResponse(new PetBoughtNotificationComposer(pet, false));

                                AchievementManager.progressAchievement(habbo.getClient().getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("PetLover"));

                                if (!unseenItems.containsKey(AddHabboItemComposer.AddHabboItemCategory.PET)) {
                                    unseenItems.put(AddHabboItemComposer.AddHabboItemCategory.PET, new ArrayList<>());
                                }

                                unseenItems.get(AddHabboItemComposer.AddHabboItemCategory.PET).add(pet.getId());
                            } else if (baseItem.getType() == FurnitureType.BADGE) {
                                if (!habbo.getInventory().getBadgesComponent().hasBadge(baseItem.getName())) {
                                    if (!badges.contains(baseItem.getName())) {
                                        badges.add(baseItem.getName());
                                    }
                                } else {
                                    badgeFound = true;
                                }
                            } else {
                                if (baseItem.getInteractionType().getType() == InteractionTrophy.class || baseItem.getInteractionType().getType() == InteractionBadgeDisplay.class) {
                                    if (baseItem.getInteractionType().getType() == InteractionBadgeDisplay.class && !habbo.getClient().getHabbo().getInventory().getBadgesComponent().hasBadge(extradata)) {
                                        ScripterManager.scripterDetected(habbo.getClient(), Emulator.getTexts().getValue("scripter.warning.catalog.badge_display").replace("%username%", habbo.getClient().getHabbo().getHabboInfo().getUsername()).replace("%badge%", extradata));
                                        extradata = "UMAD";
                                    }

                                    if (extradata.length() > Emulator.getConfig().getInt("hotel.trophies.length.max", 300)) {
                                        extradata = extradata.substring(0, Emulator.getConfig().getInt("hotel.trophies.length.max", 300));
                                    }

                                    extradata = habbo.getClient().getHabbo().getHabboInfo().getUsername() + (char) 9 + Calendar.getInstance().get(Calendar.DAY_OF_MONTH) + "-" + (Calendar.getInstance().get(Calendar.MONTH) + 1) + "-" + Calendar.getInstance().get(Calendar.YEAR) + (char) 9 + Emulator.getGameEnvironment().getWordFilter().filter(extradata.replace(((char) 9) + "", ""), habbo);
                                }

                                if (InteractionTeleport.class.isAssignableFrom(baseItem.getInteractionType().getType())) {
                                    HabboItem teleportOne = Emulator.getGameEnvironment().getItemManager().createItem(habbo.getClient().getHabbo().getHabboInfo().getId(), baseItem, limitedStack, limitedNumber, extradata);
                                    HabboItem teleportTwo = Emulator.getGameEnvironment().getItemManager().createItem(habbo.getClient().getHabbo().getHabboInfo().getId(), baseItem, limitedStack, limitedNumber, extradata);
                                    if (teleportOne == null || teleportTwo == null) {
                                        if (teleportOne != null) createdItems.add(teleportOne);
                                        if (teleportTwo != null) createdItems.add(teleportTwo);
                                        throw new IllegalStateException("failed to create catalog teleport pair");
                                    }
                                    createdItems.add(teleportOne);
                                    createdItems.add(teleportTwo);
                                    Emulator.getGameEnvironment().getItemManager().insertTeleportPair(teleportOne.getId(), teleportTwo.getId());
                                    itemsList.add(teleportOne);
                                    itemsList.add(teleportTwo);
                                } else if (baseItem.getInteractionType().getType() == InteractionHopper.class) {
                                    HabboItem hopper = Emulator.getGameEnvironment().getItemManager().createItem(habbo.getClient().getHabbo().getHabboInfo().getId(), baseItem, limitedStack, limitedNumber, extradata);

                                    if (hopper == null) throw new IllegalStateException("failed to create catalog hopper");
                                    createdItems.add(hopper);

                                    Emulator.getGameEnvironment().getItemManager().insertHopper(hopper);

                                    itemsList.add(hopper);
                                } else if (baseItem.getInteractionType().getType() == InteractionGuildFurni.class || baseItem.getInteractionType().getType() == InteractionGuildGate.class) {
                                    int guildId;
                                    try {
                                        guildId = Integer.parseInt(extradata);
                                    } catch (Exception e) {
                                        LOGGER.error("Caught exception", e);
                                        habbo.getClient().sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                                        return;
                                    }

                                    Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

                                    if (guild != null && Emulator.getGameEnvironment().getGuildManager().getGuildMember(guild, habbo) != null) {
                                        if (baseItem.getName().equals("guild_forum") && guild.getOwnerId() != habbo.getHabboInfo().getId()) {
                                            habbo.getClient().sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                                            return;
                                        }

                                        HabboItem createdItem = Emulator.getGameEnvironment().getItemManager().createItem(habbo.getClient().getHabbo().getHabboInfo().getId(), baseItem, limitedStack, limitedNumber, extradata);
                                        if (!(createdItem instanceof InteractionGuildFurni)) {
                                            if (createdItem != null) createdItems.add(createdItem);
                                            throw new IllegalStateException("failed to create catalog guild item");
                                        }
                                        InteractionGuildFurni habboItem = (InteractionGuildFurni) createdItem;
                                        createdItems.add(habboItem);
                                        habboItem.setExtradata("");
                                        habboItem.needsUpdate(true);

                                        Emulator.getThreading().run(habboItem);
                                        Emulator.getGameEnvironment().getGuildManager().setGuild(habboItem, guildId);
                                        itemsList.add(habboItem);

                                        if (baseItem.getName().equals("guild_forum")) {
                                            pendingGuildForums.add(guild);
                                        }
                                    }
                                } else if (baseItem.getInteractionType().getType() == InteractionMusicDisc.class) {
                                    SoundTrack track = Emulator.getGameEnvironment().getItemManager().getSoundTrack(item.getExtradata());

                                    if (track == null) {
                                        habbo.getClient().sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                                        return;
                                    }

                                    HabboItem createdItem = Emulator.getGameEnvironment().getItemManager().createItem(habbo.getClient().getHabbo().getHabboInfo().getId(), baseItem, limitedStack, limitedNumber, habbo.getClient().getHabbo().getHabboInfo().getUsername() + "\n" + Calendar.getInstance().get(Calendar.DAY_OF_MONTH) + "\n" + (Calendar.getInstance().get(Calendar.MONTH) + 1) + "\n" + Calendar.getInstance().get(Calendar.YEAR) + "\n" + track.getLength() + "\n" + track.getName() + "\n" + track.getId());
                                    if (!(createdItem instanceof InteractionMusicDisc)) {
                                        if (createdItem != null) createdItems.add(createdItem);
                                        throw new IllegalStateException("failed to create catalog music disc");
                                    }
                                    InteractionMusicDisc habboItem = (InteractionMusicDisc) createdItem;
                                    createdItems.add(habboItem);
                                    habboItem.needsUpdate(true);

                                    Emulator.getThreading().run(habboItem);
                                    itemsList.add(habboItem);

                                    AchievementManager.progressAchievement(habbo, Emulator.getGameEnvironment().getAchievementManager().getAchievement("MusicCollector"));
                                } else {
                                    HabboItem habboItem = Emulator.getGameEnvironment().getItemManager().createItem(habbo.getClient().getHabbo().getHabboInfo().getId(), baseItem, limitedStack, limitedNumber, extradata);
                                    if (habboItem == null) throw new IllegalStateException("failed to create catalog item");
                                    createdItems.add(habboItem);
                                    itemsList.add(habboItem);
                                }
                            }
                        }
                    }
                }

                if (badgeFound && item.getBaseItems().size() == 1) {
                    habbo.getClient().sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.ALREADY_HAVE_BADGE));
                    return;
                }

                UserCatalogItemPurchasedEvent purchasedEvent = new UserCatalogItemPurchasedEvent(habbo, item, itemsList, totalCredits, totalPoints, badges);
                Emulator.getPluginManager().fireEvent(purchasedEvent);

                CatalogPurchaseMath.requireNonNegative(purchasedEvent.totalCredits, "plugin-adjusted credit price");
                CatalogPurchaseMath.requireNonNegative(purchasedEvent.totalPoints, "plugin-adjusted points price");

                if (purchasedEvent.totalCredits > habbo.getHabboInfo().getCredits()
                        || purchasedEvent.totalPoints > habbo.getHabboInfo().getCurrencyAmount(item.getPointsType())) {
                    habbo.getClient().sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
                    return;
                }

                paidCredits = (!free && !habbo.hasPermission(Permission.ACC_INFINITE_CREDITS))
                        ? purchasedEvent.totalCredits : 0;
                paidPoints = (!free && !habbo.hasPermission(Permission.ACC_INFINITE_POINTS))
                        ? purchasedEvent.totalPoints : 0;
                paidPointsType = item.getPointsType();

                if (!CatalogPaymentService.tryTake(habbo, paidCredits, paidPointsType, paidPoints)) {
                    habbo.getClient().sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
                    return;
                }
                paymentTaken = true;

                for (Integer effectId : pendingEffects) {
                    habbo.getInventory().getEffectsComponent().createEffect(effectId);
                }
                for (Guild guild : pendingGuildForums) {
                    guild.setForum(true);
                    guild.needsUpdate = true;
                    guild.run();
                }

                if (purchasedEvent.itemsList != null && !purchasedEvent.itemsList.isEmpty()) {
                    habbo.getClient().getHabbo().getInventory().getItemsComponent().addItems(purchasedEvent.itemsList);
                    unseenItems.put(AddHabboItemComposer.AddHabboItemCategory.OWNED_FURNI, purchasedEvent.itemsList.stream().map(HabboItem::getId).collect(Collectors.toList()));

                    Emulator.getPluginManager().fireEvent(new UserCatalogFurnitureBoughtEvent(habbo, item, purchasedEvent.itemsList));

                    if (limitedConfiguration != null) {
                        for (HabboItem itm : purchasedEvent.itemsList) {
                            limitedConfiguration.limitedSold(item.getId(), habbo, itm);
                        }
                    }
                }

                if (!purchasedEvent.badges.isEmpty() && !unseenItems.containsKey(AddHabboItemComposer.AddHabboItemCategory.BADGE)) {
                    unseenItems.put(AddHabboItemComposer.AddHabboItemCategory.BADGE, new ArrayList<>());
                }

                for (String b : purchasedEvent.badges) {
                    HabboBadge badge = new HabboBadge(0, b, 0, habbo);
                    Emulator.getThreading().run(badge);
                    habbo.getInventory().getBadgesComponent().addBadge(badge);
                    habbo.getClient().sendResponse(new AddUserBadgeComposer(badge));
                    Map<String, String> keys = new HashMap<>();
                    keys.put("display", "BUBBLE");
                    keys.put("image", "${image.library.url}album1584/" + badge.getCode() + ".gif");
                    keys.put("message", Emulator.getTexts().getValue("commands.generic.cmd_badge.received"));
                    habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys));
                    unseenItems.get(AddHabboItemComposer.AddHabboItemCategory.BADGE).add(badge.getId());
                }

                // Durable items and wallet debit now agree. Nonessential response,
                // achievement, and logging failures must not undo a delivered order.
                purchaseDelivered = true;

                if (item.isLimited()) {
                    for (int i = 0; i < amount; i++) {
                        habbo.getHabboStats().addLtdLog(item.getId(), Emulator.getIntUnixTimestamp());
                    }
                }

                for (HabboItem createdItem : createdItems) {
                    if (purchasedEvent.itemsList == null || !purchasedEvent.itemsList.contains(createdItem)) {
                        Emulator.getGameEnvironment().getItemManager().deleteItem(createdItem);
                    }
                }
                habbo.getClient().getHabbo().getHabboStats().addPurchase(purchasedEvent.catalogItem);

                habbo.getClient().sendResponse(new AddHabboItemComposer(unseenItems));

                habbo.getClient().sendResponse(new PurchaseOKComposer(purchasedEvent.catalogItem));
                habbo.getClient().sendResponse(new InventoryRefreshComposer());

                Set<String> itemIds = new HashSet<>();

                for(HabboItem ix : purchasedEvent.itemsList) {
                    itemIds.add(ix.getId() + "");
                }

                if(!free) {
                    Emulator.getThreading().run(new CatalogPurchaseLogEntry(
                            Emulator.getIntUnixTimestamp(),
                            purchasedEvent.habbo.getHabboInfo().getId(),
                            purchasedEvent.catalogItem != null ? purchasedEvent.catalogItem.getId() : 0,
                            String.join(";", itemIds),
                            purchasedEvent.catalogItem != null ? purchasedEvent.catalogItem.getName() : "",
                            purchasedEvent.totalCredits,
                            purchasedEvent.totalPoints,
                            item != null ? item.getPointsType() : 0,
                            amount
                    ));
                }

            } catch (Exception e) {
                LOGGER.error("Exception caught", e);
                habbo.getClient().sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            }
        } finally {
            try {
                if (!purchaseDelivered) {
                    for (HabboItem createdItem : createdItems) {
                        if (createdItem != null) Emulator.getGameEnvironment().getItemManager().deleteItem(createdItem);
                    }
                    for (Bot bot : createdBots) {
                        habbo.getInventory().getBotsComponent().removeBot(bot);
                        Emulator.getGameEnvironment().getBotManager().deleteBot(bot);
                    }
                    for (Pet pet : createdPets) {
                        habbo.getInventory().getPetsComponent().removePet(pet);
                        Emulator.getGameEnvironment().getPetManager().deletePet(pet);
                    }
                    if (limitedConfiguration != null && limitedNumber > 0) {
                        // Return the reserved limited number to the pool so a failed
                        // purchase does not permanently shrink the stock or leave the
                        // item stranded on the sold-out page.
                        limitedConfiguration.restoreNumber(item.getId(), limitedNumber);
                    }
                    if (paymentTaken) {
                        CatalogPaymentService.refund(habbo, paidCredits, paidPointsType, paidPoints);
                    }
                }
            } finally {
                synchronized (habbo.getHabboStats()) {
                    habbo.getHabboStats().isPurchasingFurniture = false;
                }
            }
        }
    }

    private boolean isAtomicFurniturePurchase(CatalogItem item) {
        if (item == null || item.getBaseItems().isEmpty()) return false;
        for (Item baseItem : item.getBaseItems()) {
            if (baseItem == null || Item.isBot(baseItem) || Item.isPet(baseItem)
                    || baseItem.getType() == FurnitureType.BADGE || baseItem.getType() == FurnitureType.EFFECT) {
                return false;
            }
        }
        return true;
    }

    private boolean isAtomicEntitlementPurchase(CatalogItem item) {
        if (item == null || item.getBaseItems().isEmpty()) return false;
        for (Item baseItem : item.getBaseItems()) {
            if (baseItem == null || (baseItem.getType() != FurnitureType.BADGE
                    && baseItem.getType() != FurnitureType.EFFECT)) return false;
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

    private void purchaseBotsAndPetsAtomically(CatalogItem item, Habbo habbo, int amount, String extraData,
                                               boolean free, int totalCredits, int totalPoints) throws SQLException {
        String[] petData = extraData.split("\n");
        String operationId = EconomyOperationId.create(
                "catalog:" + habbo.getHabboInfo().getId() + ":" + item.getId());
        SpecialCompanionPurchase purchase = CatalogPurchaseTransaction.execute(
                habbo, operationId, connection -> {
                    List<Bot> bots = new ArrayList<>();
                    List<Pet> pets = new ArrayList<>();
                    for (int index = 0; index < amount; index++) {
                        for (Item baseItem : item.getBaseItems()) {
                            for (int count = 0; count < item.getItemAmount(baseItem.getId()); count++) {
                                if (Item.isBot(baseItem)) {
                                    String baseName = baseItem.getName();
                                    String type = item.getName().replace("rentable_bot_", "").replace("bot_", "")
                                            .replace("visitor_logger", "visitor_log");
                                    if (("bot_" + com.eu.habbo.habbohotel.bots.FrankBot.BOT_TYPE).equals(baseName)
                                            || ("rentable_bot_" + com.eu.habbo.habbohotel.bots.FrankBot.BOT_TYPE)
                                            .equals(baseName)) {
                                        if (!habbo.hasPermission(com.eu.habbo.habbohotel.bots.FrankBot.PERMISSION_USE)) {
                                            throw new SQLException("Missing permission for Frank bot");
                                        }
                                    }
                                    Map<String, String> data = new HashMap<>();
                                    for (String value : item.getExtradata().split(";")) {
                                        String[] pair = value.split(":", 2);
                                        if (pair.length == 2) data.put(pair[0], pair[1]);
                                    }
                                    Bot bot = Emulator.getGameEnvironment().getBotManager().createBot(connection,
                                            data, type, habbo.getHabboInfo().getId());
                                    if (bot == null) throw new SQLException("Unable to create catalog bot");
                                    bots.add(bot);
                                } else {
                                    if (petData.length < 3) throw new SQLException("Invalid catalog pet data");
                                    Pet pet = Emulator.getGameEnvironment().getPetManager().createPet(connection,
                                            baseItem, petData[0], petData[1], petData[2], habbo.getClient());
                                    if (pet == null) throw new SQLException("Unable to create catalog pet");
                                    pets.add(pet);
                                }
                            }
                        }
                    }

                    UserCatalogItemPurchasedEvent event = new UserCatalogItemPurchasedEvent(
                            habbo, item, new HashSet<>(), totalCredits, totalPoints, new ArrayList<>());
                    Emulator.getPluginManager().fireEvent(event);
                    ResolvedCatalogCharges charges = this.resolveCatalogCharges(habbo, item, free, event);
                    if (!free) this.writePurchaseLog(connection, event, charges, amount);
                    SpecialCompanionPurchase result = new SpecialCompanionPurchase(
                            event, bots, pets, charges.credits(), charges.points(), charges.pointsType());
                    return new CatalogPurchaseTransaction.PreparedPurchase<>(
                            result, charges.credits(), charges.points(), charges.pointsType());
                });

        this.publishCommittedCharges(habbo, purchase.credits(), purchase.points(), purchase.pointsType());
        Map<AddHabboItemComposer.AddHabboItemCategory, List<Integer>> unseenItems = new HashMap<>();
        for (Bot bot : purchase.bots()) {
            habbo.getInventory().getBotsComponent().addBot(bot);
            habbo.getClient().sendResponse(new AddBotComposer(bot));
            unseenItems.computeIfAbsent(AddHabboItemComposer.AddHabboItemCategory.BOT, ignored -> new ArrayList<>())
                    .add(bot.getId());
        }
        for (Pet pet : purchase.pets()) {
            habbo.getInventory().getPetsComponent().addPet(pet);
            habbo.getClient().sendResponse(new AddPetComposer(pet));
            habbo.getClient().sendResponse(new PetBoughtNotificationComposer(pet, false));
            AchievementManager.progressAchievement(habbo,
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("PetLover"));
            unseenItems.computeIfAbsent(AddHabboItemComposer.AddHabboItemCategory.PET, ignored -> new ArrayList<>())
                    .add(pet.getId());
        }
        habbo.getHabboStats().addPurchase(purchase.event().catalogItem);
        habbo.getClient().sendResponse(new AddHabboItemComposer(unseenItems));
        habbo.getClient().sendResponse(new PurchaseOKComposer(purchase.event().catalogItem));
        habbo.getClient().sendResponse(new InventoryRefreshComposer());
    }

    private void purchaseEntitlementsAtomically(CatalogItem item, Habbo habbo, int amount, boolean free,
                                                int totalCredits, int totalPoints) throws SQLException {
        List<String> requestedBadges = new ArrayList<>();
        Map<Integer, Integer> requestedEffects = new HashMap<>();
        for (int index = 0; index < amount; index++) {
            for (Item baseItem : item.getBaseItems()) {
                int count = item.getItemAmount(baseItem.getId());
                if (baseItem.getType() == FurnitureType.BADGE) {
                    if (habbo.getInventory().getBadgesComponent().hasBadge(baseItem.getName())) {
                        if (item.getBaseItems().size() == 1) {
                            habbo.getClient().sendResponse(new AlertPurchaseFailedComposer(
                                    AlertPurchaseFailedComposer.ALREADY_HAVE_BADGE));
                            return;
                        }
                    } else if (!requestedBadges.contains(baseItem.getName())) {
                        requestedBadges.add(baseItem.getName());
                    }
                } else {
                    int effectId = habbo.getHabboInfo().getGender() == HabboGender.F
                            ? baseItem.getEffectF() : baseItem.getEffectM();
                    if (effectId > 0) requestedEffects.merge(effectId, count, Integer::sum);
                }
            }
        }

        String operationId = EconomyOperationId.create(
                "catalog:" + habbo.getHabboInfo().getId() + ":" + item.getId());
        EntitlementPurchase purchase = CatalogPurchaseTransaction.execute(
                habbo, operationId, connection -> {
            UserCatalogItemPurchasedEvent event = new UserCatalogItemPurchasedEvent(
                    habbo, item, new HashSet<>(), totalCredits, totalPoints, new ArrayList<>(requestedBadges));
            Emulator.getPluginManager().fireEvent(event);
            ResolvedCatalogCharges charges = this.resolveCatalogCharges(habbo, item, free, event);

            List<HabboBadge> badges = new ArrayList<>();
            for (String code : event.badges) {
                if (habbo.getInventory().getBadgesComponent().hasBadge(code)) continue;
                HabboBadge badge = new HabboBadge(0, code, 0, habbo);
                badge.insert(connection);
                badges.add(badge);
            }

            Map<Integer, EffectsComponent.HabboEffect> effects = new HashMap<>();
            for (Map.Entry<Integer, Integer> requested : requestedEffects.entrySet()) {
                for (int count = 0; count < requested.getValue(); count++) {
                    EffectsComponent.HabboEffect effect = EffectsComponent.persistEffect(connection,
                            habbo.getHabboInfo().getId(), requested.getKey(), 86400);
                    effects.put(requested.getKey(), effect);
                }
            }

            if (!free) this.writePurchaseLog(connection, event, charges, amount);
            EntitlementPurchase result = new EntitlementPurchase(
                    event, badges, effects, charges.credits(), charges.points(), charges.pointsType());
            return new CatalogPurchaseTransaction.PreparedPurchase<>(
                    result, charges.credits(), charges.points(), charges.pointsType());
        });

        this.publishCommittedCharges(habbo, purchase.credits(), purchase.points(), purchase.pointsType());
        Map<AddHabboItemComposer.AddHabboItemCategory, List<Integer>> unseenItems = new HashMap<>();
        if (!purchase.badges().isEmpty()) {
            unseenItems.put(AddHabboItemComposer.AddHabboItemCategory.BADGE, new ArrayList<>());
        }
        for (HabboBadge badge : purchase.badges()) {
            habbo.getInventory().getBadgesComponent().addBadge(badge);
            habbo.getClient().sendResponse(new AddUserBadgeComposer(badge));
            unseenItems.get(AddHabboItemComposer.AddHabboItemCategory.BADGE).add(badge.getId());
            Map<String, String> keys = new HashMap<>();
            keys.put("display", "BUBBLE");
            keys.put("image", "${image.library.url}album1584/" + badge.getCode() + ".gif");
            keys.put("message", Emulator.getTexts().getValue("commands.generic.cmd_badge.received"));
            habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys));
        }
        for (EffectsComponent.HabboEffect effect : purchase.effects().values()) {
            habbo.getInventory().getEffectsComponent().publishEffect(effect);
        }
        habbo.getHabboStats().addPurchase(purchase.event().catalogItem);
        habbo.getClient().sendResponse(new AddHabboItemComposer(unseenItems));
        habbo.getClient().sendResponse(new PurchaseOKComposer(purchase.event().catalogItem));
        habbo.getClient().sendResponse(new InventoryRefreshComposer());
    }

    private void purchaseFurnitureAtomically(CatalogItem item, Habbo habbo, int amount, String extraData,
                                             boolean free, CatalogLimitedConfiguration limitedConfiguration,
                                             int limitedStack, int limitedNumber, int totalCredits, int totalPoints)
            throws SQLException {
        int userId = habbo.getHabboInfo().getId();
        try {
            String operationId = EconomyOperationId.create("catalog:" + userId + ":" + item.getId());
            AtomicFurniturePurchase purchase = CatalogPurchaseTransaction.execute(
                    habbo, operationId, connection -> {
                Set<HabboItem> createdItems = new HashSet<>();
                Map<InteractionGuildFurni, Guild> guildFurniture = new HashMap<>();
                boolean includesMusicDisc = false;
                for (int index = 0; index < amount; index++) {
                    for (Item baseItem : item.getBaseItems()) {
                        String itemExtraData = this.prepareFurnitureExtraData(habbo, baseItem, extraData);
                        for (int count = 0; count < item.getItemAmount(baseItem.getId()); count++) {
                            if (baseItem.getInteractionType().getType() == InteractionGuildFurni.class
                                    || baseItem.getInteractionType().getType() == InteractionGuildGate.class) {
                                int guildId;
                                try {
                                    guildId = Integer.parseInt(extraData);
                                } catch (NumberFormatException exception) {
                                    throw new SQLException("Invalid guild furniture id", exception);
                                }
                                Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);
                                if (guild == null || Emulator.getGameEnvironment().getGuildManager()
                                        .getGuildMember(guild, habbo) == null) {
                                    throw new SQLException("User cannot purchase furniture for guild " + guildId);
                                }
                                if (baseItem.getName().equals("guild_forum")
                                        && guild.getOwnerId() != habbo.getHabboInfo().getId()) {
                                    throw new SQLException("Only the guild owner can purchase its forum");
                                }
                                InteractionGuildFurni created = (InteractionGuildFurni) Emulator.getGameEnvironment()
                                        .getItemManager().createItem(connection, userId, baseItem,
                                                limitedStack, limitedNumber, "");
                                if (created == null) throw new SQLException("Unable to create guild furniture");
                                Emulator.getGameEnvironment().getGuildManager().persistGuild(connection,
                                        created.getId(), guildId);
                                created.setGuildId(guildId);
                                guildFurniture.put(created, guild);
                                createdItems.add(created);
                            } else if (baseItem.getInteractionType().getType() == InteractionMusicDisc.class) {
                                SoundTrack track = Emulator.getGameEnvironment().getItemManager()
                                        .getSoundTrack(item.getExtradata());
                                if (track == null) throw new SQLException("Unknown catalog music track");
                                itemExtraData = this.createMusicDiscExtraData(habbo, track);
                                HabboItem created = Emulator.getGameEnvironment().getItemManager().createItem(
                                        connection, userId, baseItem, limitedStack, limitedNumber, itemExtraData);
                                if (created == null) throw new SQLException("Unable to create music disc");
                                createdItems.add(created);
                                includesMusicDisc = true;
                            } else if (InteractionTeleport.class.isAssignableFrom(baseItem.getInteractionType().getType())) {
                                HabboItem first = Emulator.getGameEnvironment().getItemManager().createItem(
                                        connection, userId, baseItem, limitedStack, limitedNumber, itemExtraData);
                                HabboItem second = Emulator.getGameEnvironment().getItemManager().createItem(
                                        connection, userId, baseItem, limitedStack, limitedNumber, itemExtraData);
                                if (first == null || second == null) throw new SQLException("Unable to create teleport pair");
                                Emulator.getGameEnvironment().getItemManager().insertTeleportPair(
                                        connection, first.getId(), second.getId());
                                createdItems.add(first);
                                createdItems.add(second);
                            } else {
                                HabboItem created = Emulator.getGameEnvironment().getItemManager().createItem(
                                        connection, userId, baseItem, limitedStack, limitedNumber, itemExtraData);
                                if (created == null) throw new SQLException("Unable to create catalog furniture");
                                if (baseItem.getInteractionType().getType() == InteractionHopper.class) {
                                    Emulator.getGameEnvironment().getItemManager().insertHopper(connection, created);
                                }
                                createdItems.add(created);
                            }
                        }
                    }
                }

                Set<Integer> createdIds = createdItems.stream().map(HabboItem::getId).collect(Collectors.toSet());
                UserCatalogItemPurchasedEvent purchasedEvent = new UserCatalogItemPurchasedEvent(
                        habbo, item, createdItems, totalCredits, totalPoints, new ArrayList<>());
                Emulator.getPluginManager().fireEvent(purchasedEvent);
                Set<Integer> eventIds = purchasedEvent.itemsList.stream().map(HabboItem::getId).collect(Collectors.toSet());
                if (!createdIds.equals(eventIds)) {
                    throw new SQLException("Catalog plugin changed the atomic furniture set");
                }

                ResolvedCatalogCharges charges = this.resolveCatalogCharges(habbo, item, free, purchasedEvent);
                if (limitedConfiguration != null) {
                    HabboItem limitedItem = createdItems.stream().findFirst()
                            .orElseThrow(() -> new SQLException("Limited purchase created no furniture"));
                    limitedConfiguration.limitedSold(connection, item.getId(), habbo, limitedItem);
                }
                if (!free) {
                    this.writePurchaseLog(connection, purchasedEvent, charges, amount);
                }
                AtomicFurniturePurchase result = new AtomicFurniturePurchase(
                        purchasedEvent, guildFurniture, includesMusicDisc,
                        charges.credits(), charges.points(), charges.pointsType());
                return new CatalogPurchaseTransaction.PreparedPurchase<>(
                        result, charges.credits(), charges.points(), charges.pointsType());
            });

            this.publishAtomicFurniturePurchase(habbo, purchase);
            if (limitedConfiguration != null) {
                habbo.getHabboStats().addLtdLog(item.getId(), Emulator.getIntUnixTimestamp());
                limitedConfiguration.markSoldOutIfEmpty();
            }
        } catch (SQLException exception) {
            if (limitedConfiguration != null && limitedNumber > 0) limitedConfiguration.restoreNumber(limitedNumber);
            throw exception;
        }
    }

    private String prepareFurnitureExtraData(Habbo habbo, Item baseItem, String extraData) {
        if (baseItem.getInteractionType().getType() != InteractionTrophy.class
                && baseItem.getInteractionType().getType() != InteractionBadgeDisplay.class) return extraData;

        String value = extraData;
        if (baseItem.getInteractionType().getType() == InteractionBadgeDisplay.class
                && !habbo.getInventory().getBadgesComponent().hasBadge(value)) {
            ScripterManager.scripterDetected(habbo.getClient(),
                    Emulator.getTexts().getValue("scripter.warning.catalog.badge_display")
                            .replace("%username%", habbo.getHabboInfo().getUsername())
                            .replace("%badge%", value));
            value = "UMAD";
        }
        int maximum = Emulator.getConfig().getInt("hotel.trophies.length.max", 300);
        if (value.length() > maximum) value = value.substring(0, maximum);
        return habbo.getHabboInfo().getUsername() + (char) 9
                + Calendar.getInstance().get(Calendar.DAY_OF_MONTH) + "-"
                + (Calendar.getInstance().get(Calendar.MONTH) + 1) + "-"
                + Calendar.getInstance().get(Calendar.YEAR) + (char) 9
                + Emulator.getGameEnvironment().getWordFilter().filter(value.replace(((char) 9) + "", ""), habbo);
    }

    private String createMusicDiscExtraData(Habbo habbo, SoundTrack track) {
        Calendar calendar = Calendar.getInstance();
        return habbo.getHabboInfo().getUsername() + "\n"
                + calendar.get(Calendar.DAY_OF_MONTH) + "\n"
                + (calendar.get(Calendar.MONTH) + 1) + "\n"
                + calendar.get(Calendar.YEAR) + "\n"
                + track.getLength() + "\n" + track.getName() + "\n" + track.getId();
    }

    private ResolvedCatalogCharges resolveCatalogCharges(Habbo habbo, CatalogItem item, boolean free,
                                                         UserCatalogItemPurchasedEvent purchasedEvent) {
        int credits = 0;
        int points = 0;
        int pointsType = item.getPointsType();
        if (!free && !habbo.hasPermission(Permission.ACC_INFINITE_CREDITS) && purchasedEvent.totalCredits > 0) {
            UserCreditsEvent event = new UserCreditsEvent(habbo, -purchasedEvent.totalCredits);
            if (!Emulator.getPluginManager().fireEvent(event).isCancelled()) credits = Math.max(0, -event.credits);
        }
        if (!free && !habbo.hasPermission(Permission.ACC_INFINITE_POINTS) && purchasedEvent.totalPoints > 0) {
            UserPointsEvent event = new UserPointsEvent(habbo, -purchasedEvent.totalPoints, pointsType);
            if (!Emulator.getPluginManager().fireEvent(event).isCancelled()) {
                points = Math.max(0, -event.points);
                pointsType = event.type;
            }
        }
        return new ResolvedCatalogCharges(credits, points, pointsType);
    }

    private void writePurchaseLog(Connection connection, UserCatalogItemPurchasedEvent event,
                                  ResolvedCatalogCharges charges, int amount) throws SQLException {
        Set<String> itemIds = event.itemsList.stream().map(item -> Integer.toString(item.getId())).collect(Collectors.toSet());
        CatalogPurchaseLogEntry entry = new CatalogPurchaseLogEntry(
                Emulator.getIntUnixTimestamp(), event.habbo.getHabboInfo().getId(), event.catalogItem.getId(),
                String.join(";", itemIds), event.catalogItem.getName(), charges.credits(), charges.points(),
                charges.pointsType(), amount);
        try (PreparedStatement statement = connection.prepareStatement(entry.getQuery())) {
            entry.log(statement);
            statement.executeBatch();
        }
    }

    private void publishAtomicFurniturePurchase(Habbo habbo, AtomicFurniturePurchase purchase) {
        this.publishCommittedCharges(habbo, purchase.credits(), purchase.points(), purchase.pointsType());

        Set<HabboItem> items = purchase.event().itemsList;
        habbo.getInventory().getItemsComponent().addItems(items);
        Map<AddHabboItemComposer.AddHabboItemCategory, List<Integer>> unseenItems = new HashMap<>();
        unseenItems.put(AddHabboItemComposer.AddHabboItemCategory.OWNED_FURNI,
                items.stream().map(HabboItem::getId).collect(Collectors.toList()));
        Emulator.getPluginManager().fireEvent(new UserCatalogFurnitureBoughtEvent(
                habbo, purchase.event().catalogItem, items));
        for (Map.Entry<InteractionGuildFurni, Guild> guildEntry : purchase.guildFurniture().entrySet()) {
            if (guildEntry.getKey().getBaseItem().getName().equals("guild_forum")) {
                guildEntry.getValue().setForum(true);
                guildEntry.getValue().needsUpdate = true;
                Emulator.getThreading().run(guildEntry.getValue());
            }
        }
        if (purchase.includesMusicDisc()) {
            AchievementManager.progressAchievement(habbo,
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("MusicCollector"));
        }
        habbo.getHabboStats().addPurchase(purchase.event().catalogItem);
        habbo.getClient().sendResponse(new AddHabboItemComposer(unseenItems));
        habbo.getClient().sendResponse(new PurchaseOKComposer(purchase.event().catalogItem));
        habbo.getClient().sendResponse(new InventoryRefreshComposer());
    }

    private void publishCommittedCharges(Habbo habbo, int credits, int points, int pointsType) {
        if (credits > 0) {
            habbo.getClient().sendResponse(new UserCreditsComposer(habbo));
        }
        if (points > 0) {
            habbo.getClient().sendResponse(new UserPointsComposer(
                    habbo.getHabboInfo().getCurrencyAmount(pointsType), -points, pointsType));
        }
    }

    private record ResolvedCatalogCharges(int credits, int points, int pointsType) {
    }

    private record AtomicFurniturePurchase(UserCatalogItemPurchasedEvent event,
                                           Map<InteractionGuildFurni, Guild> guildFurniture,
                                           boolean includesMusicDisc, int credits, int points, int pointsType) {
    }

    private record EntitlementPurchase(UserCatalogItemPurchasedEvent event, List<HabboBadge> badges,
                                       Map<Integer, EffectsComponent.HabboEffect> effects, int credits, int points,
                                       int pointsType) {
    }

    private record SpecialCompanionPurchase(UserCatalogItemPurchasedEvent event, List<Bot> bots, List<Pet> pets,
                                            int credits, int points, int pointsType) {
    }

    public List<ClubOffer> getClubOffers() {
        return this.getClubOffers(ClubOffer.WINDOW_HABBO_CLUB);
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
        return this.targetOffers.get(offerId);
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

        int totalDiscountedItems = (basicDiscount * DiscountComposer.DISCOUNT_AMOUNT_PER_BATCH) + bonusDiscount + additionalDiscounts;

        int payableItems = Math.max(0, amount - totalDiscountedItems);
        return CatalogPurchaseMath.checkedPrice(originalPrice, payableItems);
    }
}
