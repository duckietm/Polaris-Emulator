package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class CatalogChangeSetSupport {
    private CatalogChangeSetSupport() {}

    static List<CatalogChangeEntry> diff(CatalogVersionSnapshot current, CatalogVersionSnapshot target, Gson gson) {
        List<CatalogChangeEntry> entries = new ArrayList<>();
        for (CatalogPageType type : List.of(CatalogPageType.NORMAL, CatalogPageType.BUILDER)) {
            Set<Integer> pageIds = new HashSet<>();
            current.pages().stream()
                    .filter(page -> page.catalogType() == type)
                    .forEach(page -> pageIds.add(page.pageId()));
            target.pages().stream()
                    .filter(page -> page.catalogType() == type)
                    .forEach(page -> pageIds.add(page.pageId()));
            pageIds.stream()
                    .sorted()
                    .forEach(id -> add(
                            entries,
                            CatalogEntityType.PAGE,
                            type,
                            id,
                            current.page(type, id).orElse(null),
                            target.page(type, id).orElse(null),
                            gson));

            Set<Integer> offerIds = new HashSet<>();
            current.offers().stream()
                    .filter(offer -> offer.catalogType() == type)
                    .forEach(offer -> offerIds.add(offer.offerId()));
            target.offers().stream()
                    .filter(offer -> offer.catalogType() == type)
                    .forEach(offer -> offerIds.add(offer.offerId()));
            offerIds.stream()
                    .sorted()
                    .forEach(id -> add(
                            entries,
                            CatalogEntityType.OFFER,
                            type,
                            id,
                            current.offer(type, id).orElse(null),
                            target.offer(type, id).orElse(null),
                            gson));
        }
        entries.sort(Comparator.comparing(CatalogChangeEntry::catalogType)
                .thenComparing(CatalogChangeEntry::entityType)
                .thenComparingInt(CatalogChangeEntry::entityId));
        return List.copyOf(entries);
    }

    static String fingerprint(long versionId, long revision, List<CatalogChangeEntry> entries, Gson gson) {
        try {
            String canonical = versionId + ":" + revision + ":" + gson.toJson(entries);
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void add(
            List<CatalogChangeEntry> entries,
            CatalogEntityType entityType,
            CatalogPageType catalogType,
            int entityId,
            Object before,
            Object after,
            Gson gson) {
        String beforeJson = before == null ? null : gson.toJson(before);
        String afterJson = after == null ? null : gson.toJson(after);
        if (Objects.equals(beforeJson, afterJson)) return;
        CatalogChangeOperation operation = before == null
                ? CatalogChangeOperation.CREATE
                : after == null ? CatalogChangeOperation.DELETE : CatalogChangeOperation.UPDATE;
        entries.add(new CatalogChangeEntry(0, entityType, catalogType, entityId, operation, beforeJson, afterJson));
    }
}
