package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CatalogChangeFieldDiffTest {
    @Test
    void reportsOnlyFieldsWhoseValuesChanged() {
        CatalogChangeEntry change = new CatalogChangeEntry(
                0,
                CatalogEntityType.PAGE,
                17,
                CatalogChangeOperation.UPDATE,
                "{\"pageId\":17,\"caption\":\"Old\",\"visible\":true}",
                "{\"pageId\":17,\"caption\":\"New\",\"visible\":true}");

        assertEquals(java.util.List.of("caption"), CatalogChangeFieldDiff.fields(change));
    }
}
