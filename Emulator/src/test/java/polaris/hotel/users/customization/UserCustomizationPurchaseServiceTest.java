package polaris.hotel.users.customization;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import polaris.hotel.users.User;
import polaris.hotel.users.customization.UserCustomizationPurchaseService.CustomPrefixRequest;

class UserCustomizationPurchaseServiceTest {

    @Test
    void validationAndUnavailableOffersReturnApplicationResults() throws Exception {
        UserCustomizationRepository repository = mock(UserCustomizationRepository.class);
        when(repository.loadPrefixSettings()).thenReturn(Map.of());
        when(repository.findCatalogPrefix(17)).thenReturn(Optional.empty());
        when(repository.findNickIcon("star")).thenReturn(Optional.empty());
        User user = mock(User.class, RETURNS_DEEP_STUBS);
        when(user.getInventory().getPrefixesComponent().getPrefixes()).thenReturn(List.of());
        when(user.getInventory().getPrefixesComponent().getPrefixByCatalogId(17))
                .thenReturn(null);
        when(user.getInventory().getNickIconsComponent().getNickIconByKey("star"))
                .thenReturn(null);
        when(user.getUserInfo().getRank().getId()).thenReturn(1);

        UserCustomizationPurchaseService service = new UserCustomizationPurchaseService(repository, List.of());
        var invalidPrefix =
                service.purchaseCustomPrefix(user, new CustomPrefixRequest("bad\nprefix", "#FFFFFF", "", "", ""));
        var missingPrefix = service.purchaseCatalogPrefix(user, 17);
        var missingIcon = service.purchaseNickIcon(user, "star");

        assertAll(
                () -> assertEquals(UserCustomizationPurchaseService.Status.FAILURE, invalidPrefix.status()),
                () -> assertEquals("Prefix text contains invalid characters.", invalidPrefix.message()),
                () -> assertEquals(UserCustomizationPurchaseService.Status.UNAVAILABLE, missingPrefix.status()),
                () -> assertEquals(UserCustomizationPurchaseService.Status.FAILURE, missingIcon.status()),
                () -> assertEquals("This nick icon is not available.", missingIcon.message()));
    }
}
