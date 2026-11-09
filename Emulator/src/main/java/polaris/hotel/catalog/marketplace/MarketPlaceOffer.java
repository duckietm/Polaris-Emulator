package polaris.hotel.catalog.marketplace;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public class MarketPlaceOffer implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketPlaceOffer.class);

    public int avarage;
    public int count;
    private int offerId;
    private Item baseItem;
    private int itemId;
    private int price;
    private int limitedStack;
    private int limitedNumber;
    private int timestamp = Emulator.getIntUnixTimestamp();
    private int soldTimestamp = 0;
    private MarketPlaceState state = MarketPlaceState.OPEN;
    private boolean needsUpdate = false;
    /** Posted through the Collectibles marketplace (AIR offer type 5): priced in emeralds, see {@link NftMarketplaceSettings}. */
    private boolean nft = false;

    public MarketPlaceOffer(ResultSet set, boolean privateOffer) throws SQLException {
        this.offerId = set.getInt("id");
        this.price = set.getInt("price");
        this.timestamp = set.getInt("timestamp");
        this.soldTimestamp = set.getInt("sold_timestamp");
        this.baseItem = Emulator.getGameEnvironment().getItemManager().getItem(set.getInt("base_item_id"));
        this.state = MarketPlaceState.getType(set.getInt("state"));
        this.itemId = set.getInt("item_id");
        this.nft = readNft(set);

        if (!set.getString("ltd_data").split(":")[1].equals("0")) {
            this.limitedStack = Integer.parseInt(set.getString("ltd_data").split(":")[0]);
            this.limitedNumber = Integer.parseInt(set.getString("ltd_data").split(":")[1]);
        }

        if (!privateOffer) {
            this.avarage = set.getInt("avg");
            this.count = set.getInt("number");
            this.price = set.getInt("minPrice");
        }
    }

    public MarketPlaceOffer(UserItem item, int price, User user) {
        this(item, price, user, false);
    }

    /** Posts a new offer; {@code nft} marks a Collectibles marketplace offer. */
    public MarketPlaceOffer(UserItem item, int price, User user, boolean nft) {
        this.nft = nft;
        this.price = price;
        this.baseItem = item.getBaseItem();
        this.itemId = item.getId();
        if (item.getLimitedSells() > 0) {
            this.limitedNumber = item.getLimitedSells();
            this.limitedStack = item.getLimitedStack();
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO marketplace_items (item_id, user_id, price, timestamp, state, nft) VALUES (?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, item.getId());
            statement.setInt(2, user.getUserInfo().getId());
            statement.setInt(3, this.price);
            statement.setInt(4, this.timestamp);
            statement.setString(5, this.state.getState() + "");
            statement.setInt(6, this.nft ? 1 : 0);
            statement.execute();

            try (ResultSet id = statement.getGeneratedKeys()) {
                while (id.next()) {
                    this.offerId = id.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static void insert(MarketPlaceOffer offer, User user) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO marketplace_items VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, offer.getItemId());
            statement.setInt(2, user.getUserInfo().getId());
            statement.setInt(3, offer.getPrice());
            statement.setInt(4, offer.getTimestamp());
            statement.setInt(5, offer.getSoldTimestamp());
            statement.setString(6, offer.getState().getState() + "");
            statement.execute();

            try (ResultSet id = statement.getGeneratedKeys()) {
                while (id.next()) {
                    offer.setOfferId(id.getInt(1));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public int getOfferId() {
        return this.offerId;
    }

    public boolean isPersisted() {
        return this.offerId > 0;
    }

    public void setOfferId(int offerId) {
        this.offerId = offerId;
    }

    public int getItemId() {
        return this.baseItem.getSpriteId();
    }

    public int getPrice() {
        return this.price;
    }

    public MarketPlaceState getState() {
        return this.state;
    }

    public void setState(MarketPlaceState state) {
        this.state = state;
    }

    public int getTimestamp() {
        return this.timestamp;
    }

    public int getSoldTimestamp() {
        return this.soldTimestamp;
    }

    public void setSoldTimestamp(int soldTimestamp) {
        this.soldTimestamp = soldTimestamp;
    }

    public int getLimitedStack() {
        return this.limitedStack;
    }

    public int getLimitedNumber() {
        return this.limitedNumber;
    }

    public int getSoldItemId() {
        return this.itemId;
    }

    public void needsUpdate(boolean value) {
        this.needsUpdate = value;
    }

    public boolean isNft() {
        return this.nft;
    }

    /** AIR's product code of a collectible offer: the furni's class name. */
    public String getProductCode() {
        return this.baseItem.getName();
    }

    /** The furni kind of a collectible offer: 1 floor, 2 wall. */
    public int getNftFurniType() {
        return this.baseItem.getType().equals(FurnitureType.WALL) ? 2 : 1;
    }

    /** How long the offer stays on sale: 48 hours, or the collectibles offer time. */
    public int getListingWindowSeconds() {
        return this.nft
                ? NftMarketplaceSettings.current().offerSeconds()
                : MarketplaceOffersQuery.LISTING_WINDOW_SECONDS;
    }

    /** Seconds left before an open offer expires; 0 or less once it has. */
    public int secondsLeft(int now) {
        return (this.timestamp + this.getListingWindowSeconds()) - now;
    }

    /** What the buyer pays: the asking price plus the commission, or the asking price itself for a collectible. */
    public int getBuyerPrice() {
        return this.nft ? this.price : MarketPlace.calculateCommision(this.price);
    }

    /** The search's average in the buyer's unit, like {@link #getBuyerPrice()}. */
    public int getBuyerAverage() {
        return this.nft ? this.avarage : MarketPlace.calculateCommision(this.avarage);
    }

    private static boolean readNft(ResultSet set) {
        try {
            return set.getInt("nft") == 1;
        } catch (SQLException e) {
            // A plugin's own query without the column: an offer from before the collectibles marketplace.
            return false;
        }
    }

    public int getType() {
        if (this.nft) {
            return 5;
        }

        if (this.limitedStack > 0) {
            return 3;
        }

        return this.baseItem.getType().equals(FurnitureType.WALL) ? 2 : 1;
    }

    @Override
    public void run() {
        if (this.needsUpdate) {
            this.needsUpdate = false;
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "UPDATE marketplace_items SET state = ?, sold_timestamp = ? WHERE id = ?")) {
                statement.setInt(1, this.state.getState());
                statement.setInt(2, this.soldTimestamp);
                statement.setInt(3, this.offerId);
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }
}
