package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.util.Objects;

public record CatalogPageSnapshot(
        CatalogPageType catalogType,
        int pageId,
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

    public CatalogPageSnapshot {
        catalogType = Objects.requireNonNull(catalogType, "catalogType");
        if (catalogType == CatalogPageType.BOTH) {
            throw new IllegalArgumentException("A physical catalog page cannot belong to BOTH catalogs");
        }
        if (pageId <= 0) throw new IllegalArgumentException("Page ID must be positive");
        if (minRank < 0) throw new IllegalArgumentException("Minimum rank cannot be negative");
    }

    public CatalogPageSnapshot(
            int pageId,
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
        this(
                CatalogPageType.NORMAL,
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
