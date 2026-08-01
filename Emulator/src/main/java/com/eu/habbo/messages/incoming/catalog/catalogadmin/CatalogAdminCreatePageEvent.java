package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogPageLayouts;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public class CatalogAdminCreatePageEvent extends MessageHandler {

    private static final int MAX_CAPTION_LENGTH = 128;
    private static final int MAX_CAPTION_SAVE_LENGTH = 25;
    private static final int MAX_HEADLINE_LENGTH = 1024;
    private static final int MAX_TEASER_LENGTH = 64;
    private static final int MAX_TEXT_LENGTH = 8192;
    private static final int MAX_INCLUDES_LENGTH = 128;

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

        String caption = this.packet.readString();
        String caption2 = this.packet.readString();
        String layout = this.packet.readString();
        int iconType = this.packet.readInt();
        int minRank = this.packet.readInt();
        boolean visible = this.packet.readBoolean();
        boolean enabled = this.packet.readBoolean();
        int orderNum = this.packet.readInt();
        int parentId = this.packet.readInt();
        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());
        CatalogPageType catalogMode = CatalogPageType.fromString(this.packet.readString());
        int iconColor = this.packet.bytesAvailable() > 0 ? this.packet.readInt() : 1;
        boolean clubOnly = this.packet.bytesAvailable() > 0 && this.packet.readBoolean();
        boolean vipOnly = this.packet.bytesAvailable() > 0 && this.packet.readBoolean();
        String headline = this.packet.bytesAvailable() > 0 ? this.packet.readString() : "";
        String teaser = this.packet.bytesAvailable() > 0 ? this.packet.readString() : "";
        String special = this.packet.bytesAvailable() > 0 ? this.packet.readString() : "";
        String textOne = this.packet.bytesAvailable() > 0 ? this.packet.readString() : "";
        String textTwo = this.packet.bytesAvailable() > 0 ? this.packet.readString() : "";
        String textDetails = this.packet.bytesAvailable() > 0 ? this.packet.readString() : "";
        String textTeaser = this.packet.bytesAvailable() > 0 ? this.packet.readString() : "";
        int roomId = this.packet.bytesAvailable() > 0 ? this.packet.readInt() : 0;
        String includes = this.packet.bytesAvailable() > 0 ? this.packet.readString() : "";

        CatalogPageLayouts pageLayout;
        try {
            pageLayout = CatalogPageLayouts.valueOf(layout);
        } catch (IllegalArgumentException | NullPointerException e) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Invalid layout: " + layout));
            return;
        }

        CatalogManager catalogManager = Emulator.getGameEnvironment().getCatalogManager();
        if (parentId != -1 && catalogManager.getCatalogPage(parentId, pageType) == null) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Parent page not found: " + parentId));
            return;
        }

        if (iconType < 0) iconType = 0;
        if (iconColor < 0) iconColor = 0;
        if (minRank < 1) minRank = 1;
        if (orderNum < 0) orderNum = 0;
        if (roomId < 0) roomId = 0;

        caption = this.clampLength(caption, MAX_CAPTION_LENGTH).trim();
        if (caption.isEmpty()) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Page caption is required"));
            return;
        }
        caption2 = this.clampLength(caption2, MAX_CAPTION_SAVE_LENGTH);
        headline = this.clampLength(this.sanitizeHtml(headline), MAX_HEADLINE_LENGTH);
        teaser = this.clampLength(this.sanitizeHtml(teaser), MAX_TEASER_LENGTH);
        special = this.clampLength(special, 2048);
        textOne = this.clampLength(this.sanitizeHtml(textOne), MAX_TEXT_LENGTH);
        textTwo = this.clampLength(this.sanitizeHtml(textTwo), MAX_TEXT_LENGTH);
        textDetails = this.clampLength(this.sanitizeHtml(textDetails), MAX_TEXT_LENGTH);
        textTeaser = this.clampLength(this.sanitizeHtml(textTeaser), MAX_TEXT_LENGTH);
        includes = this.normalizeIncludes(includes);
        if (includes == null) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Invalid included page IDs"));
            return;
        }
        if (!this.includesExist(includes, pageType, catalogManager)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Included page not found"));
            return;
        }

        var page = catalogManager.createCatalogPage(
                caption, caption2, roomId, iconType, pageLayout, minRank, parentId, pageType, catalogMode,
                visible, enabled, orderNum, headline, teaser, special, textOne, textTwo, textDetails,
                textTeaser, iconColor, clubOnly, vipOnly, includes
        );

        if (page == null) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Failed to create page"));
            return;
        }

        this.client.sendResponse(new CatalogAdminResultComposer(true, "Page created: " + page.getId()));
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

    private String normalizeIncludes(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String clean = value.trim().replaceAll("\\s+", "");
        if (clean.length() > MAX_INCLUDES_LENGTH) return null;

        StringBuilder normalized = new StringBuilder();
        for (String entry : clean.split("[;,]")) {
            try {
                int includedPageId = Integer.parseInt(entry);
                if (includedPageId <= 0) return null;
                if (normalized.length() > 0) normalized.append(';');
                normalized.append(includedPageId);
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return normalized.toString();
    }

    private boolean includesExist(String includes, CatalogPageType pageType, CatalogManager catalogManager) {
        if (includes.isEmpty()) return true;
        for (String entry : includes.split(";")) {
            int includedPageId = Integer.parseInt(entry);
            if (catalogManager.getCatalogPage(includedPageId, pageType) == null) {
                return false;
            }
        }
        return true;
    }
}
