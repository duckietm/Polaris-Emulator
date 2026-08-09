package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.CatalogPageLayouts;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeOperation;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogEntityType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogLockKey;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogPageSnapshot;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogSmartSaveRequest;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioMutationEnvelope;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRequestParser;
import com.eu.habbo.messages.incoming.catalog.catalogadmin.studio.CatalogStudioRuntime;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;
import com.google.gson.Gson;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public class CatalogAdminSavePageEvent extends MessageHandler {

    private static final int MAX_CAPTION_LENGTH = 128;
    private static final int MAX_CAPTION_SAVE_LENGTH = 25;
    private static final int MAX_HEADLINE_LENGTH = 1024;
    private static final int MAX_TEASER_LENGTH = 64;
    private static final int MAX_TEXT_LENGTH = 8192;
    private static final int MAX_INCLUDES_LENGTH = 128;
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
        int iconColor = this.packet.bytesAvailable() > 0 ? this.packet.readInt() : 1;
        boolean clubOnly = this.packet.bytesAvailable() > 0 && this.packet.readBoolean();
        boolean vipOnly = this.packet.bytesAvailable() > 0 && this.packet.readBoolean();
        String special = this.packet.bytesAvailable() > 0 ? this.packet.readString() : "";
        String text2 = this.packet.bytesAvailable() > 0 ? this.packet.readString() : "";
        String textTeaser = this.packet.bytesAvailable() > 0 ? this.packet.readString() : "";
        int roomId = this.packet.bytesAvailable() > 0 ? this.packet.readInt() : 0;
        String includes = this.packet.bytesAvailable() > 0 ? this.packet.readString() : "";
        CatalogStudioMutationEnvelope envelope = CatalogStudioRequestParser.parseMutationEnvelope(this.packet);

        try {
            CatalogPageLayouts.valueOf(layout);
        } catch (IllegalArgumentException | NullPointerException e) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Invalid layout: " + layout));
            return;
        }

        if (iconType < 0) iconType = 0;
        if (iconColor < 0) iconColor = 0;
        if (minRank < 1) minRank = 1;
        if (roomId < 0) roomId = 0;

        headline = this.sanitizeHtml(headline);
        teaser = this.sanitizeHtml(teaser);
        textDetails = this.sanitizeHtml(textDetails);
        text1 = this.sanitizeHtml(text1);
        text2 = this.sanitizeHtml(text2);
        textTeaser = this.sanitizeHtml(textTeaser);

        caption = this.clampLength(caption, MAX_CAPTION_LENGTH);
        caption2 = this.clampLength(caption2, MAX_CAPTION_SAVE_LENGTH);
        headline = this.clampLength(headline, MAX_HEADLINE_LENGTH);
        teaser = this.clampLength(teaser, MAX_TEASER_LENGTH);
        textDetails = this.clampLength(textDetails, MAX_TEXT_LENGTH);
        text1 = this.clampLength(text1, MAX_TEXT_LENGTH);
        special = this.clampLength(special, 2048);
        text2 = this.clampLength(text2, MAX_TEXT_LENGTH);
        textTeaser = this.clampLength(textTeaser, MAX_TEXT_LENGTH);
        includes = this.normalizeIncludes(includes);
        if (includes == null) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Invalid included page IDs"));
            return;
        }
        CatalogPageSnapshot edited = new CatalogPageSnapshot(
                pageType,
                pageId,
                parentId,
                caption2,
                caption,
                layout,
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
                text1,
                text2,
                textDetails,
                textTeaser,
                roomId,
                includes);
        Gson gson = new Gson();
        String operationId = CatalogAdminSmartSaveResponder.operationId(envelope, "savePage");
        String validatedIncludes = includes;
        var smartSaves = CatalogStudioRuntime.services().smartSaves();
        try {
            var result = smartSaves.apply(
                    new CatalogSmartSaveRequest(
                            operationId,
                            envelope.draftVersionId(),
                            envelope.expectedRevision(),
                            this.client.getHabbo().getHabboInfo().getId(),
                            this.client.getHabbo().getHabboInfo().getUsername(),
                            new CatalogLockKey(CatalogEntityType.PAGE, pageType, pageId),
                            envelope.lockToken(),
                            envelope.summary(),
                            CatalogEntityType.PAGE,
                            pageId,
                            CatalogChangeOperation.UPDATE,
                            gson.toJson(edited)),
                    draft -> {
                        if (draft.page(pageType, pageId).isEmpty()) {
                            throw new IllegalArgumentException("Page not found in shared draft: " + pageId);
                        }
                        CatalogAdminPageDraftChecks.validateParentAndIncludes(
                                draft, pageType, pageId, parentId, validatedIncludes);
                    });
            this.client.sendResponse(CatalogAdminSmartSaveResponder.success(
                    "savePage",
                    "Page saved in shared draft at revision " + result.revision(),
                    result,
                    this.client.getHabbo().getHabboInfo().getUsername(),
                    gson));
        } catch (IllegalArgumentException
                | com.eu.habbo.habbohotel.catalog.versioning.CatalogConcurrentModificationException
                | com.eu.habbo.habbohotel.catalog.versioning.CatalogLockConflictException
                | com.eu.habbo.habbohotel.catalog.versioning.CatalogUndoConflictException exception) {
            this.client.sendResponse(CatalogAdminSmartSaveResponder.failure(
                    operationId,
                    "savePage",
                    envelope.draftVersionId(),
                    envelope.expectedRevision(),
                    "PAGE",
                    pageType.name(),
                    pageId,
                    exception,
                    gson));
        }
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
}
