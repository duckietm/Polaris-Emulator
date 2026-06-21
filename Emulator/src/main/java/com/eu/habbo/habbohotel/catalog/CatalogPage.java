package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.messages.ISerialize;
import com.eu.habbo.messages.ServerMessage;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public abstract class CatalogPage implements Comparable<CatalogPage>, ISerialize {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogPage.class);

    protected final IntList offerIds = new IntArrayList();
    protected final Map<Integer, CatalogPage> childPages = new HashMap<>();
    private final Int2ObjectMap<CatalogItem> catalogItems = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
    private final ArrayList<Integer> included = new ArrayList<>();
    protected int id;
    protected int parentId;
    protected int rank;
    protected String caption;
    protected String pageName;
    protected int iconColor;
    protected int iconImage;
    protected int orderNum;
    protected boolean visible;
    protected boolean enabled;
    protected boolean clubOnly;
    protected CatalogPageType catalogPageType = CatalogPageType.NORMAL;
    protected String layout;
    protected String headerImage;
    protected String teaserImage;
    protected String specialImage;
    protected String textOne;
    protected String textTwo;
    protected String textDetails;
    protected String textTeaser;

    public CatalogPage() {
    }

    public CatalogPage(ResultSet set) throws SQLException {
        if (set == null)
            return;

        this.id = set.getInt("id");
        this.parentId = set.getInt("parent_id");
        this.rank = set.getInt("min_rank");
        this.caption = set.getString("caption");
        this.pageName = set.getString("caption_save");
        this.iconColor = set.getInt("icon_color");
        this.iconImage = set.getInt("icon_image");
        this.orderNum = set.getInt("order_num");
        this.visible = set.getBoolean("visible");
        this.enabled = set.getBoolean("enabled");
        this.clubOnly = set.getBoolean("club_only");
        try {
            this.catalogPageType = CatalogPageType.fromString(set.getString("catalog_mode"));
        } catch (SQLException ignored) {
            this.catalogPageType = CatalogPageType.NORMAL;
        }
        this.layout = set.getString("page_layout");
        this.headerImage = set.getString("page_headline");
        this.teaserImage = set.getString("page_teaser");
        this.specialImage = set.getString("page_special");
        this.textOne = set.getString("page_text1");
        this.textTwo = set.getString("page_text2");
        this.textDetails = set.getString("page_text_details");
        this.textTeaser = set.getString("page_text_teaser");

        String includes = set.getString("includes");
        if (includes != null && !includes.isEmpty()) {
            for (String id : includes.split(";")) {
                try {
                    this.included.add(Integer.valueOf(id));
                } catch (Exception e) {
                    LOGGER.error("Caught exception", e);
                    LOGGER.error("Failed to parse includes column value of ({}) for catalog page ({})", id, this.id);
                }
            }
        }
    }

    public int getId() {
        return this.id;
    }

    public int getParentId() {
        return this.parentId;
    }

    public int getRank() {
        return this.rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public String getCaption() {
        return this.caption;
    }

    public String getPageName() {
        return this.pageName;
    }

    public int getIconColor() {
        return this.iconColor;
    }

    public int getIconImage() {
        return this.iconImage;
    }

    public int getOrderNum() {
        return this.orderNum;
    }

    public boolean isVisible() {
        return this.visible;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public boolean isClubOnly() {
        return this.clubOnly;
    }

    public CatalogPageType getCatalogPageType() {
        return this.catalogPageType;
    }

    public String getLayout() {
        return this.layout;
    }

    public String getHeaderImage() {
        return this.headerImage;
    }

    public String getTeaserImage() {
        return this.teaserImage;
    }

    public String getSpecialImage() {
        return this.specialImage;
    }

    public String getTextOne() {
        return this.textOne;
    }

    public String getTextTwo() {
        return this.textTwo;
    }

    public String getTextDetails() {
        return this.textDetails;
    }

    public String getTextTeaser() {
        return this.textTeaser;
    }

    public IntList getOfferIds() {
        return this.offerIds;
    }

    public void addOfferId(int offerId) {
        this.offerIds.add(offerId);
    }

    public void addItem(CatalogItem item) {
        this.catalogItems.put(item.getId(), item);
    }

    public Int2ObjectMap<CatalogItem> getCatalogItems() {
        return this.catalogItems;
    }

    public CatalogItem getCatalogItem(int id) {
        return this.catalogItems.get(id);
    }

    public ArrayList<Integer> getIncluded() {
        return this.included;
    }

    public Map<Integer, CatalogPage> getChildPages() {
        return this.childPages;
    }

    public void addChildPage(CatalogPage page) {
        this.childPages.put(page.getId(), page);

        if (page.getRank() < this.getRank()) {
            page.setRank(this.getRank());
        }
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public int compareTo(CatalogPage page) {
        return this.getOrderNum() - page.getOrderNum();
    }

    @Override
    public abstract void serialize(ServerMessage message);
}
