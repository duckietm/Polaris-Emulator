package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.CatalogPageLayouts;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeOperation;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogDraftMutationRequest;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogDraftPageData;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogEntityType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogLockKey;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioMutationEnvelope;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRequestParser;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRuntime;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;
import com.google.gson.Gson;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public class CatalogAdminCreatePageEvent extends MessageHandler {

    private static final int MAX_CAPTION_LENGTH = 128;
    private static final int MAX_CAPTION_SAVE_LENGTH = 25;
    private static final int MAX_HEADLINE_LENGTH = 1024;
    private static final int MAX_TEASER_LENGTH = 64;
    private static final int MAX_TEXT_LENGTH = 8192;
    private static final int MAX_INCLUDES_LENGTH = 128;
    private static final int ROOT_PARENT_ID = -1;
    private static final int CATALOG_ROOT_LOCK_ID = Integer.MAX_VALUE;

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

        if (iconType < 0) iconType = 0;
        if (iconColor < 0) iconColor = 0;
        if (minRank < 1) minRank = 1;
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
        CatalogStudioMutationEnvelope envelope = CatalogStudioRequestParser.parseMutationEnvelope(this.packet);
        if (parentId == 0 || parentId < ROOT_PARENT_ID) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Invalid parent page id"));
            return;
        }
        var mutations = CatalogStudioRuntime.services().mutations();
        var draft = mutations.loadDraft(envelope.draftVersionId(), envelope.expectedRevision());
        if (parentId != ROOT_PARENT_ID && draft.page(pageType, parentId).isEmpty()) {
            this.client.sendResponse(
                    new CatalogAdminResultComposer(false, "Parent page not found in shared draft: " + parentId));
            return;
        }
        if (!this.includesExist(includes, pageType, draft)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Included page not found in shared draft"));
            return;
        }

        CatalogDraftPageData pageData = new CatalogDraftPageData(
                parentId,
                caption2,
                caption,
                pageLayout.name(),
                iconColor,
                iconType,
                minRank,
                orderNum,
                visible,
                enabled,
                clubOnly,
                catalogMode.name(),
                vipOnly,
                headline,
                teaser,
                special,
                textOne,
                textTwo,
                textDetails,
                textTeaser,
                roomId,
                includes);
        var result = mutations.apply(new CatalogDraftMutationRequest(
                envelope.draftVersionId(),
                envelope.expectedRevision(),
                this.client.getHabbo().getHabboInfo().getId(),
                new CatalogLockKey(
                        CatalogEntityType.PAGE, pageType, parentId == ROOT_PARENT_ID ? CATALOG_ROOT_LOCK_ID : parentId),
                envelope.lockToken(),
                envelope.summary(),
                CatalogEntityType.PAGE,
                0,
                CatalogChangeOperation.CREATE,
                new Gson().toJson(pageData)));
        this.client.sendResponse(new CatalogAdminResultComposer(
                true, "Page created in shared draft: " + result.entityId() + " at revision " + result.revision()));
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

    private boolean includesExist(
            String includes,
            CatalogPageType pageType,
            com.eu.habbo.habbohotel.catalog.versioning.CatalogVersionSnapshot draft) {
        if (includes.isEmpty()) return true;
        for (String entry : includes.split(";")) {
            int includedPageId = Integer.parseInt(entry);
            if (draft.page(pageType, includedPageId).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
