package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPageLayouts;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class CatalogAdminSavePageEvent extends MessageHandler {

    private static final int MAX_CAPTION_LENGTH = 128;
    private static final int MAX_CAPTION_SAVE_LENGTH = 25;
    private static final int MAX_HEADLINE_LENGTH = 1024;
    private static final int MAX_TEASER_LENGTH = 64;
    private static final int MAX_TEXT_LENGTH = 8192;
    private static final int MAX_PARENT_WALK = 64;
    private static final int ROOT_PARENT_ID = -1;

    private static final Safelist PAGE_HTML_SAFELIST = new Safelist()
            .addTags("b", "i", "u", "br", "span", "div", "p", "a", "strong", "em", "img")
            .addAttributes("a", "href", "target", "class", "style")
            .addAttributes("img", "src", "alt", "class", "style")
            .addAttributes(":all", "class", "style")
            .addProtocols("a", "href", "http", "https", "mailto", "#")
            .addProtocols("img", "src", "http", "https", "data");

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
            return;
        }

        int pageId = this.packet.readInt();
        String caption = this.packet.readString();
        String caption2 = this.packet.readString();
        String layout = this.packet.readString();
        int iconType = this.packet.readInt();
        int minRank = this.packet.readInt();
        boolean visible = this.packet.readBoolean();
        boolean enabled = this.packet.readBoolean();
        int orderNum = this.packet.readInt();
        int parentId = this.packet.readInt();
        String headline = this.packet.readString();
        String teaser = this.packet.readString();
        String textDetails = this.packet.readString();
        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());
        CatalogPageType catalogMode = CatalogPageType.fromString(this.packet.readString());
        String text1 = this.packet.bytesAvailable() > 0 ? this.packet.readString() : "";
        CatalogPage page = Emulator.getGameEnvironment().getCatalogManager().getCatalogPage(pageId, pageType);

        if (page == null) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Page not found: " + pageId));
            return;
        }

        try {
            CatalogPageLayouts.valueOf(layout);
        } catch (IllegalArgumentException | NullPointerException e) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Invalid layout: " + layout));
            return;
        }

        if (parentId != ROOT_PARENT_ID) {
            if (parentId == pageId) {
                this.client.sendResponse(new CatalogAdminResultComposer(false, "A page cannot be its own parent"));
                return;
            }

            CatalogPage parent = Emulator.getGameEnvironment().getCatalogManager().getCatalogPage(parentId, pageType);
            if (parent == null) {
                this.client.sendResponse(new CatalogAdminResultComposer(false, "Parent page not found: " + parentId));
                return;
            }

            if (this.wouldCreateCycle(pageId, parentId, pageType)) {
                this.client.sendResponse(new CatalogAdminResultComposer(false, "Refusing to re-parent: that would create a cycle"));
                return;
            }
        }

        if (iconType < 0) iconType = 0;
        if (minRank < 1) minRank = 1;
        if (orderNum < 0) orderNum = 0;
		
        headline = this.sanitizeHtml(headline);
        teaser = this.sanitizeHtml(teaser);
        textDetails = this.sanitizeHtml(textDetails);
        text1 = this.sanitizeHtml(text1);

        caption = this.clampLength(caption, MAX_CAPTION_LENGTH);
        caption2 = this.clampLength(caption2, MAX_CAPTION_SAVE_LENGTH);
        headline = this.clampLength(headline, MAX_HEADLINE_LENGTH);
        teaser = this.clampLength(teaser, MAX_TEASER_LENGTH);
        textDetails = this.clampLength(textDetails, MAX_TEXT_LENGTH);
        text1 = this.clampLength(text1, MAX_TEXT_LENGTH);

        if (headline.isEmpty() && page.getHeaderImage() != null) headline = page.getHeaderImage();
        if (teaser.isEmpty() && page.getTeaserImage() != null) teaser = page.getTeaserImage();
        if (textDetails.isEmpty() && page.getTextDetails() != null) textDetails = page.getTextDetails();
        if (text1.isEmpty() && page.getTextOne() != null) text1 = page.getTextOne();

        String query = (pageType == CatalogPageType.BUILDER)
                ? "UPDATE catalog_pages_bc SET caption = ?, page_layout = ?, icon_image = ?, visible = ?, enabled = ?, order_num = ?, parent_id = ?, page_headline = ?, page_teaser = ?, page_text_details = ?, page_text1 = ? WHERE id = ?"
                : "UPDATE catalog_pages SET caption = ?, caption_save = ?, page_layout = ?, icon_image = ?, min_rank = ?, visible = ?, enabled = ?, order_num = ?, parent_id = ?, page_headline = ?, page_teaser = ?, page_text_details = ?, page_text1 = ?, catalog_mode = ? WHERE id = ?";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, caption);

            if (pageType == CatalogPageType.BUILDER) {
                statement.setString(2, layout);
                statement.setInt(3, iconType);
                statement.setString(4, visible ? "1" : "0");
                statement.setString(5, enabled ? "1" : "0");
                statement.setInt(6, orderNum);
                statement.setInt(7, parentId);
                statement.setString(8, headline);
                statement.setString(9, teaser);
                statement.setString(10, textDetails);
                statement.setString(11, text1);
                statement.setInt(12, pageId);
            } else {
                statement.setString(2, caption2);
                statement.setString(3, layout);
                statement.setInt(4, iconType);
                statement.setInt(5, minRank);
                statement.setString(6, visible ? "1" : "0");
                statement.setString(7, enabled ? "1" : "0");
                statement.setInt(8, orderNum);
                statement.setInt(9, parentId);
                statement.setString(10, headline);
                statement.setString(11, teaser);
                statement.setString(12, textDetails);
                statement.setString(13, text1);
                statement.setString(14, catalogMode.name());
                statement.setInt(15, pageId);
            }

            if (statement.executeUpdate() == 0) {
                this.client.sendResponse(new CatalogAdminResultComposer(false, "Page not found: " + pageId));
                return;
            }
        }

        this.client.sendResponse(new CatalogAdminResultComposer(true, "Page saved"));
    }

    private boolean wouldCreateCycle(int pageId, int parentId, CatalogPageType pageType) {
        int current = parentId;
        for (int hops = 0; hops < MAX_PARENT_WALK; hops++) {
            if (current == ROOT_PARENT_ID) return false;
            if (current == pageId) return true;
            CatalogPage parent = Emulator.getGameEnvironment().getCatalogManager().getCatalogPage(current, pageType);
            if (parent == null) return false;
            current = parent.getParentId();
        }
        return true;
    }

    private String clampLength(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        return value.substring(0, max);
    }


    private String sanitizeHtml(String value) {
        if (value == null || value.isEmpty()) return "";
        return Jsoup.clean(value, PAGE_HTML_SAFELIST);
    }
}
