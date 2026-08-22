package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CatalogSnapshotThreeWayMerge {
    private final Gson gson;

    public CatalogSnapshotThreeWayMerge(Gson gson) {
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    public CatalogSnapshotMergeResult merge(
            CatalogVersionSnapshot base, CatalogVersionSnapshot live, CatalogVersionSnapshot draft) {
        List<CatalogMergeConflict> conflicts = new ArrayList<>();
        List<CatalogPageSnapshot> pages = mergeEntities(
                CatalogEntityType.PAGE,
                indexPages(base.pages()),
                indexPages(live.pages()),
                indexPages(draft.pages()),
                CatalogPageSnapshot.class,
                conflicts);
        List<CatalogOfferSnapshot> offers = mergeEntities(
                CatalogEntityType.OFFER,
                indexOffers(base.offers()),
                indexOffers(live.offers()),
                indexOffers(draft.offers()),
                CatalogOfferSnapshot.class,
                conflicts);
        CatalogVersionSnapshot merged = new CatalogVersionSnapshot(draft.version(), pages, offers);
        return new CatalogSnapshotMergeResult(merged, conflicts, CatalogChangeSetSupport.diff(draft, merged, gson));
    }

    private <T> List<T> mergeEntities(
            CatalogEntityType entityType,
            Map<EntityKey, T> base,
            Map<EntityKey, T> live,
            Map<EntityKey, T> draft,
            Class<T> type,
            List<CatalogMergeConflict> conflicts) {
        Set<EntityKey> keys = new LinkedHashSet<>();
        keys.addAll(base.keySet());
        keys.addAll(live.keySet());
        keys.addAll(draft.keySet());
        List<T> merged = new ArrayList<>();
        for (EntityKey key : keys) {
            T baseValue = base.get(key);
            T liveValue = live.get(key);
            T draftValue = draft.get(key);
            T value;
            if (baseValue != null && liveValue != null && draftValue != null) {
                value = mergeFields(entityType, key, baseValue, liveValue, draftValue, type, conflicts);
            } else if (Objects.equals(liveValue, baseValue)) value = draftValue;
            else if (Objects.equals(draftValue, baseValue)) value = liveValue;
            else if (Objects.equals(liveValue, draftValue)) value = liveValue;
            else {
                conflicts.add(new CatalogMergeConflict(entityType, key.catalogType(), key.entityId(), "$entity"));
                value = draftValue;
            }
            if (value != null) merged.add(value);
        }
        return List.copyOf(merged);
    }

    private <T> T mergeFields(
            CatalogEntityType entityType,
            EntityKey key,
            T base,
            T live,
            T draft,
            Class<T> type,
            List<CatalogMergeConflict> conflicts) {
        JsonObject baseJson = gson.toJsonTree(base).getAsJsonObject();
        JsonObject liveJson = gson.toJsonTree(live).getAsJsonObject();
        JsonObject draftJson = gson.toJsonTree(draft).getAsJsonObject();
        JsonObject mergedJson = draftJson.deepCopy();
        Set<String> fields = new LinkedHashSet<>();
        fields.addAll(baseJson.keySet());
        fields.addAll(liveJson.keySet());
        fields.addAll(draftJson.keySet());
        for (String field : fields) {
            JsonElement baseValue = baseJson.get(field);
            JsonElement liveValue = liveJson.get(field);
            JsonElement draftValue = draftJson.get(field);
            if (Objects.equals(liveValue, baseValue)) mergedJson.add(field, draftValue);
            else if (Objects.equals(draftValue, baseValue) || Objects.equals(liveValue, draftValue)) {
                mergedJson.add(field, liveValue);
            } else {
                conflicts.add(new CatalogMergeConflict(entityType, key.catalogType(), key.entityId(), field));
            }
        }
        return gson.fromJson(mergedJson, type);
    }

    private static Map<EntityKey, CatalogPageSnapshot> indexPages(List<CatalogPageSnapshot> pages) {
        Map<EntityKey, CatalogPageSnapshot> result = new LinkedHashMap<>();
        for (CatalogPageSnapshot page : pages) {
            result.put(new EntityKey(page.catalogType(), page.pageId()), page);
        }
        return result;
    }

    private static Map<EntityKey, CatalogOfferSnapshot> indexOffers(List<CatalogOfferSnapshot> offers) {
        Map<EntityKey, CatalogOfferSnapshot> result = new LinkedHashMap<>();
        for (CatalogOfferSnapshot offer : offers) {
            result.put(new EntityKey(offer.catalogType(), offer.offerId()), offer);
        }
        return result;
    }

    private record EntityKey(CatalogPageType catalogType, int entityId) {}
}
