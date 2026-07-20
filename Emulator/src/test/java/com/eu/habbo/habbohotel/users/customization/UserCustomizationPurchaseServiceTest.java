package com.eu.habbo.habbohotel.users.customization;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.customization.UserCustomizationPurchaseService.CustomPrefixRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserCustomizationPurchaseServiceTest {

    @Test
    void validationAndUnavailableOffersReturnApplicationResults() throws Exception {
        UserCustomizationRepository repository = mock(UserCustomizationRepository.class);
        when(repository.loadPrefixSettings()).thenReturn(Map.of());
        when(repository.findCatalogPrefix(17)).thenReturn(Optional.empty());
        when(repository.findNickIcon("star")).thenReturn(Optional.empty());
        Habbo habbo = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(habbo.getInventory().getPrefixesComponent().getPrefixes()).thenReturn(List.of());
        when(habbo.getInventory().getPrefixesComponent().getPrefixByCatalogId(17))
                .thenReturn(null);
        when(habbo.getInventory().getNickIconsComponent().getNickIconByKey("star"))
                .thenReturn(null);
        when(habbo.getHabboInfo().getRank().getId()).thenReturn(1);

        UserCustomizationPurchaseService service = new UserCustomizationPurchaseService(repository, List.of());
        var invalidPrefix =
                service.purchaseCustomPrefix(habbo, new CustomPrefixRequest("bad\nprefix", "#FFFFFF", "", "", ""));
        var missingPrefix = service.purchaseCatalogPrefix(habbo, 17);
        var missingIcon = service.purchaseNickIcon(habbo, "star");

        assertAll(
                () -> assertEquals(UserCustomizationPurchaseService.Status.FAILURE, invalidPrefix.status()),
                () -> assertEquals("Prefix text contains invalid characters.", invalidPrefix.message()),
                () -> assertEquals(UserCustomizationPurchaseService.Status.UNAVAILABLE, missingPrefix.status()),
                () -> assertEquals(UserCustomizationPurchaseService.Status.FAILURE, missingIcon.status()),
                () -> assertEquals("This nick icon is not available.", missingIcon.message()));
    }
}
