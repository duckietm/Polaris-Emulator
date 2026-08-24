package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.google.gson.Gson;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CatalogLiveValidationGuardTest {
    private final Gson gson = new Gson();

    @Test
    void permitsRepairingAnInheritedProblem() {
        CatalogVersionSnapshot live = snapshot(List.of(page(1, 404)));
        CatalogPageSnapshot repaired = page(1, -1);
        CatalogChangeEntry change = change(live.pages().getFirst(), repaired);
        CatalogLiveValidationGuard guard = guard();

        assertDoesNotThrow(() -> guard.rejectIntroducedProblems(null, live, List.of(change)));
    }

    @Test
    void rejectsAProblemIntroducedByTheEdit() {
        CatalogVersionSnapshot live = snapshot(List.of(page(1, -1)));
        CatalogPageSnapshot broken = page(1, 404);
        CatalogChangeEntry change = change(live.pages().getFirst(), broken);
        CatalogLiveValidationGuard guard = guard();

        assertThrows(
                CatalogLiveValidationException.class,
                () -> guard.rejectIntroducedProblems(null, live, List.of(change)));
    }

    private CatalogLiveValidationGuard guard() {
        return new CatalogLiveValidationGuard(
                ignored ->
                        new CatalogValidationReferenceData(Set.of(), Set.of(), Map.of(), Set.of("root", "default_3x3")),
                gson);
    }

    private CatalogChangeEntry change(CatalogPageSnapshot before, CatalogPageSnapshot after) {
        return new CatalogChangeEntry(
                0,
                CatalogEntityType.PAGE,
                CatalogPageType.NORMAL,
                before.pageId(),
                CatalogChangeOperation.UPDATE,
                gson.toJson(before),
                gson.toJson(after));
    }

    private static CatalogVersionSnapshot snapshot(List<CatalogPageSnapshot> pages) {
        return new CatalogVersionSnapshot(
                new CatalogVersion(
                        1,
                        CatalogVersionStatus.PUBLISHED,
                        null,
                        1,
                        "Live recovery",
                        7,
                        Instant.parse("2026-08-23T10:00:00Z"),
                        7,
                        Instant.parse("2026-08-23T10:00:00Z")),
                pages,
                List.of());
    }

    private static CatalogPageSnapshot page(int id, int parentId) {
        return new CatalogPageSnapshot(
                id,
                parentId,
                "page_" + id,
                "Page " + id,
                parentId == -1 ? "root" : "default_3x3",
                0,
                0,
                1,
                0,
                true,
                true,
                false,
                "NORMAL",
                false,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                0,
                "");
    }
}
