package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.ArrayList;
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
        Objects.requireNonNull(snapshot, "snapshot");
        List<CatalogValidationIssue> issues = new ArrayList<>();
        validatePages(snapshot, issues);
        validateOffers(snapshot, issues);
        return new CatalogValidationReport(issues);
    }

    public CatalogValidationReport validateChanges(CatalogVersionSnapshot baseline, CatalogVersionSnapshot candidate) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(candidate, "candidate");
        Set<CatalogValidationIssue> inherited = new HashSet<>(validate(baseline).issues());
        List<CatalogValidationIssue> introduced = validate(candidate).issues().stream()
                .filter(issue -> !inherited.contains(issue))
                .toList();
        return new CatalogValidationReport(introduced);
    }

    private void validatePages(CatalogVersionSnapshot snapshot, List<CatalogValidationIssue> issues) {
        Map<ParentOrder, List<Integer>> siblingOrders = new HashMap<>();
        for (CatalogPageSnapshot page : snapshot.pages()) {
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

        detectCycles(snapshot, issues);
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

    private void validateAncestors(
            CatalogVersionSnapshot snapshot, CatalogPageSnapshot page, List<CatalogValidationIssue> issues) {
        if (!page.visible() && !page.enabled()) return;
        Set<Integer> visited = new HashSet<>();
        int parentId = page.parentId();
        while (parentId > 0 && visited.add(parentId)) {
            CatalogPageSnapshot parent =
                    snapshot.page(page.catalogType(), parentId).orElse(null);
            if (parent == null) return;
            if (!parent.visible() || !parent.enabled()) {
                add(
                        issues,
                        "PAGE_ANCESTOR_NOT_AVAILABLE",
                        CatalogEntityType.PAGE,
                        page.pageId(),
                        "parentId",
                        "Visible and enabled page has a hidden or disabled ancestor");
                return;
            }
            parentId = parent.parentId();
        }
    }

    private void detectCycles(CatalogVersionSnapshot snapshot, List<CatalogValidationIssue> issues) {
        Set<String> reported = new HashSet<>();
        for (CatalogPageSnapshot page : snapshot.pages()) {
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

    private void validateOffers(CatalogVersionSnapshot snapshot, List<CatalogValidationIssue> issues) {
        for (CatalogOfferSnapshot offer : snapshot.offers()) {
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
