package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.messages.ISerialize;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

public class CatalogItem implements ISerialize, Runnable, Comparable<CatalogItem> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogItem.class);
    int id;
    int limitedStack;
    private int pageId;
    private String itemId;
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


    private HashMap<Integer, Integer> bundle;

    public CatalogItem(ResultSet set) throws SQLException {
        this.load(set);
        this.needsUpdate = false;
    }

    public static boolean haveOffer(CatalogItem item) {
        if (!item.haveOffer)
            return false;

        if (item.getAmount() != 1)
            return false;

        if (item.isLimited())
            return false;

        if (item.bundle.size() > 1)
            return false;

        if (item.getName().toLowerCase().startsWith("cf_") || item.getName().toLowerCase().startsWith("cfc_"))
            return false;

        for (Item i : item.getBaseItems()) {
            if (i.getName().toLowerCase().startsWith("cf_") || i.getName().toLowerCase().startsWith("cfc_") || i.getName().toLowerCase().startsWith("rentable_bot"))
                return false;
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

        this.bundle = new HashMap<>();
        this.loadBundle();
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
        CatalogLimitedConfiguration ltdConfig = Emulator.getGameEnvironment().getCatalogManager().getLimitedConfig(this);

        if (ltdConfig != null) {
            return this.limitedStack - ltdConfig.available();
        }

        return this.limitedStack;
    }

    public String getExtradata() {
        return this.extradata;
    }

    public boolean isClubOnly() {
        return this.clubOnly;
    }

    public boolean isHaveOffer() {
        return this.haveOffer;
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

    public void setNeedsUpdate(boolean needsUpdate) {
        this.needsUpdate = needsUpdate;
    }

    public synchronized void sellRare() {
        this.limitedSells++;

        this.needsUpdate = true;

        if (this.limitedSells == this.limitedStack) {
            Emulator.getGameEnvironment().getCatalogManager().moveCatalogItem(this, Emulator.getConfig().getInt("catalog.ltd.page.soldout"));
        }

        Emulator.getThreading().run(this);
    }

    public THashSet<Item> getBaseItems() {
        THashSet<Item> items = new THashSet<>();

        if (!this.itemId.isEmpty()) {
            String[] itemIds = this.itemId.split(";");

            for (String itemId : itemIds) {
                if (itemId.isEmpty())
                    continue;

                if (itemId.contains(":")) {
                    itemId = itemId.split(":")[0];
                }

                int identifier;
                try {

                    identifier = Integer.parseInt(itemId);
                } catch (Exception e) {
                    LOGGER.info("Invalid value ({}) for items_base column for catalog_item id ({}). Value must be integer or of the format of integer:amount;integer:amount", itemId, this.id);
                    continue;
                }
                if (identifier > 0) {
                    Item item = Emulator.getGameEnvironment().getItemManager().getItem(identifier);

                    if (item != null)
                        items.add(item);
                }
            }
        }

        return items;
    }

    public int getItemAmount(int id) {
        if (this.bundle.containsKey(id))
            return this.bundle.get(id);
        else
            return this.amount;
    }

    public HashMap<Integer, Integer> getBundle() {
        return this.bundle;
    }

    public void loadBundle() {
        int intItemId;

        if (this.itemId.contains(";")) {
            try {
                String[] itemIds = this.itemId.split(";");

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
                LOGGER.debug("Failed to load {}", this.itemId);
                LOGGER.error("Caught exception", e);
            }
        } else {
            try {
                Item item = Emulator.getGameEnvironment().getItemManager().getItem(Integer.parseInt(this.itemId));

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
        message.appendBoolean(false);
        message.appendInt(this.getCredits());
        message.appendInt(this.getPoints());
        message.appendInt(this.getPointsType());
        message.appendBoolean(this.allowGift); //Can gift

        THashSet<Item> items = this.getBaseItems();

        message.appendInt(items.size());

        for (Item item : items) {
            message.appendString(item.getType().code.toLowerCase());

            if (item.getType() == FurnitureType.BADGE) {
                message.appendString(item.getName());
            } else {
                message.appendInt(item.getSpriteId());

                if (this.getName().contains("wallpaper_single") || this.getName().contains("floor_single") || this.getName().contains("landscape_single")) {
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

        message.appendInt(this.clubOnly);
        message.appendBoolean(haveOffer(this));
        message.appendBoolean(false); //unknown
        message.appendString(this.name + ".png");
        message.appendString(this.itemId == null ? "" : this.itemId);
        message.appendBoolean(this.haveOffer);
    }

    @Override
    public void run() {
        if (this.needsUpdate) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE catalog_items SET limited_sells = ?, page_id = ? WHERE id = ?")) {
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
