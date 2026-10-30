package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A voucher is used up only once what it hands out is known to exist. One pointing at
 * a catalogue offer that had been deleted was claimed first and then delivered nothing,
 * silently: the code was spent and the user got nothing for it.
 */
class VoucherResolveBeforeClaimTest {

    private static final Path SOURCE = Path.of("src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java");

    private static String redeemVoucher() throws Exception {
        String source = Files.readString(SOURCE);
        int start = source.indexOf("public void redeemVoucher(");
        int end = source.indexOf("public boolean deleteVoucher(", start);
        return source.substring(start, end);
    }

    @Test
    void theOfferIsResolvedBeforeTheVoucherIsClaimed() throws Exception {
        String method = redeemVoucher();

        int resolve = method.indexOf("this.getCatalogItem(voucher.catalogItemId)");
        int claim = method.indexOf("voucher.claimForUser(");

        assertTrue(resolve > 0 && claim > 0, method);
        assertTrue(resolve < claim, "the offer is looked up before the voucher is spent");
    }

    @Test
    void aMissingOfferRefusesTheVoucherUnspent() throws Exception {
        String method = redeemVoucher();

        int missing = method.indexOf("if (voucherItem == null)");
        int claim = method.indexOf("voucher.claimForUser(");

        assertTrue(missing > 0, "a missing offer must be caught:\n" + method);
        assertTrue(missing < claim, "and caught while the voucher is still unspent");
    }

    @Test
    void theDeliveryUsesTheOfferAlreadyResolved() throws Exception {
        String method = redeemVoucher();

        assertTrue(method.contains("this.purchaseItemDelivered(null, voucherItem,"), method);
        assertFalse(
                method.contains("CatalogItem item = this.getCatalogItem(voucher.catalogItemId)"),
                "the offer is not looked up a second time after the claim");
    }

    @Test
    void aWalletThatCannotTakeTheValueRefusesTheVoucherUnspent() throws Exception {
        String method = redeemVoucher();

        int wallet = method.indexOf("fitsWallet(habbo.getHabboInfo().getCredits(), voucher.credits)");
        int claim = method.indexOf("voucher.claimForUser(");
        assertTrue(wallet > 0 && wallet < claim, "the wallet is checked while the voucher is still unspent");

        assertTrue(CatalogManager.fitsWallet(Integer.MAX_VALUE - 5, 5));
        assertFalse(CatalogManager.fitsWallet(Integer.MAX_VALUE - 5, 6));
        assertTrue(CatalogManager.fitsWallet(Integer.MAX_VALUE, 0));
    }

    @Test
    void anUndeliveredItemGivesTheVoucherBackAndNothingElse() throws Exception {
        String method = redeemVoucher();

        int deliver = method.indexOf("this.purchaseItemDelivered(null, voucherItem,");
        int release = method.indexOf("voucher.releaseClaim(", deliver);
        int points = method.indexOf("client.getHabbo().givePoints", deliver);
        int ok = method.indexOf("new RedeemVoucherOKComposer()", deliver);

        assertTrue(deliver > 0 && release > deliver, "a refused item releases the claim:
" + method);
        assertTrue(release < points && points < ok, "currency and the OK come only after the item was delivered");
    }
}
