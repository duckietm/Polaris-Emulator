package polaris.hotel.users.customization;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.hotel.users.LedgerWalletMutation;
import polaris.hotel.users.User;
import polaris.hotel.users.UserPrefix;
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

    @Test
    void blacklistedWordsBlockPurchasedPrefixesButNotStaffGrants() throws Exception {
        UserCustomizationRepository repository = mock(UserCustomizationRepository.class);
        when(repository.loadPrefixSettings()).thenReturn(Map.of());
        when(repository.loadPrefixBlacklist()).thenReturn(List.of("admin"));
        User user = userWithRank(1);
        UserCustomizationPurchaseService service = new UserCustomizationPurchaseService(repository, List.of());

        var purchase = service.purchaseCustomPrefix(user, new CustomPrefixRequest("ÁDMIN", "#FFFFFF", "", "", ""));
        String grant = service.grantedPrefixFailure(user, "ADMIN", "#FFFFFF", "", "");

        assertAll(
                () -> assertEquals(UserCustomizationPurchaseService.Status.FAILURE, purchase.status()),
                () -> assertEquals("This prefix contains a blocked word.", purchase.message()),
                () -> assertNull(grant));
        verify(repository, never()).insertPrefix(any());
    }

    @Test
    void textLimitNeverExceedsTheColumnWidth() {
        UserCustomizationRepository repository = mock(UserCustomizationRepository.class);
        when(repository.loadPrefixSettings()).thenReturn(Map.of("max_length", 80));
        UserCustomizationPurchaseService service = new UserCustomizationPurchaseService(repository, List.of());

        var result = service.purchaseCustomPrefix(
                userWithRank(1), new CustomPrefixRequest("x".repeat(51), "#FFFFFF", "", "", ""));

        assertEquals("Prefix text is invalid or too long (max 50 characters).", result.message());
    }

    @Test
    void failedInsertEndsThePurchaseBeforeAnyDebit() throws Exception {
        UserCustomizationRepository repository = mock(UserCustomizationRepository.class);
        when(repository.loadPrefixSettings()).thenReturn(Map.of("price_credits", 5));
        doThrow(new SQLException("column too short")).when(repository).insertPrefix(any());
        User user = userWithRank(1);
        when(user.getUserInfo().getCredits()).thenReturn(100);
        UserCustomizationPurchaseService service = new UserCustomizationPurchaseService(repository, List.of());

        try (MockedStatic<LedgerWalletMutation> ledger = mockStatic(LedgerWalletMutation.class)) {
            var result = service.purchaseCustomPrefix(user, new CustomPrefixRequest("VIP", "#FFFFFF", "", "", ""));

            assertAll(
                    () -> assertEquals(UserCustomizationPurchaseService.Status.FAILURE, result.status()),
                    () -> assertEquals("Unable to complete the purchase.", result.message()));
            ledger.verifyNoInteractions();
        }
    }

    @Test
    void failedDebitRemovesTheStoredPrefix() throws Exception {
        UserCustomizationRepository repository = mock(UserCustomizationRepository.class);
        when(repository.loadPrefixSettings()).thenReturn(Map.of("price_credits", 5));
        User user = userWithRank(1);
        when(user.getUserInfo().getCredits()).thenReturn(100);
        UserCustomizationPurchaseService service = new UserCustomizationPurchaseService(repository, List.of());

        try (MockedStatic<LedgerWalletMutation> ledger = mockStatic(LedgerWalletMutation.class)) {
            ledger.when(() -> LedgerWalletMutation.executeBatch(any(), any()))
                    .thenThrow(new IllegalArgumentException("insufficient"));

            var result = service.purchaseCustomPrefix(user, new CustomPrefixRequest("VIP", "#FFFFFF", "", "", ""));

            assertEquals("Not enough currency.", result.message());
        }
        verify(repository).insertPrefix(any());
        verify(repository).deletePrefix(anyInt(), anyInt());
    }

    @Test
    void freePurchaseStoresThePrefixAndReturnsIt() throws Exception {
        UserCustomizationRepository repository = mock(UserCustomizationRepository.class);
        when(repository.loadPrefixSettings()).thenReturn(Map.of("price_credits", 0, "font_price_credits", 0));
        UserCustomizationPurchaseService service = new UserCustomizationPurchaseService(repository, List.of());

        var result =
                service.purchaseCustomPrefix(userWithRank(1), new CustomPrefixRequest("VIP", "#FFFFFF", "", "", ""));

        assertEquals(UserCustomizationPurchaseService.Status.SUCCESS, result.status());
        verify(repository).insertPrefix(result.prefix());
    }

    @Test
    void catalogPurchaseFollowsTheRankAndCountLimits() throws Exception {
        UserCustomizationRepository repository = mock(UserCustomizationRepository.class);
        when(repository.loadPrefixSettings()).thenReturn(Map.of("min_rank_to_buy", 3, "max_prefixes", 1));
        UserCustomizationPurchaseService service = new UserCustomizationPurchaseService(repository, List.of());
        User lowRank = userWithRank(1);
        User full = userWithRank(5);
        when(full.getInventory().getPrefixesComponent().getPrefixes()).thenReturn(List.of(mock(UserPrefix.class)));

        var rankResult = service.purchaseCatalogPrefix(lowRank, 17);
        var limitResult = service.purchaseCatalogPrefix(full, 17);

        assertAll(
                () -> assertEquals("Your rank is too low to purchase prefixes.", rankResult.message()),
                () -> assertEquals("You already own the maximum number of prefixes (1).", limitResult.message()));
        verify(repository, never()).findCatalogPrefix(17);
    }

    private static User userWithRank(int rank) {
        User user = mock(User.class, RETURNS_DEEP_STUBS);
        when(user.getInventory().getPrefixesComponent().getPrefixes()).thenReturn(List.of());
        when(user.getInventory().getPrefixesComponent().getPrefixByCatalogId(17))
                .thenReturn(null);
        when(user.getUserInfo().getRank().getId()).thenReturn(rank);
        when(user.getUserInfo().getId()).thenReturn(7);
        return user;
    }
}
