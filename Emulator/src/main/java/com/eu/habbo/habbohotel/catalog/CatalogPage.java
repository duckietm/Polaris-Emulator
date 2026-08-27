package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.messages.ISerialize;
import com.eu.habbo.messages.ServerMessage;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    protected boolean vipOnly;
    protected int roomId;
    protected CatalogPageType catalogPageType = CatalogPageType.NORMAL;
    protected String layout;
    protected String headerImage;
    protected String teaserImage;
    protected String specialImage;
    protected String textOne;
    protected String textTwo;
    protected String textDetails;
    protected String textTeaser;

    public CatalogPage() {}

    public CatalogPage(ResultSet set) throws SQLException {
        if (set == null) return;

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
            this.vipOnly = set.getBoolean("vip_only");
        } catch (SQLException ignored) {
            this.vipOnly = false;
        }
        try {
            this.roomId = set.getInt("room_id");
        } catch (SQLException ignored) {
            this.roomId = 0;
        }
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

    public void setParentId(int parentId) {
        this.parentId = parentId;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public void setPageName(String pageName) {
        this.pageName = pageName;
    }

    public void setIconImage(int iconImage) {
        this.iconImage = iconImage;
    }

    public void setIconColor(int iconColor) {
        this.iconColor = iconColor;
    }

    public void setOrderNum(int orderNum) {
        this.orderNum = orderNum;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setClubOnly(boolean clubOnly) {
        this.clubOnly = clubOnly;
    }

    public void setVipOnly(boolean vipOnly) {
        this.vipOnly = vipOnly;
    }

    public void setLayout(String layout) {
        this.layout = layout;
    }

    public void setCatalogPageType(CatalogPageType catalogPageType) {
        this.catalogPageType = catalogPageType;
    }

    public void setHeaderImage(String headerImage) {
        this.headerImage = headerImage;
    }

    public void setTeaserImage(String teaserImage) {
        this.teaserImage = teaserImage;
    }

    public void setSpecialImage(String specialImage) {
        this.specialImage = specialImage;
    }

    public void setTextOne(String textOne) {
        this.textOne = textOne;
    }

    public void setTextTwo(String textTwo) {
        this.textTwo = textTwo;
    }

    public void setTextDetails(String textDetails) {
        this.textDetails = textDetails;
    }

    public void setTextTeaser(String textTeaser) {
        this.textTeaser = textTeaser;
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }

    public void setIncluded(String includes) {
        this.included.clear();
        if (includes == null || includes.isBlank()) return;
        for (String id : includes.split(";")) this.included.add(Integer.parseInt(id));
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

    public boolean isVipOnly() {
        return this.vipOnly;
    }

    public int getRoomId() {
        return this.roomId;
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
        // Pages sharing an order slot used to fall back to the iteration order of the child HashMap,
        // which is not the order they were added in and shifts as pages come and go. The stock
        // database ships eight pet pages at order 99 under one parent, so this was visible out of
        // the box: the same catalog, a different sequence after a restart. The id keeps them steady.
        int byOrder = Integer.compare(this.getOrderNum(), page.getOrderNum());

        return byOrder != 0 ? byOrder : Integer.compare(this.getId(), page.getId());
    }

    @Override
    public abstract void serialize(ServerMessage message);
}
