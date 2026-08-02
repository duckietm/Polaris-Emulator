package com.eu.habbo.habbohotel.catalog.versioning;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public final class CatalogBulkOperationService {
    private final Gson gson;

    public CatalogBulkOperationService(Gson gson) {
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    public CatalogBulkDryRun dryRun(CatalogVersionSnapshot draft, CatalogBulkRequest request) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(request, "request");
        if (draft.version().status() != CatalogVersionStatus.DRAFT) {
            throw new IllegalArgumentException("Bulk operations require a draft snapshot");
        }
        List<CatalogChangeEntry> changes =
                switch (request.operation()) {
                    case PRICE_PERCENT, REPLACE_CURRENCY -> offerChanges(draft, request);
                    case SET_VISIBILITY, SET_MIN_RANK, REPARENT -> pageChanges(draft, request);
                };
        if (changes.isEmpty()) throw new IllegalArgumentException("Bulk operation produced no changes");
        String fingerprint = fingerprint(draft.version().id(), draft.version().revision(), changes);
        return new CatalogBulkDryRun(draft.version().id(), draft.version().revision(), changes, fingerprint);
    }

    public List<CatalogChangeEntry> confirm(
            CatalogVersionSnapshot current, CatalogBulkRequest request, String fingerprint) {
        CatalogBulkDryRun fresh = dryRun(current, request);
        if (!MessageDigest.isEqual(
                fresh.fingerprint().getBytes(StandardCharsets.US_ASCII),
                Objects.requireNonNull(fingerprint, "fingerprint").getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Bulk dry-run fingerprint is stale or invalid");
        }
        return fresh.changes();
    }

    private List<CatalogChangeEntry> pageChanges(CatalogVersionSnapshot draft, CatalogBulkRequest request) {
        List<CatalogChangeEntry> changes = new ArrayList<>();
        for (int id : request.entityIds()) {
            CatalogPageSnapshot before = draft.page(request.catalogType(), id)
                    .orElseThrow(() -> new IllegalArgumentException("Catalog page not found: " + id));
            CatalogPageSnapshot after =
                    switch (request.operation()) {
                        case SET_VISIBILITY -> CatalogSnapshotPatch.setPageVisible(before, request.booleanValue());
                        case SET_MIN_RANK -> copyPage(before, before.parentId(), request.intValue());
                        case REPARENT -> copyPage(before, request.intValue(), before.minRank());
                        default -> throw new IllegalArgumentException("Operation does not target pages");
                    };
            add(changes, CatalogEntityType.PAGE, before.catalogType(), id, before, after);
        }
        return List.copyOf(changes);
    }

    private List<CatalogChangeEntry> offerChanges(CatalogVersionSnapshot draft, CatalogBulkRequest request) {
        List<CatalogChangeEntry> changes = new ArrayList<>();
        for (int id : request.entityIds()) {
            CatalogOfferSnapshot before = draft.offer(request.catalogType(), id)
                    .orElseThrow(() -> new IllegalArgumentException("Catalog offer not found: " + id));
            CatalogOfferSnapshot after =
                    switch (request.operation()) {
                        case PRICE_PERCENT ->
                            copyOffer(
                                    before,
                                    Math.max(
                                            0,
                                            Math.toIntExact(Math.round(
                                                    before.costCredits() * (100L + request.intValue()) / 100.0))),
                                    before.pointsType());
                        case REPLACE_CURRENCY -> copyOffer(before, before.costCredits(), request.intValue());
                        default -> throw new IllegalArgumentException("Operation does not target offers");
                    };
            add(changes, CatalogEntityType.OFFER, before.catalogType(), id, before, after);
        }
        return List.copyOf(changes);
    }

    private void add(
            List<CatalogChangeEntry> changes,
            CatalogEntityType entityType,
            com.eu.habbo.habbohotel.catalog.CatalogPageType catalogType,
            int entityId,
            Object before,
            Object after) {
        String beforeJson = gson.toJson(before);
        String afterJson = gson.toJson(after);
        if (!beforeJson.equals(afterJson)) {
            changes.add(new CatalogChangeEntry(
                    0, entityType, catalogType, entityId, CatalogChangeOperation.UPDATE, beforeJson, afterJson));
        }
    }

    private static CatalogPageSnapshot copyPage(CatalogPageSnapshot page, int parentId, int minRank) {
        return new CatalogPageSnapshot(
                page.catalogType(),
                page.pageId(),
                parentId,
                page.captionSave(),
                page.caption(),
                page.pageLayout(),
                page.iconColor(),
                page.iconImage(),
                minRank,
                page.orderNum(),
                page.visible(),
                page.enabled(),
                page.clubOnly(),
                page.catalogMode(),
                page.vipOnly(),
                page.pageHeadline(),
                page.pageTeaser(),
                page.pageSpecial(),
                page.pageText1(),
                page.pageText2(),
                page.pageTextDetails(),
                page.pageTextTeaser(),
                page.roomId(),
                page.includes());
    }

    private static CatalogOfferSnapshot copyOffer(CatalogOfferSnapshot offer, int costCredits, int pointsType) {
        return new CatalogOfferSnapshot(
                offer.catalogType(),
                offer.offerId(),
                offer.itemIds(),
                offer.pageId(),
                offer.catalogName(),
                costCredits,
                offer.costPoints(),
                pointsType,
                offer.amount(),
                offer.limitedStack(),
                offer.orderNumber(),
                offer.offerIdClient(),
                offer.songId(),
                offer.extradata(),
                offer.haveOffer(),
                offer.clubOnly());
    }

    private String fingerprint(long versionId, long revision, List<CatalogChangeEntry> changes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonical = versionId + ":" + revision + ":" + gson.toJson(changes);
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
