package com.eu.habbo.habbohotel.users.customization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.Habbo;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserCustomizationPurchaseServiceTest {
    @Test
    void unavailableNickIconReturnsApplicationFailure() throws Exception {
        UserCustomizationRepository repository = mock(UserCustomizationRepository.class);
        when(repository.findNickIcon("star")).thenReturn(Optional.empty());
        Habbo habbo = mock(Habbo.class, RETURNS_DEEP_STUBS);
        when(habbo.getInventory().getNickIconsComponent().getNickIconByKey("star"))
                .thenReturn(null);

        var result = new UserCustomizationPurchaseService(repository).purchaseNickIcon(habbo, "star");

        assertEquals(UserCustomizationPurchaseService.Status.FAILURE, result.status());
        assertEquals("This nick icon is not available.", result.message());
    }
}
