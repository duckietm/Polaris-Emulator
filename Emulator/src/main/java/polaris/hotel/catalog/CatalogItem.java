package polaris.hotel.catalog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.rentable.RentableFurniture;
import polaris.hotel.users.ChatStyleRepository;
import polaris.messages.ISerialize;
import polaris.messages.ServerMessage;

public class CatalogItem implements ISerialize, Runnable, Comparable<CatalogItem> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogItem.class);
    int id;
    int limitedStack;
    private int pageId;
    private String itemId;

    /** The furni of a bundle that names offers, as last read from the catalogue; null until it is read. */
    private String resolvedItemId;

    private String name;
    private int credits;
    private int points;
    private short pointsType;
    private int amount;
    private boolean allowGift = false;
    private int limitedSells;

    private String extradata;

    private boolean clubOnly;

    private boolean haveOffer;

    private int offerId;

    private boolean needsUpdate;

    private int orderNumber;

    private int rentDays;

    private int habbiconId;

    private Map<Integer, Integer> bundle;

    public CatalogItem(ResultSet set) throws SQLException {
        this.load(set);
        this.needsUpdate = false;
    }

    public static boolean haveOffer(CatalogItem item) {
        if (!item.haveOffer || item.getHabbiconId() > 0) return false;

        if (item.getAmount() != 1) return false;

        if (item.isLimited()) return false;

        if (item.getBundle().size() > 1) return false;

        if (item.getName().toLowerCase().startsWith("cf_")
                || item.getName().toLowerCase().startsWith("cfc_")) return false;

        for (Item i : item.getBaseItems()) {
            if (i.getName().toLowerCase().startsWith("cf_")
                    || i.getName().toLowerCase().startsWith("cfc_")
                    || i.getName().toLowerCase().startsWith("rentable_bot")) return false;
        }

        return !item.getName().toLowerCase().startsWith("rentable_bot_");
    }

    public void update(ResultSet set) throws SQLException {
        this.load(set);
    }

    private void load(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.pageId = set.getInt("page_id");
        this.itemId = set.getString("item_Ids");
        this.name = set.getString("catalog_name");
        this.credits = set.getInt("cost_credits");
        this.points = set.getInt("cost_points");
        this.pointsType = set.getShort("points_type");
        this.amount = set.getInt("amount");
        this.limitedStack = set.getInt("limited_stack");
        this.limitedSells = set.getInt("limited_sells");
        this.extradata = set.getString("extradata");
        this.clubOnly = set.getBoolean("club_only");
        this.haveOffer = set.getBoolean("have_offer");
        this.offerId = set.getInt("offer_id");
        this.orderNumber = set.getInt("order_number");
        this.rentDays = RentableFurniture.readRentDays(set);
        this.habbiconId = 0;
        for (int column = 1; column <= set.getMetaData().getColumnCount(); column++) {
            if ("habbicon_id".equalsIgnoreCase(set.getMetaData().getColumnLabel(column))) {
                this.habbiconId = set.getInt("habbicon_id");
                break;
            }
        }

        this.bundle = new HashMap<>();
        this.loadBundle();
    }

    public int getHabbiconId() {
        return this.habbiconId;
    }

    public int getId() {
        return this.id;
    }

    public int getPageId() {
        return this.pageId;
    }

    public void setPageId(int pageId) {
        this.pageId = pageId;
    }

    public String getItemId() {
        return this.itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getName() {
        return this.name;
    }

    public int getCredits() {
        return this.credits;
    }

    public int getPoints() {
        return this.points;
    }

    public int getPointsType() {
        return this.pointsType;
    }

    public int getAmount() {
        return this.amount;
    }

    public int getLimitedStack() {
        return this.limitedStack;
    }

    public int getLimitedSells() {
        CatalogLimitedConfiguration ltdConfig =
                Emulator.getGameEnvironment().getCatalogManager().getLimitedConfig(this);

        if (ltdConfig != null) {
            return this.limitedStack - ltdConfig.available();
        }

        return this.limitedStack;
    }

    public String getExtradata() {
        return this.extradata;
    }

    /** Default length of a bought effect when the offer does not set one (one day). */
    public static final int DEFAULT_EFFECT_DURATION = 86400;

    /** Longest effect an offer can sell (one year); 0 is permanent. */
    public static final int MAX_EFFECT_DURATION = 31536000;

    /**
     * How long an effect from this offer lasts, from catalog_items.extradata: "permanent" or "0" for a
     * permanent effect, a number of seconds for a timed one, empty for the one-day default.
     */
    public int getEffectDuration() {
        return effectDuration(this.extradata);
    }

    static int effectDuration(String extradata) {
        String value = extradata == null ? "" : extradata.trim();

        if (value.isEmpty()) return DEFAULT_EFFECT_DURATION;
        if (value.equalsIgnoreCase("permanent")) return 0;

        try {
            int seconds = Integer.parseInt(value);

            if (seconds <= 0) return 0;

            return Math.min(seconds, MAX_EFFECT_DURATION);
        } catch (NumberFormatException exception) {
            return DEFAULT_EFFECT_DURATION;
        }
    }

    /** Class id the client gets for a product: the effect id for effects (icon and preview), else the sprite id. */
    static int catalogClassId(Item item) {
        if (item.getType() == FurnitureType.EFFECT && item.getEffectM() > 0) return item.getEffectM();

        return item.getSpriteId();
    }

    public boolean isClubOnly() {
        return this.clubOnly;
    }

    public boolean isHaveOffer() {
        return this.haveOffer;
    }

    public int getRentDays() {
        return this.rentDays;
    }

    public boolean isRentOffer() {
        return this.rentDays > 0;
    }

    public int getOfferId() {
        return this.offerId;
    }

    public int getSearchOfferId() {
        if (this.offerId > 0) {
            return this.offerId;
        }

        return haveOffer(this) ? this.id : -1;
    }

    public boolean isLimited() {
        return this.limitedStack > 0;
    }

    private int getOrderNumber() {
        return this.orderNumber;
    }

    public void setOrderNumber(int orderNumber) {
        this.orderNumber = orderNumber;
    }

    public void setNeedsUpdate(boolean needsUpdate) {
        this.needsUpdate = needsUpdate;
    }

    public synchronized void sellRare() {
        this.limitedSells++;

        this.needsUpdate = true;

        Emulator.getThreading().run(this);
    }

    public Set<Item> getBaseItems() {
        Set<Item> items = new LinkedHashSet<>();
        String content = this.contentItemIds();

        if (!content.isEmpty()) {
            String[] itemIds = content.split(";");

            for (String itemId : itemIds) {
                if (itemId.isEmpty()) continue;

                if (itemId.contains(":")) {
                    itemId = itemId.split(":")[0];
                }

                int identifier;
                try {

                    identifier = Integer.parseInt(itemId);
                } catch (Exception e) {
                    LOGGER.info(
                            "Invalid value ({}) for items_base column for catalog_item id ({}). Value must be integer or of the format of integer:amount;integer:amount",
                            itemId,
                            this.id);
                    continue;
                }
                if (identifier > 0) {
                    Item item = Emulator.getGameEnvironment().getItemManager().getItem(identifier);

                    if (item != null) items.add(item);
                }
            }
        }

        return items;
    }

    public int getItemAmount(int id) {
        this.contentItemIds();
        if (this.bundle.containsKey(id)) return this.bundle.get(id);
        else return this.amount;
    }

    public Map<Integer, Integer> getBundle() {
        this.contentItemIds();
        return this.bundle;
    }

    /**
     * The item list the offer hands out. A bundle that names catalogue offers ({@code @<offer id>}, see
     * {@link BundleOfferReferences}) is written out as their furni here, read from the catalogue as it is now, so the
     * bundle follows the offers it names. Any other offer gives its {@code item_ids} unchanged.
     */
    private synchronized String contentItemIds() {
        String raw = this.itemId == null ? "" : this.itemId;
        if (!BundleOfferReferences.hasReferences(raw)) return raw;

        polaris.hotel.GameEnvironment environment = polaris.WiredPlatform.gameEnvironment();
        CatalogManager catalog = environment == null ? null : environment.getCatalogManager();
        if (catalog == null) return this.resolvedItemId == null ? "" : this.resolvedItemId;

        BundleOfferReferences.Expansion expansion = BundleOfferReferences.expand(raw, this.amount, offerId -> {
            CatalogItem offer = offerId == this.id ? null : catalog.getCatalogItem(offerId);
            return offer == null ? null : new BundleOfferReferences.Content(offer.itemId, offer.amount);
        });

        if (!expansion.itemIds().equals(this.resolvedItemId)) {
            if (!expansion.missing().isEmpty()) {
                LOGGER.warn(
                        "Catalogue bundle {} names offer(s) {} that cannot be used (unknown, or a loop); they are left out",
                        this.id,
                        expansion.missing());
            }
            this.resolvedItemId = expansion.itemIds();
            this.bundle = new HashMap<>();
            this.readBundle(this.resolvedItemId);
        }

        return this.resolvedItemId;
    }

    public void loadBundle() {
        // A bundle that names offers is read when it is first used: the offers it names may not be loaded yet.
        if (BundleOfferReferences.hasReferences(this.itemId)) {
            this.resolvedItemId = null;
            return;
        }

        this.readBundle(this.itemId);
    }

    private void readBundle(String content) {
        int intItemId;

        if (content.contains(";")) {
            try {
                String[] itemIds = content.split(";");

                for (String itemId : itemIds) {
                    if (itemId.contains(":")) {
                        String[] data = itemId.split(":");
                        if (data.length > 1 && Integer.parseInt(data[0]) > 0 && Integer.parseInt(data[1]) > 0) {
                            this.bundle.put(Integer.parseInt(data[0]), Integer.parseInt(data[1]));
                        }
                    } else {
                        if (!itemId.isEmpty()) {
                            intItemId = (Integer.parseInt(itemId));
                            this.bundle.put(intItemId, 1);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to load {}", content);
                LOGGER.error("Caught exception", e);
            }
        } else {
            try {
                Item item = Emulator.getGameEnvironment().getItemManager().getItem(Integer.parseInt(content));

                if (item != null) {
                    this.allowGift = item.allowGift();
                }
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendInt(this.getId());
        message.appendString(this.getName());
        message.appendBoolean(this.isRentOffer());
        message.appendInt(this.getCredits());
        message.appendInt(this.getPoints());
        message.appendInt(this.getPointsType());
        // A chat bubble style is granted to the buyer's account, so it cannot be wrapped as a gift.
        int chatStyleId = this.habbiconId > 0 ? 0 : ChatStyleRepository.styleForCatalogItem(this.getId());
        message.appendBoolean(this.habbiconId <= 0 && chatStyleId <= 0 && this.allowGift);

        Set<Item> items = this.habbiconId > 0 ? Set.of() : this.getBaseItems();

        message.appendInt(this.habbiconId > 0 ? 1 : items.size() + (chatStyleId > 0 ? 1 : 0));

        if (this.habbiconId > 0) {
            message.appendString("habbicon");
            message.appendInt(this.habbiconId);
            message.appendString(Integer.toString(this.habbiconId));
            message.appendInt(1);
            message.appendBoolean(false);
        }

        for (Item item : items) {
            message.appendString(item.getType().code.toLowerCase());

            if (item.getType() == FurnitureType.BADGE) {
                message.appendString(item.getName());
            } else {
                message.appendInt(catalogClassId(item));

                if (this.getName().contains("wallpaper_single")
                        || this.getName().contains("floor_single")
                        || this.getName().contains("landscape_single")) {
                    message.appendString(this.getName().split("_")[2]);
                } else if (item.getName().contains("bot") && item.getType() == FurnitureType.ROBOT) {
                    boolean lookFound = false;
                    for (String s : this.getExtradata().split(";")) {
                        if (s.startsWith("figure:")) {
                            lookFound = true;
                            message.appendString(s.replace("figure:", ""));
                            break;
                        }
                    }

                    if (!lookFound) {
                        message.appendString(this.getExtradata());
                    }
                } else if (item.getType() == FurnitureType.ROBOT) {
                    message.appendString(this.getExtradata());
                } else if (item.getName().equalsIgnoreCase("poster")) {
                    message.appendString(this.getExtradata());
                } else if (this.getName().startsWith("SONG ")) {
                    message.appendString(this.getExtradata());
                } else if (item.getType() == FurnitureType.EFFECT) {
                    // The effect's length in seconds, 0 for permanent, so the catalog can show it.
                    message.appendString(Integer.toString(this.getEffectDuration()));
                } else {
                    message.appendString("");
                }
                message.appendInt(this.getItemAmount(item.getId()));
                message.appendBoolean(this.isLimited());
                if (this.isLimited()) {
                    message.appendInt(this.getLimitedStack());
                    message.appendInt(this.getLimitedStack() - this.getLimitedSells());
                }
            }
        }

        if (chatStyleId > 0) {
            // AIR 13 PRODUCT_TYPE_CHAT_STYLE: the style id is the class id (recycler reward
            // wrapper) and the extra param (catalog preview, Offer chat style id).
            message.appendString("chat_style");
            message.appendInt(chatStyleId);
            message.appendString(Integer.toString(chatStyleId));
            message.appendInt(1);
            message.appendBoolean(false);
        }

        message.appendInt(this.clubOnly);
        message.appendBoolean(haveOffer(this));
        message.appendBoolean(false);
        message.appendString(this.name + ".png");
        message.appendString(this.itemId == null ? "" : this.itemId);
        message.appendBoolean(this.haveOffer);
    }

    @Override
    public void run() {
        if (this.needsUpdate) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "UPDATE catalog_items SET limited_sells = ?, page_id = ? WHERE id = ?")) {
                statement.setInt(1, this.getLimitedSells());
                statement.setInt(2, this.pageId);
                statement.setInt(3, this.getId());
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }

            this.needsUpdate = false;
        }
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public int compareTo(CatalogItem catalogItem) {
        if (CatalogManager.SORT_USING_ORDERNUM) {
            return this.getOrderNumber() - catalogItem.getOrderNumber();
        } else {
            return this.getId() - catalogItem.getId();
        }
    }
}
