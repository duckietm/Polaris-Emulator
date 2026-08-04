package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;

public record CatalogDraftPageData(
        int parentId,
        String captionSave,
        String caption,
        String pageLayout,
        int iconColor,
        int iconImage,
        int minRank,
        int orderNum,
        boolean visible,
        boolean enabled,
        boolean clubOnly,
        String catalogMode,
        boolean vipOnly,
        String pageHeadline,
        String pageTeaser,
        String pageSpecial,
        String pageText1,
        String pageText2,
        String pageTextDetails,
        String pageTextTeaser,
        int roomId,
        String includes) {

    CatalogPageSnapshot withId(int pageId) {
        return withId(CatalogPageType.NORMAL, pageId);
    }

    CatalogPageSnapshot withId(CatalogPageType catalogType, int pageId) {
        return new CatalogPageSnapshot(
                catalogType,
                pageId,
                parentId,
                captionSave,
                caption,
                pageLayout,
                iconColor,
                iconImage,
                minRank,
                orderNum,
                visible,
                enabled,
                clubOnly,
                catalogMode,
                vipOnly,
                pageHeadline,
                pageTeaser,
                pageSpecial,
                pageText1,
                pageText2,
                pageTextDetails,
                pageTextTeaser,
                roomId,
                includes);
    }
}
