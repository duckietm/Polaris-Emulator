package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CatalogValidator {
    private final Set<Integer> itemDefinitionIds;
    private final Set<Integer> currencyTypes;
    private final Map<Integer, Integer> liveLimitedSells;
    private final Set<String> supportedLayouts;

    public CatalogValidator(
            Set<Integer> itemDefinitionIds,
            Set<Integer> currencyTypes,
            Map<Integer, Integer> liveLimitedSells,
            Set<String> supportedLayouts) {
        this.itemDefinitionIds = Set.copyOf(itemDefinitionIds);
        this.currencyTypes = Set.copyOf(currencyTypes);
        this.liveLimitedSells = Map.copyOf(liveLimitedSells);
        this.supportedLayouts = Set.copyOf(supportedLayouts);
    }

    public CatalogValidationReport validate(CatalogVersionSnapshot snapshot) {
        return validate(snapshot, null);
    }

    public CatalogValidationReport validateChanges(CatalogVersionSnapshot baseline, CatalogVersionSnapshot candidate) {
        return validateChanges(baseline, candidate, null);
    }

    /**
     * Reports the problems the candidate introduces, looking only at the entities the changes can
     * reach.
     *
     * <p>Validating a whole live catalog twice per save is the dominant cost of an edit: on a
     * 112,000-offer catalog the two passes take roughly 600 ms, while the edit itself touches one
     * row. Passing the changes narrows both passes to the entities that can actually change verdict:
     * the edited ones, and the ones whose rules read them - descendants, siblings, pages that
     * include them, and the offers sitting on them. Ancestors are not in scope: an edit cannot
     * change their verdict, and the rules that walk upwards read the snapshot directly.
     *
     * <p>A null {@code changes} validates everything, which is what a full catalog check does.
     */
    public CatalogValidationReport validateChanges(
            CatalogVersionSnapshot baseline, CatalogVersionSnapshot candidate, Collection<CatalogChangeEntry> changes) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(candidate, "candidate");
        Scope scope = changes == null ? null : scopeOf(changes, baseline, candidate);
        Set<CatalogValidationIssue> inherited =
                new HashSet<>(validate(baseline, scope).issues());
        List<CatalogValidationIssue> introduced = validate(candidate, scope).issues().stream()
                .filter(issue -> !inherited.contains(issue))
                .toList();
        return new CatalogValidationReport(introduced);
    }

    private CatalogValidationReport validate(CatalogVersionSnapshot snapshot, Scope scope) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<CatalogValidationIssue> issues = new ArrayList<>();
        validatePages(snapshot, issues, scope);
        validateOffers(snapshot, issues, scope);
        return new CatalogValidationReport(issues);
    }

    private record EntityKey(CatalogPageType catalogType, int entityId) {}

    /** The entities whose verdict the changes can alter. A null scope means the whole catalog. */
    private record Scope(Set<EntityKey> pages, Set<EntityKey> offers) {}

    private Scope scopeOf(
            Collection<CatalogChangeEntry> changes, CatalogVersionSnapshot baseline, CatalogVersionSnapshot candidate) {
        Set<EntityKey> pages = new HashSet<>();
        Set<EntityKey> offers = new HashSet<>();
        for (CatalogChangeEntry change : changes) {
            EntityKey key = new EntityKey(change.catalogType(), change.entityId());
            if (change.entityType() == CatalogEntityType.PAGE) {
                pages.add(key);
            } else {
                offers.add(key);
            }
        }
        // An entity can be present in one snapshot and gone from the other, so both are walked from
        // the same seeds: expanding from an already grown set would pull the tree in sideways.
        Set<EntityKey> seeds = Set.copyOf(pages);
        expand(baseline, seeds, pages);
        expand(candidate, seeds, pages);

        // An offer only reads whether its page exists - not whether it is visible - so editing a
        // page leaves every offer's verdict untouched. Only pages that appear or disappear pull
        // their offers in, which is what turns a subtree edit from ~100,000 offers into none.
        Set<EntityKey> appearing = new HashSet<>();
        for (EntityKey key : pages) {
            boolean inBaseline =
                    baseline.page(key.catalogType(), key.entityId()).isPresent();
            boolean inCandidate =
                    candidate.page(key.catalogType(), key.entityId()).isPresent();
            if (inBaseline != inCandidate) appearing.add(key);
        }
        if (!appearing.isEmpty()) {
            collectOffers(baseline, appearing, offers);
            collectOffers(candidate, appearing, offers);
        }
        return new Scope(Set.copyOf(pages), Set.copyOf(offers));
    }

    private void expand(CatalogVersionSnapshot snapshot, Set<EntityKey> seeds, Set<EntityKey> pages) {
        Map<EntityKey, List<CatalogPageSnapshot>> children = new HashMap<>();
        for (CatalogPageSnapshot page : snapshot.pages()) {
            children.computeIfAbsent(new EntityKey(page.catalogType(), page.parentId()), ignored -> new ArrayList<>())
                    .add(page);
        }

        // Only an edited page can take or free an order slot, so siblings are collected for the
        // seeds alone. Doing it for every page reached would re-scan one parent's children once per
        // child, which is quadratic on a wide branch.
        for (EntityKey seed : seeds) {
            CatalogPageSnapshot page =
                    snapshot.page(seed.catalogType(), seed.entityId()).orElse(null);
            if (page == null) continue;
            EntityKey parent = new EntityKey(page.catalogType(), page.parentId());
            for (CatalogPageSnapshot sibling : children.getOrDefault(parent, List.of())) {
                pages.add(new EntityKey(sibling.catalogType(), sibling.pageId()));
            }
        }

        // Descendants read the availability of everything above them, so they follow an edit down.
        Deque<EntityKey> pending = new ArrayDeque<>(seeds);
        while (!pending.isEmpty()) {
            EntityKey key = pending.poll();
            for (CatalogPageSnapshot child : children.getOrDefault(key, List.of())) {
                EntityKey childKey = new EntityKey(child.catalogType(), child.pageId());
                if (pages.add(childKey)) pending.add(childKey);
            }
        }

        Set<Integer> pageIds = new HashSet<>();
        for (EntityKey key : pages) pageIds.add(key.entityId());
        for (CatalogPageSnapshot page : snapshot.pages()) {
            if (includesAnyOf(page, pageIds)) {
                pages.add(new EntityKey(page.catalogType(), page.pageId()));
            }
        }
    }

    private static void collectOffers(CatalogVersionSnapshot snapshot, Set<EntityKey> pages, Set<EntityKey> offers) {
        for (CatalogOfferSnapshot offer : snapshot.offers()) {
            if (pages.contains(new EntityKey(offer.catalogType(), offer.pageId()))) {
                offers.add(new EntityKey(offer.catalogType(), offer.offerId()));
            }
        }
    }

    private static boolean includesAnyOf(CatalogPageSnapshot page, Set<Integer> pageIds) {
        if (page.includes() == null || page.includes().isBlank()) return false;
        for (String rawId : page.includes().split(";", -1)) {
            try {
                if (pageIds.contains(Integer.parseInt(rawId.trim()))) return true;
            } catch (NumberFormatException ignored) {
                // An unparsable include is reported by validateIncludes, not here.
            }
        }
        return false;
    }

    private static List<CatalogPageSnapshot> pagesInScope(CatalogVersionSnapshot snapshot, Scope scope) {
        if (scope == null) return snapshot.pages();
        List<CatalogPageSnapshot> selected = new ArrayList<>(scope.pages().size());
        for (EntityKey key : scope.pages()) {
            snapshot.page(key.catalogType(), key.entityId()).ifPresent(selected::add);
        }
        return selected;
    }

    private static List<CatalogOfferSnapshot> offersInScope(CatalogVersionSnapshot snapshot, Scope scope) {
        if (scope == null) return snapshot.offers();
        List<CatalogOfferSnapshot> selected = new ArrayList<>(scope.offers().size());
        for (EntityKey key : scope.offers()) {
            snapshot.offer(key.catalogType(), key.entityId()).ifPresent(selected::add);
        }
        return selected;
    }

    private void validatePages(CatalogVersionSnapshot snapshot, List<CatalogValidationIssue> issues, Scope scope) {
        Map<ParentOrder, List<Integer>> siblingOrders = new HashMap<>();
        for (CatalogPageSnapshot page : pagesInScope(snapshot, scope)) {
            if (page.parentId() > 0
                    && snapshot.page(page.catalogType(), page.parentId()).isEmpty()) {
                add(
                        issues,
                        "PAGE_PARENT_MISSING",
                        CatalogEntityType.PAGE,
                        page.pageId(),
                        "parentId",
                        "Parent page " + page.parentId() + " does not exist");
            }
            if (!supportedLayouts.contains(page.pageLayout())) {
                add(
                        issues,
                        "PAGE_LAYOUT_UNSUPPORTED",
                        CatalogEntityType.PAGE,
                        page.pageId(),
                        "pageLayout",
                        "Unsupported page layout: " + page.pageLayout());
            }
            siblingOrders
                    .computeIfAbsent(
                            new ParentOrder(page.catalogType().name(), page.parentId(), page.orderNum()),
                            ignored -> new ArrayList<>())
                    .add(page.pageId());
            validateIncludes(snapshot, page, issues);
            validateAncestors(snapshot, page, issues);
        }

        siblingOrders.forEach((key, pageIds) -> {
            if (pageIds.size() > 1) {
                for (int pageId : pageIds) {
                    add(
                            issues,
                            "PAGE_SIBLING_ORDER_DUPLICATE",
                            CatalogEntityType.PAGE,
                            pageId,
                            "orderNum",
                            "Sibling order " + key.orderNum() + " is used more than once");
                }
            }
        });

        detectCycles(snapshot, issues, scope);
    }

    private void validateIncludes(
            CatalogVersionSnapshot snapshot, CatalogPageSnapshot page, List<CatalogValidationIssue> issues) {
        if (page.includes() == null || page.includes().isBlank()) return;
        for (String rawId : page.includes().split(";", -1)) {
            try {
                int includedId = Integer.parseInt(rawId.trim());
                if (includedId <= 0) throw new NumberFormatException();
                if (snapshot.page(page.catalogType(), includedId).isEmpty()) {
                    add(
                            issues,
                            "PAGE_INCLUDE_MISSING",
                            CatalogEntityType.PAGE,
                            page.pageId(),
                            "includes",
                            "Included page " + includedId + " does not exist");
                }
            } catch (NumberFormatException exception) {
                add(
                        issues,
                        "PAGE_INCLUDE_INVALID",
                        CatalogEntityType.PAGE,
                        page.pageId(),
                        "includes",
                        "Invalid included page ID: " + rawId);
            }
        }
    }

    /**
     * A page is reachable in the client when every ancestor is visible: the index only descends into
     * visible children. A visible ancestor that is disabled is not a defect - that is how a folder
     * node is expressed, shown in the tree but sent with id -1 so it expands instead of opening.
     */
    private void validateAncestors(
            CatalogVersionSnapshot snapshot, CatalogPageSnapshot page, List<CatalogValidationIssue> issues) {
        if (!page.visible()) return;
        Set<Integer> visited = new HashSet<>();
        int parentId = page.parentId();
        while (parentId > 0 && visited.add(parentId)) {
            CatalogPageSnapshot parent =
                    snapshot.page(page.catalogType(), parentId).orElse(null);
            if (parent == null) return;
            if (!parent.visible()) {
                add(
                        issues,
                        "PAGE_ANCESTOR_NOT_AVAILABLE",
                        CatalogEntityType.PAGE,
                        page.pageId(),
                        "parentId",
                        "Visible page is unreachable: ancestor page " + parent.pageId() + " is hidden");
                return;
            }
            parentId = parent.parentId();
        }
    }

    private void detectCycles(CatalogVersionSnapshot snapshot, List<CatalogValidationIssue> issues, Scope scope) {
        Set<String> reported = new HashSet<>();
        for (CatalogPageSnapshot page : pagesInScope(snapshot, scope)) {
            Map<Integer, Integer> positions = new HashMap<>();
            List<Integer> path = new ArrayList<>();
            int currentId = page.pageId();
            while (currentId > 0 && snapshot.page(page.catalogType(), currentId).isPresent()) {
                Integer cycleStart = positions.putIfAbsent(currentId, path.size());
                if (cycleStart != null) {
                    for (int index = cycleStart; index < path.size(); index++) {
                        int cyclePageId = path.get(index);
                        if (reported.add(page.catalogType() + ":" + cyclePageId)) {
                            add(
                                    issues,
                                    "PAGE_CYCLE",
                                    CatalogEntityType.PAGE,
                                    cyclePageId,
                                    "parentId",
                                    "Page hierarchy contains a cycle");
                        }
                    }
                    break;
                }
                path.add(currentId);
                currentId = snapshot.page(page.catalogType(), currentId)
                        .orElseThrow()
                        .parentId();
            }
        }
    }

    private void validateOffers(CatalogVersionSnapshot snapshot, List<CatalogValidationIssue> issues, Scope scope) {
        for (CatalogOfferSnapshot offer : offersInScope(snapshot, scope)) {
            if (snapshot.page(offer.catalogType(), offer.pageId()).isEmpty()) {
                add(
                        issues,
                        "OFFER_PAGE_MISSING",
                        CatalogEntityType.OFFER,
                        offer.offerId(),
                        "pageId",
                        "Offer page " + offer.pageId() + " does not exist");
            }
            validateItemIds(offer, issues);
            if (offer.catalogName().startsWith("SONG ")
                    && (offer.songId() <= 0
                            || offer.extradata() == null
                            || offer.extradata().isBlank())) {
                add(
                        issues,
                        "OFFER_SOUND_REFERENCE_MISSING",
                        CatalogEntityType.OFFER,
                        offer.offerId(),
                        "songId",
                        "Sound offers require both a song ID and soundtrack code");
            }
            if (offer.costCredits() < 0 || offer.costPoints() < 0 || offer.amount() <= 0) {
                add(
                        issues,
                        "OFFER_PRICE_INVALID",
                        CatalogEntityType.OFFER,
                        offer.offerId(),
                        "price",
                        "Credits and points cannot be negative and amount must be positive");
            }
            if (offer.costPoints() > 0 && !currencyTypes.contains(offer.pointsType())) {
                add(
                        issues,
                        "OFFER_CURRENCY_UNSUPPORTED",
                        CatalogEntityType.OFFER,
                        offer.offerId(),
                        "pointsType",
                        "Unsupported points currency: " + offer.pointsType());
            }
            int sold = liveLimitedSells.getOrDefault(offer.offerId(), 0);
            if (offer.limitedStack() < sold) {
                add(
                        issues,
                        "OFFER_LIMITED_STACK_BELOW_SALES",
                        CatalogEntityType.OFFER,
                        offer.offerId(),
                        "limitedStack",
                        "Limited stack " + offer.limitedStack() + " is lower than the live sold count " + sold);
            }
        }
    }

    private void validateItemIds(CatalogOfferSnapshot offer, List<CatalogValidationIssue> issues) {
        if (offer.itemIds() == null || offer.itemIds().isBlank()) {
            add(
                    issues,
                    "OFFER_ITEM_INVALID",
                    CatalogEntityType.OFFER,
                    offer.offerId(),
                    "itemIds",
                    "Offer must contain at least one item definition");
            return;
        }
        for (String token : offer.itemIds().split(";", -1)) {
            String[] parts = token.trim().split(":", -1);
            try {
                if (parts.length > 2) throw new NumberFormatException();
                int itemId = Integer.parseInt(parts[0]);
                int quantity = parts.length == 2 ? Integer.parseInt(parts[1]) : 1;
                if (itemId <= 0 || quantity <= 0) throw new NumberFormatException();
                if (!itemDefinitionIds.contains(itemId)) {
                    add(
                            issues,
                            "OFFER_ITEM_MISSING",
                            CatalogEntityType.OFFER,
                            offer.offerId(),
                            "itemIds",
                            "Item definition " + itemId + " does not exist");
                }
            } catch (NumberFormatException exception) {
                add(
                        issues,
                        "OFFER_ITEM_INVALID",
                        CatalogEntityType.OFFER,
                        offer.offerId(),
                        "itemIds",
                        "Invalid item token: " + token);
            }
        }
    }

    private static void add(
            List<CatalogValidationIssue> issues,
            String code,
            CatalogEntityType entityType,
            int entityId,
            String field,
            String message) {
        issues.add(new CatalogValidationIssue(code, entityType, entityId, field, message));
    }

    private record ParentOrder(String catalogType, int parentId, int orderNum) {}
}
