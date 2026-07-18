package com.eu.habbo.habbohotel.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogValueValidatorTest {
    @Test
    void acceptsValidFreeCreditAndCurrencyOffers() {
        assertTrue(CatalogValueValidator.validate(new CatalogValueValidator.Values(1, 0, 0, 0, 1, 0, 0)).isEmpty());
        assertTrue(CatalogValueValidator.validate(new CatalogValueValidator.Values(2, 100, 0, 0, 1, 0, 0)).isEmpty());
        assertTrue(CatalogValueValidator.validate(new CatalogValueValidator.Values(3, 0, 25, 5, 1, 100, 4)).isEmpty());
    }

    @Test
    void reportsEveryUnsafeEconomicValueWithTheCatalogId() {
        var findings = CatalogValueValidator.validate(
                new CatalogValueValidator.Values(77, -1, -2, -1, 0, -4, 5));

        assertEquals(6, findings.size());
        assertTrue(findings.stream().allMatch(finding -> finding.startsWith("catalog item 77:")));
    }
}
